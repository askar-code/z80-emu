package dev.z8emu.machine.spectrum.snapshot;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Z80V2V3SnapshotCodecTest {
    @Test
    void decodesCanonicalV2FixtureWithIndependentPageOrderPagingAndAyState() throws Exception {
        byte[] image = canonicalV2Fixture();

        Spectrum128Snapshot snapshot = assertInstanceOf(
                Spectrum128Snapshot.class,
                Z80V2V3SnapshotCodec.decode(image)
        );

        assertEquals(canonicalCpu(), snapshot.cpu());
        assertEquals(5, snapshot.borderColor());
        assertEquals(0x1E, snapshot.pagingPort7ffd());
        assertEquals(11, snapshot.selectedAyRegister());
        assertArrayEquals(ayData(), snapshot.ayRegisters());
        for (int bank = 0; bank < 8; bank++) {
            assertArrayEquals(bankData(bank), snapshot.ramBank(bank), "physical bank " + bank);
        }
    }

    @Test
    void decodesCanonicalV3Compressed48kFixtureWithoutUsingEncoder() throws Exception {
        byte[] image = canonicalV3Compressed48Fixture();

        Spectrum48Snapshot snapshot = assertInstanceOf(
                Spectrum48Snapshot.class,
                Z80V2V3SnapshotCodec.decode(image)
        );

        assertEquals(canonicalCpu(), snapshot.cpu());
        assertEquals(5, snapshot.borderColor());
        byte[] ram = snapshot.ram();
        assertFilled(ram, 0, 16_384, 0x88);
        assertFilled(ram, 16_384, 32_768, 0x44);
        assertFilled(ram, 32_768, 49_152, 0x55);
    }

    @ParameterizedTest
    @EnumSource(Z80V2V3SnapshotCodec.Compression.class)
    void canonicalV3EncoderRoundTripsAll128kState(Z80V2V3SnapshotCodec.Compression compression)
            throws Exception {
        Spectrum128Snapshot expected = sample128();

        byte[] image = Z80V2V3SnapshotCodec.encode(expected, compression);
        Spectrum128Snapshot actual = assertInstanceOf(
                Spectrum128Snapshot.class,
                Z80V2V3SnapshotCodec.decode(image)
        );

        assert128Equals(expected, actual);
        assertEquals(Z80V2V3SnapshotCodec.V3_ADDITIONAL_HEADER_SIZE, le16(image, 30));
        assertEquals(4, Byte.toUnsignedInt(image[34]));
        assertEquals(0, Byte.toUnsignedInt(image[37]));
        assertEquals(0, le16(image, 6));
        assertEquals(17_726, le16(image, 55));
        assertEquals(3, Byte.toUnsignedInt(image[57]));
        assertEquals(0xFF, Byte.toUnsignedInt(image[61]));
        assertEquals(0xFF, Byte.toUnsignedInt(image[62]));
    }

    @ParameterizedTest
    @EnumSource(Z80V2V3SnapshotCodec.Compression.class)
    void canonicalV3EncoderRoundTrips48kPageMapping(Z80V2V3SnapshotCodec.Compression compression)
            throws Exception {
        byte[] ram = new byte[Spectrum48Snapshot.RAM_SIZE];
        for (int offset = 0; offset < ram.length; offset++) {
            ram[offset] = (byte) (offset * 13 + (offset >>> 8));
        }
        Spectrum48Snapshot expected = new Spectrum48Snapshot(canonicalCpu(), 5, ram);

        Spectrum48Snapshot actual = assertInstanceOf(
                Spectrum48Snapshot.class,
                Z80V2V3SnapshotCodec.decode(Z80V2V3SnapshotCodec.encode(expected, compression))
        );

        assertEquals(expected.cpu(), actual.cpu());
        assertEquals(expected.borderColor(), actual.borderColor());
        assertArrayEquals(expected.ram(), actual.ram());
    }

    @Test
    void reportsZeroLengthTruncatedAndOverflowingRleAtPageBoundary() {
        SpectrumSnapshotException zero = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(v3WithFirstCompressedPage(
                        4,
                        new byte[]{(byte) 0xED, (byte) 0xED, 0, 0x42}
                ))
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, zero.reason());
        assertTrue(zero.getMessage().contains("Zero-length"));

        SpectrumSnapshotException truncated = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(v3WithFirstCompressedPage(
                        4,
                        new byte[]{(byte) 0xED, (byte) 0xED, 4}
                ))
        );
        assertTrue(truncated.getMessage().contains("Truncated ED ED"));

        ByteArrayOutputStream overflowPayload = new ByteArrayOutputStream();
        for (int index = 0; index < 65; index++) {
            writeRun(overflowPayload, 255, 0x11);
        }
        SpectrumSnapshotException overflow = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(v3WithFirstCompressedPage(
                        4,
                        overflowPayload.toByteArray()
                ))
        );
        assertTrue(overflow.getMessage().contains("beyond 16384"));
    }

    @Test
    void rejectsDuplicateMissingAndUnsupportedMemoryPages() {
        ByteArrayOutputStream duplicate = new ByteArrayOutputStream();
        duplicate.writeBytes(v3Header(0, 54));
        writeUncompressedPage(duplicate, 4, new byte[16_384]);
        writeUncompressedPage(duplicate, 4, new byte[16_384]);
        SpectrumSnapshotException duplicateError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(duplicate.toByteArray())
        );
        assertTrue(duplicateError.getMessage().contains("Duplicate"));

        ByteArrayOutputStream missing = new ByteArrayOutputStream();
        missing.writeBytes(v3Header(0, 54));
        writeUncompressedPage(missing, 4, new byte[16_384]);
        SpectrumSnapshotException missingError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(missing.toByteArray())
        );
        assertTrue(missingError.getMessage().contains("missing required RAM page 5"));

        ByteArrayOutputStream unsupported = new ByteArrayOutputStream();
        unsupported.writeBytes(v3Header(0, 54));
        writeUncompressedPage(unsupported, 11, new byte[16_384]);
        SpectrumSnapshotException pageError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(unsupported.toByteArray())
        );
        assertEquals(
                SpectrumSnapshotException.UnsupportedFeature.MULTIFACE,
                pageError.unsupportedFeature().orElseThrow()
        );

        ByteArrayOutputStream slt = new ByteArrayOutputStream();
        slt.writeBytes(canonicalV3Compressed48Fixture());
        slt.writeBytes(new byte[]{0, 0, 0, 'S', 'L', 'T'});
        SpectrumSnapshotException sltError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(slt.toByteArray())
        );
        assertEquals(
                SpectrumSnapshotException.UnsupportedFeature.FORMAT_EXTENSION,
                sltError.unsupportedFeature().orElseThrow()
        );
    }

    @Test
    void classifiesSamRamPlusTwoPlusThreeAndHeaderExtensionPrecisely() {
        assertUnsupportedHardware(2, SpectrumSnapshotException.UnsupportedFeature.SAM_RAM);
        assertUnsupportedHardware(3, SpectrumSnapshotException.UnsupportedFeature.MGT);
        assertUnsupportedHardware(12, SpectrumSnapshotException.UnsupportedFeature.SPECTRUM_PLUS_2);
        assertUnsupportedHardware(7, SpectrumSnapshotException.UnsupportedFeature.SPECTRUM_PLUS_3);

        byte[] plus2Modified = v3Header(4, 54);
        plus2Modified[37] = (byte) 0x80;
        SpectrumSnapshotException plus2 = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(plus2Modified)
        );
        assertEquals(
                SpectrumSnapshotException.UnsupportedFeature.SPECTRUM_PLUS_2,
                plus2.unsupportedFeature().orElseThrow()
        );

        byte[] oneFffd = v3Header(4, 55);
        oneFffd[86] = 1;
        SpectrumSnapshotException extension = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(oneFffd)
        );
        assertEquals(
                SpectrumSnapshotException.UnsupportedFeature.FORMAT_EXTENSION,
                extension.unsupportedFeature().orElseThrow()
        );
    }

    @Test
    void rejectsAyExtensionOnStock48kButCarriesItOn128k() {
        byte[] image = v3Header(0, 54);
        image[37] = 0x04;
        image[39] = 0x55;

        SpectrumSnapshotException error = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(image)
        );

        assertEquals(
                SpectrumSnapshotException.UnsupportedFeature.AY_EXTENSION,
                error.unsupportedFeature().orElseThrow()
        );
    }

    @Test
    void rejectsOutOfRangeV3FrameCounters() {
        byte[] image = v3Header(4, 54);
        putLe16(image, 55, 17_727);
        byte[] invalidLow = image;

        SpectrumSnapshotException low = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(invalidLow)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, low.reason());
        assertTrue(low.getMessage().contains("frame counter"));

        image = v3Header(4, 54);
        image[57] = 4;
        byte[] invalidHigh = image;
        SpectrumSnapshotException high = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(invalidHigh)
        );
        assertTrue(high.getMessage().contains("high=4"));
    }

    private static byte[] canonicalV2Fixture() {
        byte[] header = baseHeader(23);
        header[34] = 3;
        header[35] = 0x1E;
        header[38] = 11;
        System.arraycopy(ayData(), 0, header, 39, 16);
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        image.writeBytes(header);
        for (int page : new int[]{10, 3, 8, 4, 9, 5, 7, 6}) {
            writeUncompressedPage(image, page, bankData(page - 3));
        }
        return image.toByteArray();
    }

    private static byte[] canonicalV3Compressed48Fixture() {
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        image.writeBytes(v3Header(0, 54));
        writeCompressedFilledPage(image, 5, 0x55);
        writeCompressedFilledPage(image, 8, 0x88);
        writeCompressedFilledPage(image, 4, 0x44);
        return image.toByteArray();
    }

    private static byte[] v3WithFirstCompressedPage(int page, byte[] payload) {
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        image.writeBytes(v3Header(0, 54));
        image.write(payload.length & 0xFF);
        image.write((payload.length >>> 8) & 0xFF);
        image.write(page);
        image.writeBytes(payload);
        return image.toByteArray();
    }

    private static byte[] v3Header(int hardwareMode, int additionalLength) {
        byte[] header = baseHeader(additionalLength);
        header[34] = (byte) hardwareMode;
        if (additionalLength >= 54) {
            header[61] = (byte) 0xFF;
            header[62] = (byte) 0xFF;
        }
        return header;
    }

    private static byte[] baseHeader(int additionalLength) {
        byte[] image = new byte[32 + additionalLength];
        image[0] = (byte) 0xAB;
        image[1] = (byte) 0xCD;
        putLe16(image, 2, 0xDDEE);
        putLe16(image, 4, 0x99AA);
        putLe16(image, 6, 0);
        putLe16(image, 8, 0x9002);
        image[10] = 0x3C;
        image[11] = 0x11;
        image[12] = (byte) (0x01 | (5 << 1));
        putLe16(image, 13, 0xBBCC);
        putLe16(image, 15, 0x5566);
        putLe16(image, 17, 0x3344);
        putLe16(image, 19, 0x1122);
        image[21] = 0x77;
        image[22] = (byte) 0x88;
        putLe16(image, 23, 0x1357);
        putLe16(image, 25, 0x2468);
        image[27] = 1;
        image[28] = 0;
        image[29] = 2;
        putLe16(image, 30, additionalLength);
        putLe16(image, 32, 0x4567);
        return image;
    }

    private static void writeCompressedFilledPage(ByteArrayOutputStream image, int page, int value) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        int remaining = 16_384;
        while (remaining > 0) {
            int count = Math.min(remaining, 255);
            writeRun(payload, count, value);
            remaining -= count;
        }
        byte[] bytes = payload.toByteArray();
        image.write(bytes.length & 0xFF);
        image.write((bytes.length >>> 8) & 0xFF);
        image.write(page);
        image.writeBytes(bytes);
    }

    private static void writeUncompressedPage(ByteArrayOutputStream image, int page, byte[] data) {
        image.write(0xFF);
        image.write(0xFF);
        image.write(page);
        image.writeBytes(data);
    }

    private static void writeRun(ByteArrayOutputStream output, int count, int value) {
        output.write(0xED);
        output.write(0xED);
        output.write(count);
        output.write(value);
    }

    private static Spectrum128Snapshot sample128() {
        return new Spectrum128Snapshot(canonicalCpu(), 5, 0x3E, 11, ayData(), allBanks());
    }

    private static Z80SnapshotState canonicalCpu() {
        return new Z80SnapshotState(
                0xABCD, 0xDDEE, 0xBBCC, 0x99AA,
                0x7788, 0x5566, 0x3344, 0x1122,
                0x2468, 0x1357, 0x9002, 0x4567,
                0x3C, 0x91, true, false, 2
        );
    }

    private static byte[] ayData() {
        byte[] registers = new byte[16];
        for (int register = 0; register < registers.length; register++) {
            registers[register] = (byte) (register * 5 + 1);
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
        byte[] data = new byte[16_384];
        for (int offset = 0; offset < data.length; offset++) {
            data[offset] = (byte) (bank * 31 + offset * 11 + (offset >>> 7));
        }
        Arrays.fill(data, bank * 100, bank * 100 + 64, (byte) (0x40 + bank));
        return data;
    }

    private static void assertUnsupportedHardware(
            int mode,
            SpectrumSnapshotException.UnsupportedFeature feature
    ) {
        byte[] image = v3Header(mode, 54);
        SpectrumSnapshotException error = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V2V3SnapshotCodec.decode(image)
        );
        assertEquals(SpectrumSnapshotException.Reason.UNSUPPORTED, error.reason());
        assertEquals(feature, error.unsupportedFeature().orElseThrow());
    }

    private static void assert128Equals(Spectrum128Snapshot expected, Spectrum128Snapshot actual) {
        assertEquals(expected.cpu(), actual.cpu());
        assertEquals(expected.borderColor(), actual.borderColor());
        assertEquals(expected.pagingPort7ffd(), actual.pagingPort7ffd());
        assertEquals(expected.selectedAyRegister(), actual.selectedAyRegister());
        assertArrayEquals(expected.ayRegisters(), actual.ayRegisters());
        for (int bank = 0; bank < 8; bank++) {
            assertArrayEquals(expected.ramBank(bank), actual.ramBank(bank));
        }
    }

    private static void assertFilled(byte[] data, int from, int to, int value) {
        for (int offset = from; offset < to; offset++) {
            assertEquals(value, Byte.toUnsignedInt(data[offset]), "RAM offset " + offset);
        }
    }

    private static void putLe16(byte[] image, int offset, int value) {
        image[offset] = (byte) value;
        image[offset + 1] = (byte) (value >>> 8);
    }

    private static int le16(byte[] image, int offset) {
        return Byte.toUnsignedInt(image[offset]) | (Byte.toUnsignedInt(image[offset + 1]) << 8);
    }
}
