package dev.z8emu.machine.apple2.disk;

import java.io.ByteArrayOutputStream;

final class Apple2DiskNibblizer {
    static final int TRACK_BYTES = 6400;

    private static final int INITIAL_SYNC_BYTES = 64;
    private static final int ADDRESS_TO_DATA_SYNC_BYTES = 8;
    private static final int DATA_TO_ADDRESS_SYNC_BYTES = 25;

    private Apple2DiskNibblizer() {
    }

    static byte[] buildTrack(Apple2DosDiskImage image, int track) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(TRACK_BYTES);
        appendSync(out, INITIAL_SYNC_BYTES);
        int volume = image.volumeNumber();
        for (int physicalSector = 0; physicalSector < Apple2DosDiskImage.SECTORS_PER_TRACK; physicalSector++) {
            appendAddressField(out, volume, track, physicalSector);
            appendSync(out, ADDRESS_TO_DATA_SYNC_BYTES);
            appendDataField(out, image.readPhysicalSector(track, physicalSector));
            appendSync(out, DATA_TO_ADDRESS_SYNC_BYTES);
        }
        return out.toByteArray();
    }

    private static void appendAddressField(ByteArrayOutputStream out, int volume, int track, int sector) {
        out.write(0xD5);
        out.write(0xAA);
        out.write(0x96);
        append4And4(out, volume);
        append4And4(out, track);
        append4And4(out, sector);
        append4And4(out, volume ^ track ^ sector);
        out.write(0xDE);
        out.write(0xAA);
        out.write(0xEB);
    }

    private static void appendDataField(ByteArrayOutputStream out, byte[] sector) {
        out.write(0xD5);
        out.write(0xAA);
        out.write(0xAD);
        append6And2(out, sector);
        out.write(0xDE);
        out.write(0xAA);
        out.write(0xEB);
    }

    private static void append4And4(ByteArrayOutputStream out, int value) {
        int normalized = value & 0xFF;
        out.write(((normalized >>> 1) | 0xAA) & 0xFF);
        out.write((normalized | 0xAA) & 0xFF);
    }

    private static void append6And2(ByteArrayOutputStream out, byte[] sector) {
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
            out.write(Apple2GcrEncoding.encode6And2(previous ^ value));
            previous = value;
        }
        for (int value : sixes) {
            int normalized = value & 0x3F;
            out.write(Apple2GcrEncoding.encode6And2(previous ^ normalized));
            previous = normalized;
        }
        out.write(Apple2GcrEncoding.encode6And2(previous));
    }

    private static void appendSync(ByteArrayOutputStream out, int count) {
        for (int i = 0; i < count; i++) {
            out.write(0xFF);
        }
    }
}
