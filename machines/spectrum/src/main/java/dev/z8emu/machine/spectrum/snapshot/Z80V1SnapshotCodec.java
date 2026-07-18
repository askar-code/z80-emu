package dev.z8emu.machine.spectrum.snapshot;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.MALFORMED;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.UNREPRESENTABLE;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.UNSUPPORTED;

/** Encoder and decoder for 48K version-1 .z80 snapshots. */
public final class Z80V1SnapshotCodec {
    public static final int HEADER_SIZE = 30;
    private static final int UNCOMPRESSED_IMAGE_SIZE = HEADER_SIZE + Spectrum48Snapshot.RAM_SIZE;
    private static final int ED = 0xED;

    private Z80V1SnapshotCodec() {
    }

    public static Spectrum48Snapshot decode(byte[] image) throws SpectrumSnapshotException {
        Objects.requireNonNull(image, "image");
        if (image.length < HEADER_SIZE) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "Z80 snapshot is shorter than the 30-byte base header: " + image.length + " bytes"
            );
        }

        int pc = SnapshotBytes.le16(image, 6);
        if (pc == 0) {
            throw new SpectrumSnapshotException(
                    UNSUPPORTED,
                    "Z80 v2/v3 snapshots are not supported by the v1 codec (base-header PC is zero)"
            );
        }

        int flags1 = SnapshotBytes.u8(image, 12);
        if (flags1 == 0xFF) {
            // The original format description reserves FF as the historical
            // equivalent of 01.
            flags1 = 0x01;
        }
        boolean compressed = (flags1 & 0x20) != 0;
        int interruptMode = SnapshotBytes.u8(image, 29) & 0x03;
        if (interruptMode == 3) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "Z80 v1 header has invalid interrupt mode 3 at offset 29"
            );
        }

        byte[] ram;
        if (compressed) {
            ram = decodeCompressedRam(image);
        } else {
            if (image.length != UNCOMPRESSED_IMAGE_SIZE) {
                throw new SpectrumSnapshotException(
                        MALFORMED,
                        "Uncompressed Z80 v1 image must be exactly 49182 bytes, got " + image.length
                );
            }
            ram = Arrays.copyOfRange(image, HEADER_SIZE, image.length);
        }

        int r = (SnapshotBytes.u8(image, 11) & 0x7F) | ((flags1 & 0x01) << 7);
        Z80SnapshotState cpu = new Z80SnapshotState(
                (SnapshotBytes.u8(image, 0) << 8) | SnapshotBytes.u8(image, 1),
                SnapshotBytes.le16(image, 2),
                SnapshotBytes.le16(image, 13),
                SnapshotBytes.le16(image, 4),
                (SnapshotBytes.u8(image, 21) << 8) | SnapshotBytes.u8(image, 22),
                SnapshotBytes.le16(image, 15),
                SnapshotBytes.le16(image, 17),
                SnapshotBytes.le16(image, 19),
                SnapshotBytes.le16(image, 25),
                SnapshotBytes.le16(image, 23),
                SnapshotBytes.le16(image, 8),
                pc,
                SnapshotBytes.u8(image, 10),
                r,
                SnapshotBytes.u8(image, 27) != 0,
                SnapshotBytes.u8(image, 28) != 0,
                interruptMode
        );
        return new Spectrum48Snapshot(cpu, (flags1 >>> 1) & 0x07, ram);
    }

    public static byte[] encode(Spectrum48Snapshot snapshot, Compression compression)
            throws SpectrumSnapshotException {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(compression, "compression");
        Z80SnapshotState cpu = snapshot.cpu();
        if (cpu.pc() == 0) {
            throw new SpectrumSnapshotException(
                    UNREPRESENTABLE,
                    "Z80 v1 cannot represent PC 0x0000 because a zero base-header PC selects v2/v3"
            );
        }

        byte[] ram = snapshot.ram();
        byte[] payload = compression == Compression.COMPRESSED ? encodeCompressedRam(ram) : ram;
        byte[] image = new byte[HEADER_SIZE + payload.length];

        SnapshotBytes.put8(image, 0, cpu.af() >>> 8);
        SnapshotBytes.put8(image, 1, cpu.af());
        SnapshotBytes.putLe16(image, 2, cpu.bc());
        SnapshotBytes.putLe16(image, 4, cpu.hl());
        SnapshotBytes.putLe16(image, 6, cpu.pc());
        SnapshotBytes.putLe16(image, 8, cpu.sp());
        SnapshotBytes.put8(image, 10, cpu.i());
        SnapshotBytes.put8(image, 11, cpu.r() & 0x7F);
        int flags1 = ((cpu.r() >>> 7) & 0x01)
                | ((snapshot.borderColor() & 0x07) << 1)
                | (compression == Compression.COMPRESSED ? 0x20 : 0x00);
        SnapshotBytes.put8(image, 12, flags1);
        SnapshotBytes.putLe16(image, 13, cpu.de());
        SnapshotBytes.putLe16(image, 15, cpu.bcAlt());
        SnapshotBytes.putLe16(image, 17, cpu.deAlt());
        SnapshotBytes.putLe16(image, 19, cpu.hlAlt());
        SnapshotBytes.put8(image, 21, cpu.afAlt() >>> 8);
        SnapshotBytes.put8(image, 22, cpu.afAlt());
        SnapshotBytes.putLe16(image, 23, cpu.iy());
        SnapshotBytes.putLe16(image, 25, cpu.ix());
        SnapshotBytes.put8(image, 27, cpu.iff1() ? 1 : 0);
        SnapshotBytes.put8(image, 28, cpu.iff2() ? 1 : 0);
        SnapshotBytes.put8(image, 29, cpu.interruptMode());
        System.arraycopy(payload, 0, image, HEADER_SIZE, payload.length);
        return image;
    }

    private static byte[] decodeCompressedRam(byte[] image) throws SpectrumSnapshotException {
        byte[] ram = new byte[Spectrum48Snapshot.RAM_SIZE];
        int inputOffset = HEADER_SIZE;
        int outputOffset = 0;

        while (inputOffset < image.length) {
            if (isTerminator(image, inputOffset)) {
                inputOffset += 4;
                if (outputOffset != ram.length) {
                    throw new SpectrumSnapshotException(
                            MALFORMED,
                            "Compressed Z80 v1 RAM terminates after " + outputOffset
                                    + " bytes; expected 49152"
                    );
                }
                if (inputOffset != image.length) {
                    throw new SpectrumSnapshotException(
                            MALFORMED,
                            "Compressed Z80 v1 image has " + (image.length - inputOffset)
                                    + " trailing bytes after the 00 ED ED 00 terminator"
                    );
                }
                return ram;
            }

            int value = SnapshotBytes.u8(image, inputOffset);
            if (value == ED
                    && inputOffset + 1 < image.length
                    && SnapshotBytes.u8(image, inputOffset + 1) == ED) {
                if (inputOffset + 3 >= image.length) {
                    throw new SpectrumSnapshotException(
                            MALFORMED,
                            "Truncated ED ED run at compressed Z80 v1 offset " + inputOffset
                    );
                }
                int count = SnapshotBytes.u8(image, inputOffset + 2);
                if (count == 0) {
                    throw new SpectrumSnapshotException(
                            MALFORMED,
                            "Zero-length ED ED run at compressed Z80 v1 offset " + inputOffset
                    );
                }
                if (outputOffset + count > ram.length) {
                    throw new SpectrumSnapshotException(
                            MALFORMED,
                            "ED ED run at compressed Z80 v1 offset " + inputOffset
                                    + " expands beyond 49152 RAM bytes"
                    );
                }
                Arrays.fill(ram, outputOffset, outputOffset + count, image[inputOffset + 3]);
                outputOffset += count;
                inputOffset += 4;
            } else {
                if (outputOffset == ram.length) {
                    throw new SpectrumSnapshotException(
                            MALFORMED,
                            "Compressed Z80 v1 payload continues after 49152 RAM bytes; expected terminator"
                    );
                }
                ram[outputOffset++] = image[inputOffset++];
            }
        }

        throw new SpectrumSnapshotException(
                MALFORMED,
                "Compressed Z80 v1 image is missing the 00 ED ED 00 terminator"
        );
    }

    private static byte[] encodeCompressedRam(byte[] ram) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(ram.length / 2);
        int offset = 0;
        boolean lastOutputWasLiteralEd = false;
        while (offset < ram.length) {
            int value = Byte.toUnsignedInt(ram[offset]);
            int count = 1;
            while (offset + count < ram.length
                    && count < 255
                    && Byte.toUnsignedInt(ram[offset + count]) == value) {
                count++;
            }

            boolean compressRun = count >= 5 || (value == ED && count >= 2);
            if (compressRun && lastOutputWasLiteralEd) {
                // A literal ED immediately followed by an ED ED run marker
                // would be decoded as a run beginning at that literal. Emit
                // one byte from the current run to break the sequence first.
                output.write(value);
                offset++;
                lastOutputWasLiteralEd = value == ED;
            } else if (compressRun) {
                output.write(ED);
                output.write(ED);
                output.write(count);
                output.write(value);
                offset += count;
                lastOutputWasLiteralEd = false;
            } else {
                output.write(ram, offset, count);
                offset += count;
                lastOutputWasLiteralEd = value == ED;
            }
        }
        output.write(0x00);
        output.write(ED);
        output.write(ED);
        output.write(0x00);
        return output.toByteArray();
    }

    private static boolean isTerminator(byte[] image, int offset) {
        return offset + 3 < image.length
                && SnapshotBytes.u8(image, offset) == 0x00
                && SnapshotBytes.u8(image, offset + 1) == ED
                && SnapshotBytes.u8(image, offset + 2) == ED
                && SnapshotBytes.u8(image, offset + 3) == 0x00;
    }

    public enum Compression {
        UNCOMPRESSED,
        COMPRESSED
    }
}
