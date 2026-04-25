package dev.z8emu.machine.apple2.disk;

import java.util.ArrayList;
import java.util.List;

final class Apple2DiskNibblizer {
    static final int TRACK_BYTES = 6400;

    private static final int INITIAL_SYNC_BYTES = 64;
    private static final int ADDRESS_TO_DATA_SYNC_BYTES = 8;
    private static final int DATA_TO_ADDRESS_SYNC_BYTES = 25;

    private static final int[] GCR_6_AND_2 = {
            0x96, 0x97, 0x9A, 0x9B, 0x9D, 0x9E, 0x9F, 0xA6,
            0xA7, 0xAB, 0xAC, 0xAD, 0xAE, 0xAF, 0xB2, 0xB3,
            0xB4, 0xB5, 0xB6, 0xB7, 0xB9, 0xBA, 0xBB, 0xBC,
            0xBD, 0xBE, 0xBF, 0xCB, 0xCD, 0xCE, 0xCF, 0xD3,
            0xD6, 0xD7, 0xD9, 0xDA, 0xDB, 0xDC, 0xDD, 0xDE,
            0xDF, 0xE5, 0xE6, 0xE7, 0xE9, 0xEA, 0xEB, 0xEC,
            0xED, 0xEE, 0xEF, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6,
            0xF7, 0xF9, 0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF
    };

    private Apple2DiskNibblizer() {
    }

    static byte[] buildTrack(Apple2DosDiskImage image, int track) {
        List<Integer> bytes = new ArrayList<>(TRACK_BYTES);
        appendSync(bytes, INITIAL_SYNC_BYTES);
        int volume = image.volumeNumber();
        for (int physicalSector = 0; physicalSector < Apple2DosDiskImage.SECTORS_PER_TRACK; physicalSector++) {
            appendAddressField(bytes, volume, track, physicalSector);
            appendSync(bytes, ADDRESS_TO_DATA_SYNC_BYTES);
            appendDataField(bytes, image.readPhysicalSector(track, physicalSector));
            appendSync(bytes, DATA_TO_ADDRESS_SYNC_BYTES);
        }

        byte[] trackBytes = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            trackBytes[i] = (byte) (bytes.get(i) & 0xFF);
        }
        return trackBytes;
    }

    private static void appendAddressField(List<Integer> bytes, int volume, int track, int sector) {
        bytes.add(0xD5);
        bytes.add(0xAA);
        bytes.add(0x96);
        append4And4(bytes, volume);
        append4And4(bytes, track);
        append4And4(bytes, sector);
        append4And4(bytes, volume ^ track ^ sector);
        bytes.add(0xDE);
        bytes.add(0xAA);
        bytes.add(0xEB);
    }

    private static void appendDataField(List<Integer> bytes, byte[] sector) {
        bytes.add(0xD5);
        bytes.add(0xAA);
        bytes.add(0xAD);
        append6And2(bytes, sector);
        bytes.add(0xDE);
        bytes.add(0xAA);
        bytes.add(0xEB);
    }

    private static void append4And4(List<Integer> bytes, int value) {
        int normalized = value & 0xFF;
        bytes.add(((normalized >>> 1) | 0xAA) & 0xFF);
        bytes.add((normalized | 0xAA) & 0xFF);
    }

    private static void append6And2(List<Integer> bytes, byte[] sector) {
        if (sector.length != Apple2DosDiskImage.SECTOR_SIZE) {
            throw new IllegalArgumentException("Apple II sector must be exactly 256 bytes");
        }

        int[] twos = new int[86];
        int[] sixes = new int[Apple2DosDiskImage.SECTOR_SIZE];
        for (int i = 0; i < sector.length; i++) {
            int value = Byte.toUnsignedInt(sector[i]);
            sixes[i] = (value >>> 2) & 0x3F;

            int twosIndex = 85 - (i % 86);
            int bitOffset = (i / 86) * 2;
            if ((value & 0x02) != 0) {
                twos[twosIndex] |= 1 << bitOffset;
            }
            if ((value & 0x01) != 0) {
                twos[twosIndex] |= 1 << (bitOffset + 1);
            }
        }

        int previous = 0;
        for (int y = twos.length; y > 0; y--) {
            int value = twos[y - 1] & 0x3F;
            bytes.add(GCR_6_AND_2[previous ^ value]);
            previous = value;
        }
        for (int value : sixes) {
            int normalized = value & 0x3F;
            bytes.add(GCR_6_AND_2[previous ^ normalized]);
            previous = normalized;
        }
        bytes.add(GCR_6_AND_2[previous]);
    }

    private static void appendSync(List<Integer> bytes, int count) {
        for (int i = 0; i < count; i++) {
            bytes.add(0xFF);
        }
    }
}
