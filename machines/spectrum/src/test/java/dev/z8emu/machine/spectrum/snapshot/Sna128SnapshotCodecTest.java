package dev.z8emu.machine.spectrum.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sna128SnapshotCodecTest {
    @Test
    void decodesCanonical131103ByteFixtureAndMapsEveryPhysicalBank() throws Exception {
        byte[] image = canonicalFixture(3, false);

        Spectrum128Snapshot snapshot = Sna128SnapshotCodec.decode(image);

        assertEquals(canonicalCpu(), snapshot.cpu());
        assertEquals(6, snapshot.borderColor());
        assertEquals(0x1B, snapshot.pagingPort7ffd());
        for (int bank = 0; bank < Spectrum128Snapshot.RAM_BANK_COUNT; bank++) {
            assertArrayEquals(bankData(bank), snapshot.ramBank(bank), "physical bank " + bank);
        }
        assertArrayEquals(new byte[16], snapshot.ayRegisters());
        assertEquals(0, snapshot.selectedAyRegister());
    }

    @Test
    void decodesRealExtendedLayoutWhenTopPageDuplicatesFixedBankFive() throws Exception {
        byte[] image = canonicalFixture(5, true);

        Spectrum128Snapshot snapshot = Sna128SnapshotCodec.decode(image);

        assertEquals(0x3D, snapshot.pagingPort7ffd());
        for (int bank = 0; bank < Spectrum128Snapshot.RAM_BANK_COUNT; bank++) {
            assertArrayEquals(bankData(bank), snapshot.ramBank(bank), "physical bank " + bank);
        }
    }

    @Test
    void canonicalEncoderChoosesSizeFromDistinctPhysicalPages() throws Exception {
        Spectrum128Snapshot ordinary = sampleSnapshot(6);
        Spectrum128Snapshot duplicate = sampleSnapshot(2);

        byte[] ordinaryImage = Sna128SnapshotCodec.encode(ordinary);
        byte[] duplicateImage = Sna128SnapshotCodec.encode(duplicate);

        assertEquals(Sna128SnapshotCodec.IMAGE_SIZE, ordinaryImage.length);
        assertEquals(Sna128SnapshotCodec.DUPLICATE_FIXED_BANK_IMAGE_SIZE, duplicateImage.length);
        assertSnapshotEquals(ordinary, Sna128SnapshotCodec.decode(ordinaryImage), false);
        assertSnapshotEquals(duplicate, Sna128SnapshotCodec.decode(duplicateImage), false);
    }

    @Test
    void rejectsWrongSizeForFixedPageAndConflictingDuplicate() {
        byte[] shortFixedPage = canonicalFixture(3, false);
        shortFixedPage[Sna128SnapshotCodec.BASE_IMAGE_SIZE + 2] = 5;
        SpectrumSnapshotException sizeError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Sna128SnapshotCodec.decode(shortFixedPage)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, sizeError.reason());
        assertTrue(sizeError.getMessage().contains("147487"));

        byte[] conflict = canonicalFixture(5, true);
        conflict[Sna128SnapshotCodec.HEADER_SIZE + 2 * Spectrum128Snapshot.RAM_BANK_SIZE] ^= 1;
        SpectrumSnapshotException conflictError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Sna128SnapshotCodec.decode(conflict)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, conflictError.reason());
        assertTrue(conflictError.getMessage().contains("conflicting copies"));
    }

    @Test
    void reportsTrDosAsTypedUnsupportedState() {
        byte[] image = canonicalFixture(3, false);
        image[Sna128SnapshotCodec.BASE_IMAGE_SIZE + 3] = 1;

        SpectrumSnapshotException error = assertThrows(
                SpectrumSnapshotException.class,
                () -> Sna128SnapshotCodec.decode(image)
        );

        assertEquals(SpectrumSnapshotException.Reason.UNSUPPORTED, error.reason());
        assertEquals(
                SpectrumSnapshotException.UnsupportedFeature.TR_DOS,
                error.unsupportedFeature().orElseThrow()
        );
    }

    @Test
    void refusesToSilentlyLoseDifferentInterruptFlipFlops() {
        Spectrum128Snapshot sample = sampleSnapshot(3);
        Z80SnapshotState cpu = sample.cpu();
        Spectrum128Snapshot unrepresentable = new Spectrum128Snapshot(
                new Z80SnapshotState(
                        cpu.af(), cpu.bc(), cpu.de(), cpu.hl(),
                        cpu.afAlt(), cpu.bcAlt(), cpu.deAlt(), cpu.hlAlt(),
                        cpu.ix(), cpu.iy(), cpu.sp(), cpu.pc(), cpu.i(), cpu.r(),
                        true, false, cpu.interruptMode()
                ),
                sample.borderColor(), sample.pagingPort7ffd(), sample.selectedAyRegister(),
                sample.ayRegisters(), allBanks()
        );

        SpectrumSnapshotException error = assertThrows(
                SpectrumSnapshotException.class,
                () -> Sna128SnapshotCodec.encode(unrepresentable)
        );

        assertEquals(SpectrumSnapshotException.Reason.UNREPRESENTABLE, error.reason());
        assertTrue(error.getMessage().contains("IFF1/IFF2"));
    }

    private static byte[] canonicalFixture(int pagedBank, boolean extended) {
        byte[] image = new byte[extended
                ? Sna128SnapshotCodec.DUPLICATE_FIXED_BANK_IMAGE_SIZE
                : Sna128SnapshotCodec.IMAGE_SIZE];
        writeHeader(image);
        putBank(image, Sna128SnapshotCodec.HEADER_SIZE, 5);
        putBank(image, Sna128SnapshotCodec.HEADER_SIZE + Spectrum128Snapshot.RAM_BANK_SIZE, 2);
        putBank(image, Sna128SnapshotCodec.HEADER_SIZE + 2 * Spectrum128Snapshot.RAM_BANK_SIZE, pagedBank);
        int extension = Sna128SnapshotCodec.BASE_IMAGE_SIZE;
        putLe16(image, extension, 0x4567);
        image[extension + 2] = (byte) (0x18 | (extended ? 0x20 : 0) | pagedBank);
        image[extension + 3] = 0;

        int offset = extension + 4;
        for (int bank = 0; bank < Spectrum128Snapshot.RAM_BANK_COUNT; bank++) {
            if (bank != 5 && bank != 2 && bank != pagedBank) {
                putBank(image, offset, bank);
                offset += Spectrum128Snapshot.RAM_BANK_SIZE;
            }
        }
        assertEquals(image.length, offset);
        return image;
    }

    private static void writeHeader(byte[] image) {
        image[0] = 0x3C;
        putLe16(image, 1, 0x1122);
        putLe16(image, 3, 0x3344);
        putLe16(image, 5, 0x5566);
        putLe16(image, 7, 0x7788);
        putLe16(image, 9, 0x99AA);
        putLe16(image, 11, 0xBBCC);
        putLe16(image, 13, 0xDDEE);
        putLe16(image, 15, 0x1357);
        putLe16(image, 17, 0x2468);
        image[19] = 0x04;
        image[20] = (byte) 0x91;
        putLe16(image, 21, 0xABCD);
        putLe16(image, 23, 0x9002);
        image[25] = 2;
        image[26] = 6;
    }

    private static Spectrum128Snapshot sampleSnapshot(int pagedBank) {
        return new Spectrum128Snapshot(
                canonicalCpu(),
                6,
                0x18 | pagedBank,
                7,
                ayData(),
                allBanks()
        );
    }

    private static Z80SnapshotState canonicalCpu() {
        return new Z80SnapshotState(
                0xABCD, 0xDDEE, 0xBBCC, 0x99AA,
                0x7788, 0x5566, 0x3344, 0x1122,
                0x2468, 0x1357, 0x9002, 0x4567,
                0x3C, 0x91, true, true, 2
        );
    }

    private static byte[] ayData() {
        byte[] registers = new byte[16];
        for (int index = 0; index < registers.length; index++) {
            registers[index] = (byte) (index * 7 + 1);
        }
        return registers;
    }

    private static byte[][] allBanks() {
        byte[][] banks = new byte[8][];
        for (int bank = 0; bank < banks.length; bank++) {
            banks[bank] = bankData(bank);
        }
        return banks;
    }

    private static byte[] bankData(int bank) {
        byte[] data = new byte[Spectrum128Snapshot.RAM_BANK_SIZE];
        for (int offset = 0; offset < data.length; offset++) {
            data[offset] = (byte) (bank * 29 + offset * 17 + (offset >>> 8));
        }
        return data;
    }

    private static void putBank(byte[] image, int offset, int bank) {
        System.arraycopy(bankData(bank), 0, image, offset, Spectrum128Snapshot.RAM_BANK_SIZE);
    }

    private static void putLe16(byte[] image, int offset, int value) {
        image[offset] = (byte) value;
        image[offset + 1] = (byte) (value >>> 8);
    }

    private static void assertSnapshotEquals(
            Spectrum128Snapshot expected,
            Spectrum128Snapshot actual,
            boolean expectAy
    ) {
        assertEquals(expected.cpu(), actual.cpu());
        assertEquals(expected.borderColor(), actual.borderColor());
        assertEquals(expected.pagingPort7ffd(), actual.pagingPort7ffd());
        if (expectAy) {
            assertEquals(expected.selectedAyRegister(), actual.selectedAyRegister());
            assertArrayEquals(expected.ayRegisters(), actual.ayRegisters());
        }
        for (int bank = 0; bank < 8; bank++) {
            assertArrayEquals(expected.ramBank(bank), actual.ramBank(bank));
        }
    }
}
