package dev.z8emu.machine.spectrum.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sna48SnapshotCodecTest {
    @Test
    void decodesCanonical48kFixtureIncludingPcFromStackImage() throws Exception {
        byte[] image = canonicalFixture();

        Spectrum48Snapshot snapshot = Sna48SnapshotCodec.decode(image);

        assertEquals(new Z80SnapshotState(
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
                true,
                2
        ), snapshot.cpu());
        assertEquals(5, snapshot.borderColor());
        assertEquals(0x0B, Byte.toUnsignedInt(snapshot.ram()[0]));
        assertEquals(0x67, Byte.toUnsignedInt(snapshot.ram()[0x5000]));
        assertEquals(0x45, Byte.toUnsignedInt(snapshot.ram()[0x5001]));
    }

    @Test
    void roundTripPreservesCanonicalState() throws Exception {
        Spectrum48Snapshot decoded = Sna48SnapshotCodec.decode(canonicalFixture());

        byte[] encoded = Sna48SnapshotCodec.encode(decoded);
        Spectrum48Snapshot roundTripped = Sna48SnapshotCodec.decode(encoded);

        assertSnapshotEquals(decoded, roundTripped);
        assertEquals(Sna48SnapshotCodec.IMAGE_SIZE, encoded.length);
    }

    @Test
    void rejectsMalformedAndUnsupportedImagesWithSpecificReasons() {
        SpectrumSnapshotException wrongSize = assertThrows(
                SpectrumSnapshotException.class,
                () -> Sna48SnapshotCodec.decode(new byte[100])
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, wrongSize.reason());
        assertTrue(wrongSize.getMessage().contains("exactly 49179 bytes"));

        SpectrumSnapshotException sna128 = assertThrows(
                SpectrumSnapshotException.class,
                () -> Sna48SnapshotCodec.decode(new byte[131_103])
        );
        assertEquals(SpectrumSnapshotException.Reason.UNSUPPORTED, sna128.reason());
        assertTrue(sna128.getMessage().contains("128K SNA"));

        byte[] invalidIm = canonicalFixture();
        invalidIm[25] = 3;
        SpectrumSnapshotException imError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Sna48SnapshotCodec.decode(invalidIm)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, imError.reason());
        assertTrue(imError.getMessage().contains("offset 25"));

        byte[] invalidStack = canonicalFixture();
        putLe16(invalidStack, 23, 0x3FFF);
        SpectrumSnapshotException stackError = assertThrows(
                SpectrumSnapshotException.class,
                () -> Sna48SnapshotCodec.decode(invalidStack)
        );
        assertEquals(SpectrumSnapshotException.Reason.MALFORMED, stackError.reason());
        assertTrue(stackError.getMessage().contains("0x3FFF"));
    }

    @Test
    void rejectsStateWhosePcCannotBePlacedOnTheSnaStack() {
        Spectrum48Snapshot snapshot = new Spectrum48Snapshot(
                new Z80SnapshotState(
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0,
                        0x4001,
                        0x1234,
                        0, 0,
                        false, false, 0
                ),
                0,
                new byte[Spectrum48Snapshot.RAM_SIZE]
        );

        SpectrumSnapshotException error = assertThrows(
                SpectrumSnapshotException.class,
                () -> Sna48SnapshotCodec.encode(snapshot)
        );

        assertEquals(SpectrumSnapshotException.Reason.UNREPRESENTABLE, error.reason());
        assertTrue(error.getMessage().contains("SP 0x4001"));
    }

    @Test
    void masksReservedHeaderBitsButStillRejectsInterruptModeThree() throws Exception {
        byte[] image = canonicalFixture();
        image[25] = (byte) 0x82;
        image[26] = (byte) 0xFD;

        Spectrum48Snapshot snapshot = Sna48SnapshotCodec.decode(image);

        assertEquals(2, snapshot.cpu().interruptMode());
        assertEquals(5, snapshot.borderColor());
    }

    private static byte[] canonicalFixture() {
        byte[] image = new byte[Sna48SnapshotCodec.IMAGE_SIZE];
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
        putLe16(image, 23, 0x9000);
        image[25] = 2;
        image[26] = 5;
        for (int offset = 0; offset < Spectrum48Snapshot.RAM_SIZE; offset++) {
            image[Sna48SnapshotCodec.HEADER_SIZE + offset] = (byte) ((offset * 37 + 11) & 0xFF);
        }
        putLe16(image, Sna48SnapshotCodec.HEADER_SIZE + 0x5000, 0x4567);
        return image;
    }

    private static void putLe16(byte[] image, int offset, int value) {
        image[offset] = (byte) value;
        image[offset + 1] = (byte) (value >>> 8);
    }

    private static void assertSnapshotEquals(Spectrum48Snapshot expected, Spectrum48Snapshot actual) {
        assertEquals(expected.cpu(), actual.cpu());
        assertEquals(expected.borderColor(), actual.borderColor());
        assertArrayEquals(expected.ram(), actual.ram());
    }
}
