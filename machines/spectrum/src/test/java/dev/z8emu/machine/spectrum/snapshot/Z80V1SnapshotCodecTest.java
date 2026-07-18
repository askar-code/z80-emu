package dev.z8emu.machine.spectrum.snapshot;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Z80V1SnapshotCodecTest {
    @Test
    void decodesCanonicalUncompressedV1Fixture() throws Exception {
        byte[] image = canonicalUncompressedFixture();

        Spectrum48Snapshot snapshot = Z80V1SnapshotCodec.decode(image);

        assertEquals(canonicalCpu(), snapshot.cpu());
        assertEquals(5, snapshot.borderColor());
        assertEquals(0x0B, Byte.toUnsignedInt(snapshot.ram()[0]));
        assertEquals(0xE6, Byte.toUnsignedInt(snapshot.ram()[Spectrum48Snapshot.RAM_SIZE - 1]));
    }

    @Test
    void treatsLegacyFlagsByteFfAsOne() throws Exception {
        byte[] image = canonicalUncompressedFixture();
        image[12] = (byte) 0xFF;

        Spectrum48Snapshot snapshot = Z80V1SnapshotCodec.decode(image);

        assertEquals(0x91, snapshot.cpu().r());
        assertEquals(0, snapshot.borderColor());
    }

    @Test
    void decodesCanonicalCompressedFixtureWithEdEdEscapeAndTerminator() throws Exception {
        byte[] image = canonicalCompressedFixture();

        Spectrum48Snapshot snapshot = Z80V1SnapshotCodec.decode(image);

        assertEquals(canonicalCpu(), snapshot.cpu());
        byte[] ram = snapshot.ram();
        assertEquals(0x01, Byte.toUnsignedInt(ram[0]));
        assertEquals(0xED, Byte.toUnsignedInt(ram[1]));
        assertEquals(0xED, Byte.toUnsignedInt(ram[2]));
        assertEquals(0xED, Byte.toUnsignedInt(ram[3]));
        assertEquals(0x02, Byte.toUnsignedInt(ram[4]));
        assertEquals(0x00, Byte.toUnsignedInt(ram[ram.length - 1]));
    }

    @ParameterizedTest
    @EnumSource(Z80V1SnapshotCodec.Compression.class)
    void roundTripPreservesAllRepresentableState(Z80V1SnapshotCodec.Compression compression) throws Exception {
        Spectrum48Snapshot original = sampleSnapshot();

        byte[] image = Z80V1SnapshotCodec.encode(original, compression);
        Spectrum48Snapshot decoded = Z80V1SnapshotCodec.decode(image);

        assertSnapshotEquals(original, decoded);
        if (compression == Z80V1SnapshotCodec.Compression.UNCOMPRESSED) {
            assertEquals(49_182, image.length);
        } else {
            assertArrayEquals(
                    new byte[]{0x00, (byte) 0xED, (byte) 0xED, 0x00},
                    Arrays.copyOfRange(image, image.length - 4, image.length)
            );
            assertTrue(contains(image, new byte[]{(byte) 0xED, (byte) 0xED, 0x03, (byte) 0xED}));
        }
    }

    @Test
    void classifiesV2V3AndMalformedV1ImagesPrecisely() {
        byte[] extended = new byte[Z80V1SnapshotCodec.HEADER_SIZE];
        SpectrumSnapshotException unsupported = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V1SnapshotCodec.decode(extended)
        );
        assertEquals(SpectrumSnapshotException.Reason.UNSUPPORTED, unsupported.reason());
        assertTrue(unsupported.getMessage().contains("v2/v3"));

        SpectrumSnapshotException shortHeader = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V1SnapshotCodec.decode(new byte[29])
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, shortHeader.reason());
        assertTrue(shortHeader.getMessage().contains("30-byte"));

        byte[] invalidIm = canonicalUncompressedFixture();
        invalidIm[29] = 3;
        SpectrumSnapshotException imError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V1SnapshotCodec.decode(invalidIm)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, imError.reason());
        assertTrue(imError.getMessage().contains("offset 29"));

        byte[] truncatedRam = Arrays.copyOf(canonicalUncompressedFixture(), 49_181);
        SpectrumSnapshotException sizeError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V1SnapshotCodec.decode(truncatedRam)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, sizeError.reason());
        assertTrue(sizeError.getMessage().contains("49182 bytes"));
    }

    @Test
    void reportsMalformedCompressedStreamsAtTheirFailureBoundary() {
        byte[] zeroRun = compressedImageWithPayload((byte) 0xED, (byte) 0xED, (byte) 0x00, (byte) 0x11);
        SpectrumSnapshotException zeroRunError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V1SnapshotCodec.decode(zeroRun)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, zeroRunError.reason());
        assertTrue(zeroRunError.getMessage().contains("Zero-length ED ED run"));

        byte[] missingTerminator = compressedImageWithPayload(
                (byte) 0xED, (byte) 0xED, (byte) 0xFF, (byte) 0x00
        );
        SpectrumSnapshotException terminatorError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V1SnapshotCodec.decode(missingTerminator)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, terminatorError.reason());
        assertTrue(terminatorError.getMessage().contains("missing the 00 ED ED 00 terminator"));

        byte[] earlyTerminator = compressedImageWithPayload(
                (byte) 0x00, (byte) 0xED, (byte) 0xED, (byte) 0x00
        );
        SpectrumSnapshotException lengthError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V1SnapshotCodec.decode(earlyTerminator)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, lengthError.reason());
        assertTrue(lengthError.getMessage().contains("terminates after 0 bytes"));

        byte[] trailing = Arrays.copyOf(canonicalCompressedFixture(), canonicalCompressedFixture().length + 1);
        SpectrumSnapshotException trailingError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V1SnapshotCodec.decode(trailing)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, trailingError.reason());
        assertTrue(trailingError.getMessage().contains("trailing bytes"));
    }

    @Test
    void rejectsPcZeroWhenEncodingV1() {
        Spectrum48Snapshot sample = sampleSnapshot();
        Z80SnapshotState cpu = sample.cpu();
        Spectrum48Snapshot pcZero = new Spectrum48Snapshot(
                new Z80SnapshotState(
                        cpu.af(), cpu.bc(), cpu.de(), cpu.hl(),
                        cpu.afAlt(), cpu.bcAlt(), cpu.deAlt(), cpu.hlAlt(),
                        cpu.ix(), cpu.iy(), cpu.sp(), 0,
                        cpu.i(), cpu.r(), cpu.iff1(), cpu.iff2(), cpu.interruptMode()
                ),
                sample.borderColor(),
                sample.ram()
        );

        SpectrumSnapshotException error = assertThrows(
                SpectrumSnapshotException.class,
                () -> Z80V1SnapshotCodec.encode(pcZero, Z80V1SnapshotCodec.Compression.COMPRESSED)
        );

        assertEquals(SpectrumSnapshotException.Reason.UNREPRESENTABLE, error.reason());
        assertTrue(error.getMessage().contains("PC 0x0000"));
    }

    @Test
    void snapshotRamIsDefensivelyCopied() {
        byte[] ram = new byte[Spectrum48Snapshot.RAM_SIZE];
        ram[0] = 1;
        Spectrum48Snapshot snapshot = new Spectrum48Snapshot(canonicalCpu(), 0, ram);

        ram[0] = 2;
        byte[] exposed = snapshot.ram();
        exposed[0] = 3;

        assertEquals(1, Byte.toUnsignedInt(snapshot.ram()[0]));
    }

    @Test
    void compressedEncoderDoesNotStartRunMarkerImmediatelyAfterLiteralEd() throws Exception {
        Spectrum48Snapshot sample = sampleSnapshot();
        byte[] ram = sample.ram();
        ram[0] = (byte) 0xED;
        Arrays.fill(ram, 1, 6, (byte) 0x42);
        Spectrum48Snapshot boundary = new Spectrum48Snapshot(sample.cpu(), sample.borderColor(), ram);

        byte[] image = Z80V1SnapshotCodec.encode(boundary, Z80V1SnapshotCodec.Compression.COMPRESSED);
        Spectrum48Snapshot decoded = Z80V1SnapshotCodec.decode(image);

        assertSnapshotEquals(boundary, decoded);
        assertArrayEquals(
                new byte[]{(byte) 0xED, 0x42, 0x42, 0x42, 0x42, 0x42},
                Arrays.copyOfRange(image, Z80V1SnapshotCodec.HEADER_SIZE, Z80V1SnapshotCodec.HEADER_SIZE + 6)
        );
    }

    private static byte[] canonicalUncompressedFixture() {
        byte[] image = new byte[Z80V1SnapshotCodec.HEADER_SIZE + Spectrum48Snapshot.RAM_SIZE];
        writeCanonicalHeader(image, false);
        for (int offset = 0; offset < Spectrum48Snapshot.RAM_SIZE; offset++) {
            image[Z80V1SnapshotCodec.HEADER_SIZE + offset] = (byte) ((offset * 37 + 11) & 0xFF);
        }
        return image;
    }

    private static byte[] canonicalCompressedFixture() {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(0x01);
        writeRun(payload, 3, 0xED);
        payload.write(0x02);
        int remaining = Spectrum48Snapshot.RAM_SIZE - 5;
        while (remaining > 0) {
            int count = Math.min(remaining, 255);
            writeRun(payload, count, 0x00);
            remaining -= count;
        }
        payload.write(0x00);
        payload.write(0xED);
        payload.write(0xED);
        payload.write(0x00);

        byte[] image = new byte[Z80V1SnapshotCodec.HEADER_SIZE + payload.size()];
        writeCanonicalHeader(image, true);
        System.arraycopy(payload.toByteArray(), 0, image, Z80V1SnapshotCodec.HEADER_SIZE, payload.size());
        return image;
    }

    private static byte[] compressedImageWithPayload(byte... payload) {
        byte[] image = new byte[Z80V1SnapshotCodec.HEADER_SIZE + payload.length];
        writeCanonicalHeader(image, true);
        System.arraycopy(payload, 0, image, Z80V1SnapshotCodec.HEADER_SIZE, payload.length);
        return image;
    }

    private static void writeCanonicalHeader(byte[] image, boolean compressed) {
        image[0] = (byte) 0xAB;
        image[1] = (byte) 0xCD;
        putLe16(image, 2, 0xDDEE);
        putLe16(image, 4, 0x99AA);
        putLe16(image, 6, 0x4567);
        putLe16(image, 8, 0x9002);
        image[10] = 0x3C;
        image[11] = 0x11;
        image[12] = (byte) (0x01 | (5 << 1) | (compressed ? 0x20 : 0));
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
    }

    private static Spectrum48Snapshot sampleSnapshot() {
        byte[] ram = new byte[Spectrum48Snapshot.RAM_SIZE];
        for (int offset = 0; offset < ram.length; offset++) {
            ram[offset] = (byte) ((offset * 29 + (offset >>> 8)) & 0xFF);
        }
        Arrays.fill(ram, 0x2000, 0x2100, (byte) 0x42);
        ram[0x0100] = 0x00;
        ram[0x0101] = (byte) 0xED;
        ram[0x0102] = (byte) 0xED;
        ram[0x0103] = 0x00;
        ram[0x3456] = (byte) 0xED;
        ram[0x3457] = (byte) 0xED;
        ram[0x3458] = (byte) 0xED;
        return new Spectrum48Snapshot(canonicalCpu(), 5, ram);
    }

    private static Z80SnapshotState canonicalCpu() {
        return new Z80SnapshotState(
                0xABCD,
                0xDDEE,
                0xBBCC,
                0x99AA,
                0x7788,
                0x5566,
                0x3344,
                0x1122,
                0x2468,
                0x1357,
                0x9002,
                0x4567,
                0x3C,
                0x91,
                true,
                false,
                2
        );
    }

    private static void writeRun(ByteArrayOutputStream output, int count, int value) {
        output.write(0xED);
        output.write(0xED);
        output.write(count);
        output.write(value);
    }

    private static void putLe16(byte[] image, int offset, int value) {
        image[offset] = (byte) value;
        image[offset + 1] = (byte) (value >>> 8);
    }

    private static boolean contains(byte[] image, byte[] needle) {
        for (int offset = 0; offset <= image.length - needle.length; offset++) {
            boolean match = true;
            for (int index = 0; index < needle.length; index++) {
                if (image[offset + index] != needle[index]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static void assertSnapshotEquals(Spectrum48Snapshot expected, Spectrum48Snapshot actual) {
        assertEquals(expected.cpu(), actual.cpu());
        assertEquals(expected.borderColor(), actual.borderColor());
        assertArrayEquals(expected.ram(), actual.ram());
    }
}
