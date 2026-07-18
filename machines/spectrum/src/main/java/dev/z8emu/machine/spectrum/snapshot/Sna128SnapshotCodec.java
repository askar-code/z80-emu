package dev.z8emu.machine.spectrum.snapshot;

import java.util.Arrays;
import java.util.Objects;

import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.MALFORMED;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.UNREPRESENTABLE;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.Reason.UNSUPPORTED;
import static dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException.UnsupportedFeature.TR_DOS;

/** Encoder and decoder for the de-facto 128K extension of the SNA format. */
public final class Sna128SnapshotCodec {
    public static final int HEADER_SIZE = 27;
    public static final int BASE_IMAGE_SIZE = HEADER_SIZE + 3 * Spectrum128Snapshot.RAM_BANK_SIZE;
    public static final int IMAGE_SIZE = 131_103;
    public static final int DUPLICATE_FIXED_BANK_IMAGE_SIZE = 147_487;
    private static final int EXTENDED_HEADER_SIZE = 4;

    private Sna128SnapshotCodec() {
    }

    public static Spectrum128Snapshot decode(byte[] image) throws SpectrumSnapshotException {
        Objects.requireNonNull(image, "image");
        if (image.length == Sna48SnapshotCodec.IMAGE_SIZE) {
            throw new SpectrumSnapshotException(
                    UNSUPPORTED,
                    "48K SNA snapshots are not supported by the 128K SNA codec"
            );
        }
        if (image.length != IMAGE_SIZE && image.length != DUPLICATE_FIXED_BANK_IMAGE_SIZE) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "128K SNA image must be exactly 131103 or 147487 bytes, got " + image.length
            );
        }

        int interruptMode = SnapshotBytes.u8(image, 25) & 0x03;
        if (interruptMode == 3) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "128K SNA header has invalid interrupt mode 3 at offset 25"
            );
        }
        int extensionOffset = BASE_IMAGE_SIZE;
        int pc = SnapshotBytes.le16(image, extensionOffset);
        int pagingPort7ffd = SnapshotBytes.u8(image, extensionOffset + 2);
        int pagedBank = pagingPort7ffd & 0x07;
        int trDosPaged = SnapshotBytes.u8(image, extensionOffset + 3);
        if (trDosPaged != 0) {
            throw new SpectrumSnapshotException(
                    UNSUPPORTED,
                    TR_DOS,
                    "128K SNA has the TR-DOS ROM paged (extension byte is " + trDosPaged + ")"
            );
        }

        boolean duplicatesFixedBank = pagedBank == 2 || pagedBank == 5;
        int expectedSize = duplicatesFixedBank ? DUPLICATE_FIXED_BANK_IMAGE_SIZE : IMAGE_SIZE;
        if (image.length != expectedSize) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "128K SNA paging value 0x" + hex8(pagingPort7ffd)
                            + " requires " + expectedSize + " bytes because RAM bank " + pagedBank
                            + (duplicatesFixedBank ? " duplicates a fixed CPU page" : " is a distinct CPU page")
            );
        }

        byte[][] ramBanks = new byte[Spectrum128Snapshot.RAM_BANK_COUNT][];
        ramBanks[5] = block(image, HEADER_SIZE);
        ramBanks[2] = block(image, HEADER_SIZE + Spectrum128Snapshot.RAM_BANK_SIZE);
        byte[] topPage = block(image, HEADER_SIZE + 2 * Spectrum128Snapshot.RAM_BANK_SIZE);
        if (ramBanks[pagedBank] == null) {
            ramBanks[pagedBank] = topPage;
        } else if (!Arrays.equals(ramBanks[pagedBank], topPage)) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "128K SNA contains conflicting copies of fixed RAM bank " + pagedBank
            );
        }

        int inputOffset = extensionOffset + EXTENDED_HEADER_SIZE;
        for (int bank = 0; bank < Spectrum128Snapshot.RAM_BANK_COUNT; bank++) {
            if (ramBanks[bank] == null) {
                ramBanks[bank] = block(image, inputOffset);
                inputOffset += Spectrum128Snapshot.RAM_BANK_SIZE;
            }
        }
        if (inputOffset != image.length) {
            throw new SpectrumSnapshotException(
                    MALFORMED,
                    "128K SNA has " + (image.length - inputOffset) + " unassigned RAM bytes"
            );
        }

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
                SnapshotBytes.le16(image, 23),
                pc,
                SnapshotBytes.u8(image, 0),
                SnapshotBytes.u8(image, 20),
                iff2,
                iff2,
                interruptMode
        );
        return new Spectrum128Snapshot(
                cpu,
                SnapshotBytes.u8(image, 26) & 0x07,
                pagingPort7ffd,
                0,
                new byte[Spectrum128Snapshot.AY_REGISTER_COUNT],
                ramBanks
        );
    }

    public static byte[] encode(Spectrum128Snapshot snapshot) throws SpectrumSnapshotException {
        Objects.requireNonNull(snapshot, "snapshot");
        Z80SnapshotState cpu = snapshot.cpu();
        if (cpu.iff1() != cpu.iff2()) {
            throw new SpectrumSnapshotException(
                    UNREPRESENTABLE,
                    "128K SNA stores only IFF2 and cannot preserve differing IFF1/IFF2 values"
            );
        }

        int pagedBank = snapshot.pagingPort7ffd() & 0x07;
        boolean duplicatesFixedBank = pagedBank == 2 || pagedBank == 5;
        byte[] image = new byte[duplicatesFixedBank ? DUPLICATE_FIXED_BANK_IMAGE_SIZE : IMAGE_SIZE];
        writeCpuHeader(image, cpu, snapshot.borderColor());
        snapshot.copyRamBankTo(5, image, HEADER_SIZE);
        snapshot.copyRamBankTo(2, image, HEADER_SIZE + Spectrum128Snapshot.RAM_BANK_SIZE);
        snapshot.copyRamBankTo(pagedBank, image, HEADER_SIZE + 2 * Spectrum128Snapshot.RAM_BANK_SIZE);

        int extensionOffset = BASE_IMAGE_SIZE;
        SnapshotBytes.putLe16(image, extensionOffset, cpu.pc());
        SnapshotBytes.put8(image, extensionOffset + 2, snapshot.pagingPort7ffd());
        SnapshotBytes.put8(image, extensionOffset + 3, 0);

        int outputOffset = extensionOffset + EXTENDED_HEADER_SIZE;
        for (int bank = 0; bank < Spectrum128Snapshot.RAM_BANK_COUNT; bank++) {
            if (bank != 5 && bank != 2 && bank != pagedBank) {
                snapshot.copyRamBankTo(bank, image, outputOffset);
                outputOffset += Spectrum128Snapshot.RAM_BANK_SIZE;
            }
        }
        if (outputOffset != image.length) {
            throw new IllegalStateException("Internal 128K SNA bank accounting error");
        }
        return image;
    }

    private static void writeCpuHeader(byte[] image, Z80SnapshotState cpu, int borderColor) {
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
        SnapshotBytes.putLe16(image, 23, cpu.sp());
        SnapshotBytes.put8(image, 25, cpu.interruptMode());
        SnapshotBytes.put8(image, 26, borderColor);
    }

    private static byte[] block(byte[] image, int offset) {
        return Arrays.copyOfRange(image, offset, offset + Spectrum128Snapshot.RAM_BANK_SIZE);
    }

    private static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
    }
}
