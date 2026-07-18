package dev.z8emu.machine.spectrum.snapshot;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.MALFORMED;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.UNSUPPORTED;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.AY_EXTENSION;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.FORMAT_EXTENSION;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.FULLER_AUDIO;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.INTERFACE_1;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.MEMORY_PAGE;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.MGT;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.PENTAGON;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.SAM_RAM;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.SCORPION;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.SPECTRUM_16K;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.SPECTRUM_PLUS_2;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.SPECTRUM_PLUS_2A;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.SPECTRUM_PLUS_3;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.TIMEX;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.UNKNOWN_HARDWARE;

/** Decoder for .z80 v2/v3 snapshots and canonical v3 encoder for stock 48K/128K machines. */
public final class Z80V2V3SnapshotCodec {
    public static final int BASE_HEADER_SIZE = 30;
    public static final int V2_ADDITIONAL_HEADER_SIZE = 23;
    public static final int V3_ADDITIONAL_HEADER_SIZE = 54;
    public static final int V3_EXTENDED_ADDITIONAL_HEADER_SIZE = 55;
    public static final int RAM_PAGE_SIZE = 16 * 1024;
    private static final int V3_HEADER_SIZE = BASE_HEADER_SIZE + 2 + V3_ADDITIONAL_HEADER_SIZE;
    private static final int FRAME_QUARTER_TSTATES_48K = 17_472;
    private static final int FRAME_QUARTER_TSTATES_128K = 17_727;
    private static final int ED = 0xED;

    private Z80V2V3SnapshotCodec() {
    }

    public static SpectrumSnapshot decode(byte[] image) throws SpectrumSnapshotException {
        Objects.requireNonNull(image, "image");
        if (image.length < BASE_HEADER_SIZE + 2) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "Z80 v2/v3 snapshot is shorter than the 32-byte base and length headers: "
                            + image.length + " bytes"
            );
        }
        if (SnapshotBytes.le16(image, 6) != 0) {
            throw new SpectrumSnapshotException(
                    UNSUPPORTED,
                    "Z80 v1 snapshots are not supported by the v2/v3 codec (base-header PC is non-zero)"
            );
        }

        int additionalLength = SnapshotBytes.le16(image, 30);
        Version version = switch (additionalLength) {
            case V2_ADDITIONAL_HEADER_SIZE -> Version.V2;
            case V3_ADDITIONAL_HEADER_SIZE, V3_EXTENDED_ADDITIONAL_HEADER_SIZE -> Version.V3;
            default -> throw new SpectrumSnapshotException(
                    UNSUPPORTED,
                    FORMAT_EXTENSION,
                    "Unsupported Z80 additional-header length " + additionalLength
                            + " (expected 23, 54 or 55)"
            );
        };
        int memoryOffset = BASE_HEADER_SIZE + 2 + additionalLength;
        if (memoryOffset > image.length) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "Z80 additional header declares " + additionalLength
                            + " bytes but the image ends at byte " + image.length
            );
        }

        int flags2 = SnapshotBytes.u8(image, 37);
        Hardware hardware = decodeHardware(version, SnapshotBytes.u8(image, 34), (flags2 & 0x80) != 0);
        validateFrameCounters(image, version, hardware);
        rejectUnsupportedExtensions(image, additionalLength, hardware, flags2);
        Z80SnapshotState cpu = decodeCpu(image, SnapshotBytes.le16(image, 32));
        int flags1 = SnapshotBytes.u8(image, 12);
        if (flags1 == 0xFF) {
            flags1 = 0x01;
        }
        int borderColor = (flags1 >>> 1) & 0x07;
        Map<Integer, byte[]> pages = decodePages(image, memoryOffset, hardware.is128K());

        if (hardware.is128K()) {
            byte[][] ramBanks = new byte[Spectrum128Snapshot.RAM_BANK_COUNT][];
            for (int bank = 0; bank < Spectrum128Snapshot.RAM_BANK_COUNT; bank++) {
                ramBanks[bank] = pages.get(bank + 3);
            }
            byte[] ayRegisters = Arrays.copyOfRange(image, 39, 55);
            return new Spectrum128Snapshot(
                    cpu,
                    borderColor,
                    SnapshotBytes.u8(image, 35),
                    SnapshotBytes.u8(image, 38) & 0x0F,
                    ayRegisters,
                    ramBanks
            );
        }

        byte[] ram = new byte[Spectrum48Snapshot.RAM_SIZE];
        System.arraycopy(pages.get(8), 0, ram, 0, RAM_PAGE_SIZE);
        System.arraycopy(pages.get(4), 0, ram, RAM_PAGE_SIZE, RAM_PAGE_SIZE);
        System.arraycopy(pages.get(5), 0, ram, 2 * RAM_PAGE_SIZE, RAM_PAGE_SIZE);
        return new Spectrum48Snapshot(cpu, borderColor, ram);
    }

    public static byte[] encode(Spectrum48Snapshot snapshot, Compression compression) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(compression, "compression");
        ByteArrayOutputStream output = new ByteArrayOutputStream(50_000);
        output.writeBytes(canonicalV3Header(snapshot.cpu(), snapshot.borderColor(), false, 0, 0, new byte[16]));
        byte[] ram = snapshot.ram();
        writePage(output, 4, Arrays.copyOfRange(ram, RAM_PAGE_SIZE, 2 * RAM_PAGE_SIZE), compression);
        writePage(output, 5, Arrays.copyOfRange(ram, 2 * RAM_PAGE_SIZE, 3 * RAM_PAGE_SIZE), compression);
        writePage(output, 8, Arrays.copyOfRange(ram, 0, RAM_PAGE_SIZE), compression);
        return output.toByteArray();
    }

    public static byte[] encode(Spectrum128Snapshot snapshot, Compression compression) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(compression, "compression");
        ByteArrayOutputStream output = new ByteArrayOutputStream(132_000);
        output.writeBytes(canonicalV3Header(
                snapshot.cpu(),
                snapshot.borderColor(),
                true,
                snapshot.pagingPort7ffd(),
                snapshot.selectedAyRegister(),
                snapshot.ayRegisters()
        ));
        for (int bank = 0; bank < Spectrum128Snapshot.RAM_BANK_COUNT; bank++) {
            writePage(output, bank + 3, snapshot.ramBank(bank), compression);
        }
        return output.toByteArray();
    }

    private static byte[] canonicalV3Header(
            Z80SnapshotState cpu,
            int borderColor,
            boolean is128K,
            int pagingPort7ffd,
            int selectedAyRegister,
            byte[] ayRegisters
    ) {
        byte[] header = new byte[V3_HEADER_SIZE];
        SnapshotBytes.put8(header, 0, cpu.af() >>> 8);
        SnapshotBytes.put8(header, 1, cpu.af());
        SnapshotBytes.putLe16(header, 2, cpu.bc());
        SnapshotBytes.putLe16(header, 4, cpu.hl());
        SnapshotBytes.putLe16(header, 6, 0);
        SnapshotBytes.putLe16(header, 8, cpu.sp());
        SnapshotBytes.put8(header, 10, cpu.i());
        SnapshotBytes.put8(header, 11, cpu.r() & 0x7F);
        SnapshotBytes.put8(
                header,
                12,
                ((cpu.r() >>> 7) & 0x01) | ((borderColor & 0x07) << 1)
        );
        SnapshotBytes.putLe16(header, 13, cpu.de());
        SnapshotBytes.putLe16(header, 15, cpu.bcAlt());
        SnapshotBytes.putLe16(header, 17, cpu.deAlt());
        SnapshotBytes.putLe16(header, 19, cpu.hlAlt());
        SnapshotBytes.put8(header, 21, cpu.afAlt() >>> 8);
        SnapshotBytes.put8(header, 22, cpu.afAlt());
        SnapshotBytes.putLe16(header, 23, cpu.iy());
        SnapshotBytes.putLe16(header, 25, cpu.ix());
        SnapshotBytes.put8(header, 27, cpu.iff1() ? 1 : 0);
        SnapshotBytes.put8(header, 28, cpu.iff2() ? 1 : 0);
        SnapshotBytes.put8(header, 29, cpu.interruptMode());
        SnapshotBytes.putLe16(header, 30, V3_ADDITIONAL_HEADER_SIZE);
        SnapshotBytes.putLe16(header, 32, cpu.pc());
        SnapshotBytes.put8(header, 34, is128K ? 4 : 0);
        SnapshotBytes.put8(header, 35, is128K ? pagingPort7ffd : 0);
        // AY is inherent in stock 128K hardware. Bit 2 is reserved for an AY
        // extension (Melodik) on machines which do not already have one.
        SnapshotBytes.put8(header, 37, 0);
        SnapshotBytes.put8(header, 38, is128K ? selectedAyRegister : 0);
        if (is128K) {
            System.arraycopy(ayRegisters, 0, header, 39, Spectrum128Snapshot.AY_REGISTER_COUNT);
        }
        int frameQuarterTStates = is128K ? FRAME_QUARTER_TSTATES_128K : FRAME_QUARTER_TSTATES_48K;
        SnapshotBytes.putLe16(header, 55, frameQuarterTStates - 1);
        SnapshotBytes.put8(header, 57, 3);
        // Stock 48K and 128K machines have ROM in both lower 8K regions.
        SnapshotBytes.put8(header, 61, 0xFF);
        SnapshotBytes.put8(header, 62, 0xFF);
        return header;
    }

    private static Z80SnapshotState decodeCpu(byte[] image, int pc) throws SpectrumSnapshotException {
        int interruptMode = SnapshotBytes.u8(image, 29) & 0x03;
        if (interruptMode == 3) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "Z80 v2/v3 header has invalid interrupt mode 3 at offset 29"
            );
        }
        int flags1 = SnapshotBytes.u8(image, 12);
        if (flags1 == 0xFF) {
            flags1 = 0x01;
        }
        int r = (SnapshotBytes.u8(image, 11) & 0x7F) | ((flags1 & 0x01) << 7);
        return new Z80SnapshotState(
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
    }

    private static Map<Integer, byte[]> decodePages(
            byte[] image,
            int memoryOffset,
            boolean is128K
    ) throws SpectrumSnapshotException {
        Map<Integer, byte[]> pages = new HashMap<>();
        int offset = memoryOffset;
        while (offset < image.length) {
            if (image.length - offset < 3) {
                throw new SpectrumSnapshotException(
                        MALFORMED,
                        "Truncated Z80 memory-block header at offset " + offset
                );
            }
            if (isSltExtension(image, offset)) {
                throw new SpectrumSnapshotException(
                        UNSUPPORTED,
                        FORMAT_EXTENSION,
                        "Z80 SLT trailing extension is not supported"
                );
            }

            int storedLength = SnapshotBytes.le16(image, offset);
            int page = SnapshotBytes.u8(image, offset + 2);
            validatePage(page, is128K);
            if (pages.containsKey(page)) {
                throw new SpectrumSnapshotException(
                        MALFORMED,
                        "Duplicate Z80 RAM page " + page + " at offset " + offset
                );
            }
            offset += 3;
            int payloadLength = storedLength == 0xFFFF ? RAM_PAGE_SIZE : storedLength;
            if (payloadLength > image.length - offset) {
                throw new SpectrumSnapshotException(
                        MALFORMED,
                        "Z80 RAM page " + page + " declares " + payloadLength
                                + " bytes but only " + (image.length - offset) + " remain"
                );
            }
            byte[] pageData = storedLength == 0xFFFF
                    ? Arrays.copyOfRange(image, offset, offset + RAM_PAGE_SIZE)
                    : decodeCompressedPage(image, offset, payloadLength, page);
            pages.put(page, pageData);
            offset += payloadLength;
        }

        int[] requiredPages = is128K ? new int[]{3, 4, 5, 6, 7, 8, 9, 10} : new int[]{4, 5, 8};
        for (int requiredPage : requiredPages) {
            if (!pages.containsKey(requiredPage)) {
                throw new SpectrumSnapshotException(
                        MALFORMED,
                        "Z80 snapshot is missing required RAM page " + requiredPage
                );
            }
        }
        return pages;
    }

    private static boolean isSltExtension(byte[] image, int offset) {
        return image.length - offset >= 6
                && image[offset] == 0
                && image[offset + 1] == 0
                && image[offset + 2] == 0
                && image[offset + 3] == 'S'
                && image[offset + 4] == 'L'
                && image[offset + 5] == 'T';
    }

    private static byte[] decodeCompressedPage(
            byte[] image,
            int inputOffset,
            int inputLength,
            int page
    ) throws SpectrumSnapshotException {
        byte[] output = new byte[RAM_PAGE_SIZE];
        int inputEnd = inputOffset + inputLength;
        int outputOffset = 0;
        int offset = inputOffset;
        while (offset < inputEnd) {
            int value = SnapshotBytes.u8(image, offset);
            if (value == ED && offset + 1 < inputEnd && SnapshotBytes.u8(image, offset + 1) == ED) {
                if (offset + 3 >= inputEnd) {
                    throw new SpectrumSnapshotException(
                            MALFORMED,
                            "Truncated ED ED run in Z80 RAM page " + page
                                    + " at payload offset " + (offset - inputOffset)
                    );
                }
                int count = SnapshotBytes.u8(image, offset + 2);
                if (count == 0) {
                    throw new SpectrumSnapshotException(
                            MALFORMED,
                            "Zero-length ED ED run in Z80 RAM page " + page
                                    + " at payload offset " + (offset - inputOffset)
                    );
                }
                if (outputOffset + count > RAM_PAGE_SIZE) {
                    throw new SpectrumSnapshotException(
                            MALFORMED,
                            "ED ED run in Z80 RAM page " + page + " expands beyond 16384 bytes"
                    );
                }
                Arrays.fill(output, outputOffset, outputOffset + count, image[offset + 3]);
                outputOffset += count;
                offset += 4;
            } else {
                if (outputOffset == RAM_PAGE_SIZE) {
                    throw new SpectrumSnapshotException(
                            MALFORMED,
                            "Compressed Z80 RAM page " + page + " contains trailing payload bytes"
                    );
                }
                output[outputOffset++] = image[offset++];
            }
        }
        if (outputOffset != RAM_PAGE_SIZE) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "Compressed Z80 RAM page " + page + " expands to " + outputOffset
                            + " bytes; expected 16384"
            );
        }
        return output;
    }

    private static void writePage(
            ByteArrayOutputStream output,
            int page,
            byte[] data,
            Compression compression
    ) {
        if (compression == Compression.UNCOMPRESSED) {
            output.write(0xFF);
            output.write(0xFF);
            output.write(page);
            output.writeBytes(data);
            return;
        }

        byte[] payload = encodeCompressedPage(data);
        output.write(payload.length & 0xFF);
        output.write((payload.length >>> 8) & 0xFF);
        output.write(page);
        output.writeBytes(payload);
    }

    private static byte[] encodeCompressedPage(byte[] data) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(data.length / 2);
        int offset = 0;
        boolean lastOutputWasLiteralEd = false;
        while (offset < data.length) {
            int value = Byte.toUnsignedInt(data[offset]);
            int count = 1;
            while (offset + count < data.length
                    && count < 255
                    && Byte.toUnsignedInt(data[offset + count]) == value) {
                count++;
            }

            boolean compressRun = count >= 5 || (value == ED && count >= 2);
            if (compressRun && lastOutputWasLiteralEd) {
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
                output.write(data, offset, count);
                offset += count;
                lastOutputWasLiteralEd = value == ED;
            }
        }
        return output.toByteArray();
    }

    private static void validatePage(int page, boolean is128K) throws SpectrumSnapshotException {
        boolean supported = is128K ? page >= 3 && page <= 10 : page == 4 || page == 5 || page == 8;
        if (!supported) {
            SpectrumSnapshotException.UnsupportedFeature feature = page == 11
                    ? SpectrumSnapshotException.UnsupportedFeature.MULTIFACE
                    : MEMORY_PAGE;
            throw new SpectrumSnapshotException(
                    UNSUPPORTED,
                    feature,
                    "Z80 " + (is128K ? "128K" : "48K") + " snapshot contains unsupported page " + page
            );
        }
    }

    private static void validateFrameCounters(byte[] image, Version version, Hardware hardware)
            throws SpectrumSnapshotException {
        if (version != Version.V3) {
            return;
        }
        int maximumLowCounter = (hardware.is128K()
                ? FRAME_QUARTER_TSTATES_128K
                : FRAME_QUARTER_TSTATES_48K) - 1;
        int lowCounter = SnapshotBytes.le16(image, 55);
        int highCounter = SnapshotBytes.u8(image, 57);
        if (lowCounter > maximumLowCounter || highCounter > 3) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "Invalid Z80 v3 frame counter low=" + lowCounter + ", high=" + highCounter
                            + " for " + (hardware.is128K() ? "128K" : "48K") + " timing"
            );
        }
    }

    private static Hardware decodeHardware(Version version, int mode, boolean modified)
            throws SpectrumSnapshotException {
        if (version == Version.V2) {
            return switch (mode) {
                case 0 -> modified ? unsupportedHardware(SPECTRUM_16K, "16K Spectrum") : Hardware.SPECTRUM_48K;
                case 1 -> unsupportedHardware(INTERFACE_1, "48K Spectrum with Interface 1");
                case 2 -> unsupportedHardware(SAM_RAM, "SamRam");
                case 3 -> modified ? unsupportedHardware(SPECTRUM_PLUS_2, "Spectrum +2") : Hardware.SPECTRUM_128K;
                case 4 -> unsupportedHardware(INTERFACE_1, "128K Spectrum with Interface 1");
                default -> unsupportedHardware(UNKNOWN_HARDWARE, "Z80 v2 hardware mode " + mode);
            };
        }

        return switch (mode) {
            case 0 -> modified ? unsupportedHardware(SPECTRUM_16K, "16K Spectrum") : Hardware.SPECTRUM_48K;
            case 1 -> unsupportedHardware(INTERFACE_1, "48K Spectrum with Interface 1");
            case 2 -> unsupportedHardware(SAM_RAM, "SamRam");
            case 3 -> unsupportedHardware(MGT, "48K Spectrum with MGT");
            case 4 -> modified ? unsupportedHardware(SPECTRUM_PLUS_2, "Spectrum +2") : Hardware.SPECTRUM_128K;
            case 5 -> unsupportedHardware(INTERFACE_1, "128K Spectrum with Interface 1");
            case 6 -> unsupportedHardware(MGT, "128K Spectrum with MGT");
            case 7, 8 -> modified
                    ? unsupportedHardware(SPECTRUM_PLUS_2A, "Spectrum +2A")
                    : unsupportedHardware(SPECTRUM_PLUS_3, "Spectrum +3");
            case 9 -> unsupportedHardware(PENTAGON, "Pentagon 128K");
            case 10 -> unsupportedHardware(SCORPION, "Scorpion 256K");
            case 12 -> unsupportedHardware(SPECTRUM_PLUS_2, "Spectrum +2");
            case 13 -> unsupportedHardware(SPECTRUM_PLUS_2A, "Spectrum +2A");
            case 14, 15, 128 -> unsupportedHardware(TIMEX, "Timex Spectrum");
            default -> unsupportedHardware(UNKNOWN_HARDWARE, "Z80 v3 hardware mode " + mode);
        };
    }

    private static Hardware unsupportedHardware(
            SpectrumSnapshotException.UnsupportedFeature feature,
            String name
    ) throws SpectrumSnapshotException {
        throw new SpectrumSnapshotException(UNSUPPORTED, feature, name + " snapshots are not supported");
    }

    private static void rejectUnsupportedExtensions(
            byte[] image,
            int additionalLength,
            Hardware hardware,
            int flags2
    ) throws SpectrumSnapshotException {
        if (SnapshotBytes.u8(image, 36) != 0) {
            throw new SpectrumSnapshotException(
                    UNSUPPORTED,
                    INTERFACE_1,
                    "Z80 snapshot has an Interface 1 ROM paged"
            );
        }
        if ((flags2 & 0x44) == 0x44) {
            throw new SpectrumSnapshotException(
                    UNSUPPORTED,
                    FULLER_AUDIO,
                    "Z80 snapshot requires Fuller Audio Box emulation"
            );
        }
        if (!hardware.is128K()) {
            if ((flags2 & 0x04) != 0) {
                throw new SpectrumSnapshotException(
                        UNSUPPORTED,
                        AY_EXTENSION,
                        "Stock 48K Z80 snapshot cannot restore the carried AY extension state"
                );
            }
        }
        if (additionalLength >= V3_ADDITIONAL_HEADER_SIZE) {
            if (SnapshotBytes.u8(image, 59) != 0) {
                throw new SpectrumSnapshotException(UNSUPPORTED, MGT, "Z80 snapshot has an MGT ROM paged");
            }
            if (SnapshotBytes.u8(image, 60) != 0) {
                throw new SpectrumSnapshotException(
                        UNSUPPORTED,
                        SpectrumSnapshotException.UnsupportedFeature.MULTIFACE,
                        "Z80 snapshot has a Multiface ROM paged"
                );
            }
            if (SnapshotBytes.u8(image, 61) != 0xFF || SnapshotBytes.u8(image, 62) != 0xFF) {
                throw new SpectrumSnapshotException(
                        UNSUPPORTED,
                        FORMAT_EXTENSION,
                        "Z80 snapshot maps RAM into part of 0x0000..0x3FFF instead of the stock ROM"
                );
            }
        }
        if (additionalLength == V3_EXTENDED_ADDITIONAL_HEADER_SIZE && SnapshotBytes.u8(image, 86) != 0) {
            throw new SpectrumSnapshotException(
                    UNSUPPORTED,
                    FORMAT_EXTENSION,
                    "Z80 snapshot carries a non-zero 0x1FFD paging state"
            );
        }
    }

    private enum Version {
        V2,
        V3
    }

    private enum Hardware {
        SPECTRUM_48K(false),
        SPECTRUM_128K(true);

        private final boolean is128K;

        Hardware(boolean is128K) {
            this.is128K = is128K;
        }

        boolean is128K() {
            return is128K;
        }
    }

    public enum Compression {
        UNCOMPRESSED,
        COMPRESSED
    }
}
