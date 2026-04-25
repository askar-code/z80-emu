package dev.z8emu.machine.apple2.disk;

import java.util.Arrays;
import java.util.Objects;

public final class Apple2DosDiskImage {
    public static final int TRACK_COUNT = 35;
    public static final int SECTORS_PER_TRACK = 16;
    public static final int SECTOR_SIZE = 256;
    public static final int IMAGE_SIZE = TRACK_COUNT * SECTORS_PER_TRACK * SECTOR_SIZE;

    private static final int VTOC_TRACK = 17;
    private static final int VTOC_SECTOR = 0;
    private static final int VTOC_VOLUME_OFFSET = 6;

    private static final int[] DOS33_LOGICAL_TO_PHYSICAL = {
            0x00, 0x0D, 0x0B, 0x09,
            0x07, 0x05, 0x03, 0x01,
            0x0E, 0x0C, 0x0A, 0x08,
            0x06, 0x04, 0x02, 0x0F
    };
    private static final int[] DOS33_PHYSICAL_TO_LOGICAL = invert(DOS33_LOGICAL_TO_PHYSICAL);

    private final byte[] dosOrderedBytes;
    private final int volumeNumber;

    private Apple2DosDiskImage(byte[] dosOrderedBytes) {
        this.dosOrderedBytes = Arrays.copyOf(dosOrderedBytes, dosOrderedBytes.length);
        this.volumeNumber = Byte.toUnsignedInt(this.dosOrderedBytes[logicalOffset(VTOC_TRACK, VTOC_SECTOR) + VTOC_VOLUME_OFFSET]);
    }

    public static Apple2DosDiskImage fromDosOrderedBytes(byte[] dosOrderedBytes) {
        Objects.requireNonNull(dosOrderedBytes, "dosOrderedBytes");
        if (dosOrderedBytes.length != IMAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Apple II DOS-order disk image must be exactly %d bytes".formatted(IMAGE_SIZE)
            );
        }
        return new Apple2DosDiskImage(dosOrderedBytes);
    }

    public int volumeNumber() {
        return volumeNumber;
    }

    public byte[] readLogicalSector(int track, int logicalSector) {
        validateTrack(track);
        validateSector(logicalSector);
        int offset = logicalOffset(track, logicalSector);
        return Arrays.copyOfRange(dosOrderedBytes, offset, offset + SECTOR_SIZE);
    }

    public byte[] readPhysicalSector(int track, int physicalSector) {
        validateSector(physicalSector);
        return readLogicalSector(track, DOS33_PHYSICAL_TO_LOGICAL[physicalSector]);
    }

    public static int physicalSectorForLogical(int logicalSector) {
        validateSector(logicalSector);
        return DOS33_LOGICAL_TO_PHYSICAL[logicalSector];
    }

    public static int logicalSectorForPhysical(int physicalSector) {
        validateSector(physicalSector);
        return DOS33_PHYSICAL_TO_LOGICAL[physicalSector];
    }

    private static int logicalOffset(int track, int logicalSector) {
        return ((track * SECTORS_PER_TRACK) + logicalSector) * SECTOR_SIZE;
    }

    private static void validateTrack(int track) {
        if (track < 0 || track >= TRACK_COUNT) {
            throw new IllegalArgumentException("Apple II disk track out of range: " + track);
        }
    }

    private static void validateSector(int sector) {
        if (sector < 0 || sector >= SECTORS_PER_TRACK) {
            throw new IllegalArgumentException("Apple II disk sector out of range: " + sector);
        }
    }

    private static int[] invert(int[] values) {
        int[] inverted = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            inverted[values[i]] = i;
        }
        return inverted;
    }
}
