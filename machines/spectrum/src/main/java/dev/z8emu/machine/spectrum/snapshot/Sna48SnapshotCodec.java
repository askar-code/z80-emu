package dev.z8emu.machine.spectrum.snapshot;

import java.util.Arrays;
import java.util.Objects;

import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.MALFORMED;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.UNREPRESENTABLE;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.UNSUPPORTED;

/** Encoder and decoder for the original 48K SNA image layout. */
public final class Sna48SnapshotCodec {
    public static final int HEADER_SIZE = 27;
    public static final int IMAGE_SIZE = HEADER_SIZE + Spectrum48Snapshot.RAM_SIZE;
    private static final int SNA_128_IMAGE_SIZE = 131_103;
    private static final int SNA_128_EXTENDED_IMAGE_SIZE = 147_487;
    private static final int RAM_BASE = 0x4000;

    private Sna48SnapshotCodec() {
    }

    public static Spectrum48Snapshot decode(byte[] image) throws SpectrumSnapshotException {
        Objects.requireNonNull(image, "image");
        if (image.length == SNA_128_IMAGE_SIZE || image.length == SNA_128_EXTENDED_IMAGE_SIZE) {
            throw new SpectrumSnapshotException(
                    UNSUPPORTED,
                    "128K SNA snapshots are not supported by the 48K SNA codec (image size " + image.length + ")"
            );
        }
        if (image.length != IMAGE_SIZE) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "48K SNA image must be exactly 49179 bytes, got " + image.length
            );
        }

        int interruptMode = SnapshotBytes.u8(image, 25) & 0x03;
        if (interruptMode == 3) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "48K SNA header has invalid interrupt mode " + interruptMode + " at offset 25"
            );
        }
        int borderColor = SnapshotBytes.u8(image, 26) & 0x07;

        int storedSp = SnapshotBytes.le16(image, 23);
        if (storedSp < RAM_BASE || storedSp > 0xFFFE) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "48K SNA stored SP must address two bytes of RAM (0x4000..0xFFFE), got 0x"
                            + hex16(storedSp)
            );
        }

        byte[] ram = Arrays.copyOfRange(image, HEADER_SIZE, image.length);
        int pcOffset = storedSp - RAM_BASE;
        int pc = Byte.toUnsignedInt(ram[pcOffset]) | (Byte.toUnsignedInt(ram[pcOffset + 1]) << 8);
        boolean iff2 = (SnapshotBytes.u8(image, 19) & 0x04) != 0;

        Z80SnapshotState cpu = new Z80SnapshotState(
                SnapshotBytes.le16(image, 21),
                SnapshotBytes.le16(image, 13),
                SnapshotBytes.le16(image, 11),
                SnapshotBytes.le16(image, 9),
                SnapshotBytes.le16(image, 7),
                SnapshotBytes.le16(image, 5),
                SnapshotBytes.le16(image, 3),
                SnapshotBytes.le16(image, 1),
                SnapshotBytes.le16(image, 17),
                SnapshotBytes.le16(image, 15),
                (storedSp + 2) & 0xFFFF,
                pc,
                SnapshotBytes.u8(image, 0),
                SnapshotBytes.u8(image, 20),
                iff2,
                iff2,
                interruptMode
        );
        return new Spectrum48Snapshot(cpu, borderColor, ram);
    }

    public static byte[] encode(Spectrum48Snapshot snapshot) throws SpectrumSnapshotException {
        Objects.requireNonNull(snapshot, "snapshot");
        Z80SnapshotState cpu = snapshot.cpu();
        if (cpu.sp() < RAM_BASE + 2) {
            throw new SpectrumSnapshotException(
                    UNREPRESENTABLE,
                    "48K SNA cannot store PC on the emulated stack: SP 0x" + hex16(cpu.sp())
                            + " is below 0x4002"
            );
        }
        int storedSp = cpu.sp() - 2;

        byte[] image = new byte[IMAGE_SIZE];
        SnapshotBytes.put8(image, 0, cpu.i());
        SnapshotBytes.putLe16(image, 1, cpu.hlAlt());
        SnapshotBytes.putLe16(image, 3, cpu.deAlt());
        SnapshotBytes.putLe16(image, 5, cpu.bcAlt());
        SnapshotBytes.putLe16(image, 7, cpu.afAlt());
        SnapshotBytes.putLe16(image, 9, cpu.hl());
        SnapshotBytes.putLe16(image, 11, cpu.de());
        SnapshotBytes.putLe16(image, 13, cpu.bc());
        SnapshotBytes.putLe16(image, 15, cpu.iy());
        SnapshotBytes.putLe16(image, 17, cpu.ix());
        SnapshotBytes.put8(image, 19, cpu.iff2() ? 0x04 : 0x00);
        SnapshotBytes.put8(image, 20, cpu.r());
        SnapshotBytes.putLe16(image, 21, cpu.af());
        SnapshotBytes.putLe16(image, 23, storedSp);
        SnapshotBytes.put8(image, 25, cpu.interruptMode());
        SnapshotBytes.put8(image, 26, snapshot.borderColor());
        snapshot.copyRamTo(image, HEADER_SIZE);

        int pcOffset = HEADER_SIZE + storedSp - RAM_BASE;
        SnapshotBytes.putLe16(image, pcOffset, cpu.pc());
        return image;
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }
}
