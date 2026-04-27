package dev.z8emu.machine.apple2.disk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Apple2Gcr35MediaTest {
    @Test
    void usesApple35ZoneSectorCounts() {
        assertEquals(12, Apple2Gcr35Media.sectorsPerTrack(0));
        assertEquals(12, Apple2Gcr35Media.sectorsPerTrack(15));
        assertEquals(11, Apple2Gcr35Media.sectorsPerTrack(16));
        assertEquals(10, Apple2Gcr35Media.sectorsPerTrack(32));
        assertEquals(9, Apple2Gcr35Media.sectorsPerTrack(48));
        assertEquals(8, Apple2Gcr35Media.sectorsPerTrack(64));
        assertEquals(8, Apple2Gcr35Media.sectorsPerTrack(79));
    }

    @Test
    void mapsProDosBlocksInTrackHeadSectorOrder() {
        assertEquals(0, Apple2Gcr35Media.blockIndex(0, 0, 0));
        assertEquals(11, Apple2Gcr35Media.blockIndex(0, 0, 11));
        assertEquals(12, Apple2Gcr35Media.blockIndex(0, 1, 0));
        assertEquals(23, Apple2Gcr35Media.blockIndex(0, 1, 11));
        assertEquals(24, Apple2Gcr35Media.blockIndex(1, 0, 0));
        assertEquals(385, Apple2Gcr35Media.blockIndex(16, 0, 1));
        assertEquals(1599, Apple2Gcr35Media.blockIndex(79, 1, 7));

        assertThrows(IllegalArgumentException.class, () -> Apple2Gcr35Media.blockIndex(79, 1, 8));
    }

    @Test
    void buildsTrackWithMacGcrAddressAndDataPrologs() {
        Apple2Gcr35Media media = Apple2Gcr35Media.fromProDosBlockImage(blockImage());

        byte[] stream = media.trackStream(0, 0);

        int address = 64 + 16;
        assertArrayEquals(new byte[]{
                (byte) 0xD5,
                (byte) 0xAA,
                (byte) 0x96,
                (byte) Apple2GcrEncoding.encode6And2(0),
                (byte) Apple2GcrEncoding.encode6And2(0),
                (byte) Apple2GcrEncoding.encode6And2(0),
                (byte) Apple2GcrEncoding.encode6And2(0x22),
                (byte) Apple2GcrEncoding.encode6And2(0x22),
                (byte) 0xDE,
                (byte) 0xAA,
                (byte) 0xFF
        }, slice(stream, address, 11));

        int data = address + 11 + 6;
        assertArrayEquals(new byte[]{
                (byte) 0xD5,
                (byte) 0xAA,
                (byte) 0xAD,
                (byte) Apple2GcrEncoding.encode6And2(0)
        }, slice(stream, data, 4));
    }

    @Test
    void generatedAddressAndDataFieldsUseMatchingSectorNumbers() {
        Apple2Gcr35Media media = Apple2Gcr35Media.fromProDosBlockImage(blockImage());

        for (int track = 0; track < Apple2Gcr35Media.TRACK_COUNT; track++) {
            for (int head = 0; head < Apple2Gcr35Media.HEAD_COUNT; head++) {
                assertTrackFieldsUseMatchingSectorNumbers(media.trackStream(track, head), track, head);
            }
        }
    }

    @Test
    void nextByteAdvancesAndWrapsWithinTrackHeadStream() {
        Apple2Gcr35Media media = Apple2Gcr35Media.fromProDosBlockImage(blockImage());
        byte[] stream = media.trackStream(0, 0);

        for (byte value : stream) {
            assertEquals(Byte.toUnsignedInt(value), media.nextByte(0, 0));
        }
        assertEquals(Byte.toUnsignedInt(stream[0]), media.nextByte(0, 0));
    }

    @Test
    void trackStreamReturnsDefensiveCopyWithoutAffectingMediaReads() {
        Apple2Gcr35Media media = Apple2Gcr35Media.fromProDosBlockImage(blockImage());
        byte[] stream = media.trackStream(0, 0);
        int firstByte = Byte.toUnsignedInt(stream[0]);

        stream[0] ^= (byte) 0xFF;

        assertEquals(firstByte, media.nextByte(0, 0));
    }

    private static Apple2ProDosBlockImage blockImage() {
        byte[] bytes = new byte[Apple2ProDosBlockImage.IMAGE_SIZE_800K];
        for (int block = 0; block < Apple2ProDosBlockImage.BLOCK_COUNT_800K; block++) {
            int offset = block * Apple2ProDosBlockImage.BLOCK_SIZE;
            bytes[offset] = (byte) block;
            bytes[offset + 1] = (byte) (block >>> 8);
        }
        return Apple2ProDosBlockImage.fromProDosOrderedBytes(bytes);
    }

    private static void assertTrackFieldsUseMatchingSectorNumbers(byte[] stream, int track, int head) {
        int sectors = Apple2Gcr35Media.sectorsPerTrack(track);
        boolean[] seenSectors = new boolean[sectors];
        int addressFields = 0;
        int dataFields = 0;
        for (int offset = 0; offset < stream.length - 3; offset++) {
            if (matches(stream, offset, 0xD5, 0xAA, 0xAD)) {
                dataFields++;
                continue;
            }
            if (!matches(stream, offset, 0xD5, 0xAA, 0x96)) {
                continue;
            }

            int trackLow = decode6And2(stream[offset + 3]);
            int sector = decode6And2(stream[offset + 4]);
            int side = decode6And2(stream[offset + 5]);
            int format = decode6And2(stream[offset + 6]);
            int check = decode6And2(stream[offset + 7]);
            int expectedSide = ((track & 0x40) != 0 ? 0x01 : 0x00) | (head != 0 ? 0x20 : 0x00);
            String location = "track=%d head=%d offset=%d".formatted(track, head, offset);

            assertEquals(track & 0x3F, trackLow, location);
            assertTrue(sector < sectors, location);
            assertEquals(expectedSide, side, location);
            assertEquals((track ^ sector ^ side ^ format) & 0x3F, check, location);

            int nextAddressField = findProlog(stream, offset + 1, 0xD5, 0xAA, 0x96);
            int dataField = findProlog(stream, offset + 1, 0xD5, 0xAA, 0xAD);
            assertTrue(dataField >= 0, location);
            assertTrue(nextAddressField < 0 || dataField < nextAddressField, location);
            assertEquals(sector, decode6And2(stream[dataField + 3]), location);

            seenSectors[sector] = true;
            addressFields++;
        }
        assertEquals(sectors, addressFields, "address fields track=%d head=%d".formatted(track, head));
        assertEquals(sectors, dataFields, "data fields track=%d head=%d".formatted(track, head));
        for (int sector = 0; sector < sectors; sector++) {
            assertTrue(seenSectors[sector], "track=%d head=%d sector=%d".formatted(track, head, sector));
        }
    }

    private static int findProlog(byte[] stream, int start, int first, int second, int third) {
        for (int offset = start; offset < stream.length - 2; offset++) {
            if (matches(stream, offset, first, second, third)) {
                return offset;
            }
        }
        return -1;
    }

    private static boolean matches(byte[] stream, int offset, int first, int second, int third) {
        return Byte.toUnsignedInt(stream[offset]) == first
                && Byte.toUnsignedInt(stream[offset + 1]) == second
                && Byte.toUnsignedInt(stream[offset + 2]) == third;
    }

    private static int decode6And2(byte encoded) {
        int value = Byte.toUnsignedInt(encoded);
        for (int index = 0; index < Apple2GcrEncoding.GCR_6_AND_2.length; index++) {
            if (Apple2GcrEncoding.GCR_6_AND_2[index] == value) {
                return index;
            }
        }
        throw new AssertionError("Invalid Apple 3.5 GCR byte: 0x%02X".formatted(value));
    }

    private static byte[] slice(byte[] bytes, int offset, int length) {
        byte[] slice = new byte[length];
        System.arraycopy(bytes, offset, slice, 0, length);
        return slice;
    }
}
