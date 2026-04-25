package dev.z8emu.machine.apple2.disk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Apple2DiskImageTest {
    @Test
    void loadsDosOrdered140kImages(@TempDir Path tempDir) throws IOException {
        byte[] imageBytes = patternedDosImage();
        imageBytes[((17 * 16) * 256) + 6] = 0x7B;
        Path imagePath = tempDir.resolve("disk.do");
        Files.write(imagePath, imageBytes);

        Apple2DosDiskImage image = Apple2DosDiskImageLoader.load(imagePath);

        assertEquals(0x7B, image.volumeNumber());
        assertEquals(Apple2DosDiskImage.IMAGE_SIZE, imageBytes.length);
        assertEquals(0x02, image.readLogicalSector(0, 2)[0] & 0xFF);
        assertEquals(0x07, image.readPhysicalSector(0, 1)[0] & 0xFF);
        assertEquals(0x0D, Apple2DosDiskImage.physicalSectorForLogical(1));
        assertEquals(0x01, Apple2DosDiskImage.logicalSectorForPhysical(0x0D));
    }

    @Test
    void rejectsImagesThatAreNotDosOrderSize() {
        assertThrows(IllegalArgumentException.class, () -> Apple2DosDiskImage.fromDosOrderedBytes(new byte[1024]));
    }

    @Test
    void nibblizedTrackDecodesBackToOriginalSectorData() {
        Apple2DosDiskImage image = Apple2DosDiskImage.fromDosOrderedBytes(patternedDosImage());
        byte[] track = Apple2DiskNibblizer.buildTrack(image, 0);
        byte[] decodedSector = decodeFirstDataField(track);

        assertEquals(Apple2DiskNibblizer.TRACK_BYTES, track.length);
        assertArrayEquals(image.readLogicalSector(0, 0), decodedSector);
    }

    private static byte[] decodeFirstDataField(byte[] track) {
        int offset = find(track, 0xD5, 0xAA, 0xAD) + 3;
        int[] decodeTable = decodeTable();
        int[] twos = new int[86];
        byte[] sector = new byte[256];
        int accumulator = 0;
        for (int y = 86; y > 0; y--) {
            accumulator ^= decodeTable[track[offset++] & 0xFF];
            twos[y - 1] = accumulator;
        }
        for (int y = 0; y < 256; y++) {
            accumulator ^= decodeTable[track[offset++] & 0xFF];
            sector[y] = (byte) accumulator;
        }
        accumulator ^= decodeTable[track[offset] & 0xFF];
        assertEquals(0, accumulator);

        int y = 0;
        int x = 86;
        while (y < 256) {
            x--;
            if (x < 0) {
                x = 85;
            }
            int value = sector[y] & 0xFF;
            int firstCarry = twos[x] & 0x01;
            twos[x] >>>= 1;
            value = ((value << 1) & 0xFE) | firstCarry;
            int secondCarry = twos[x] & 0x01;
            twos[x] >>>= 1;
            value = ((value << 1) & 0xFE) | secondCarry;
            sector[y] = (byte) value;
            y++;
        }
        return sector;
    }

    private static int find(byte[] bytes, int first, int second, int third) {
        for (int i = 0; i <= bytes.length - 3; i++) {
            if ((bytes[i] & 0xFF) == first
                    && (bytes[i + 1] & 0xFF) == second
                    && (bytes[i + 2] & 0xFF) == third) {
                return i;
            }
        }
        throw new AssertionError("prologue not found");
    }

    private static int[] decodeTable() {
        int[] gcr = {
                0x96, 0x97, 0x9A, 0x9B, 0x9D, 0x9E, 0x9F, 0xA6,
                0xA7, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF, 0xB2, 0xB3,
                0xB4, 0xB5, 0xB6, 0xB7, 0xB9, 0xBA, 0xBB, 0xBC,
                0xBD, 0xBE, 0xBF, 0xCB, 0xCD, 0xCE, 0xCF, 0xD3,
                0xD6, 0xD7, 0xD9, 0xDA, 0xDB, 0xDC, 0xDD, 0xDE,
                0xDF, 0xE5, 0xE6, 0xE7, 0xE9, 0xEA, 0xEB, 0xEC,
                0xED, 0xEE, 0xEF, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6,
                0xF7, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF
        };
        int[] table = new int[256];
        for (int value = 0; value < gcr.length; value++) {
            table[gcr[value]] = value;
        }
        return table;
    }

    private static byte[] patternedDosImage() {
        byte[] image = new byte[Apple2DosDiskImage.IMAGE_SIZE];
        for (int track = 0; track < Apple2DosDiskImage.TRACK_COUNT; track++) {
            for (int sector = 0; sector < Apple2DosDiskImage.SECTORS_PER_TRACK; sector++) {
                int offset = ((track * Apple2DosDiskImage.SECTORS_PER_TRACK) + sector)
                        * Apple2DosDiskImage.SECTOR_SIZE;
                for (int i = 0; i < Apple2DosDiskImage.SECTOR_SIZE; i++) {
                    image[offset + i] = (byte) ((track * 17 + sector + i) & 0xFF);
                }
            }
        }
        return image;
    }
}
