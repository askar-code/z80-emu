package dev.z8emu.machine.cpc.disk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CpcDskLoader {
    private static final int DISC_INFO_SIZE = 0x100;
    private static final int TRACK_INFO_SIZE = 0x100;
    private static final int SECTOR_INFO_OFFSET = 0x18;
    private static final int SECTOR_INFO_SIZE = 8;
    private static final String STANDARD_SIGNATURE = "MV - CPCEMU Disk-File\r\nDisk-Info\r\n";
    private static final String EXTENDED_SIGNATURE = "EXTENDED CPC DSK File\r\nDisk-Info\r\n";
    private static final String TRACK_SIGNATURE = "Track-Info\r\n";

    private CpcDskLoader() {
    }

    public static CpcDskImage load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return load(input.readAllBytes());
        }
    }

    public static CpcDskImage load(byte[] image) throws IOException {
        if (image.length < DISC_INFO_SIZE) {
            throw new IOException("DSK image is smaller than the disc information block");
        }
        boolean extended = hasAsciiPrefix(image, 0, EXTENDED_SIGNATURE);
        boolean standard = hasAsciiPrefix(image, 0, STANDARD_SIGNATURE);
        if (!standard && !extended) {
            throw new IOException("Unsupported DSK image signature");
        }

        int trackCount = unsigned(image[0x30]);
        int sideCount = unsigned(image[0x31]);
        if (trackCount <= 0) {
            throw new IOException("DSK image has no tracks");
        }
        if (sideCount <= 0 || sideCount > 2) {
            throw new IOException("DSK image side count must be 1 or 2");
        }

        CpcDiskTrack[][] tracks = extended
                ? parseExtendedTracks(image, trackCount, sideCount)
                : parseStandardTracks(image, trackCount, sideCount);
        return new CpcDskImage(trackCount, sideCount, tracks);
    }

    private static CpcDiskTrack[][] parseStandardTracks(byte[] image, int trackCount, int sideCount) throws IOException {
        int trackSize = littleEndian16(image, 0x32);
        if (trackSize < TRACK_INFO_SIZE) {
            throw new IOException("DSK track size is smaller than the track information block");
        }
        int requiredSize = DISC_INFO_SIZE + (trackCount * sideCount * trackSize);
        if (image.length < requiredSize) {
            throw new IOException("DSK image is truncated");
        }

        CpcDiskTrack[][] tracks = new CpcDiskTrack[trackCount][sideCount];
        for (int track = 0; track < trackCount; track++) {
            for (int side = 0; side < sideCount; side++) {
                int trackIndex = (track * sideCount) + side;
                int offset = DISC_INFO_SIZE + (trackIndex * trackSize);
                tracks[track][side] = parseTrack(image, offset, trackSize, false);
            }
        }
        return tracks;
    }

    private static CpcDiskTrack[][] parseExtendedTracks(byte[] image, int trackCount, int sideCount) throws IOException {
        int trackTableLength = trackCount * sideCount;
        if (0x34 + trackTableLength > DISC_INFO_SIZE) {
            throw new IOException("Extended DSK track size table exceeds disc information block");
        }

        CpcDiskTrack[][] tracks = new CpcDiskTrack[trackCount][sideCount];
        int offset = DISC_INFO_SIZE;
        for (int track = 0; track < trackCount; track++) {
            for (int side = 0; side < sideCount; side++) {
                int trackIndex = (track * sideCount) + side;
                int trackSize = unsigned(image[0x34 + trackIndex]) << 8;
                if (trackSize == 0) {
                    tracks[track][side] = null;
                    continue;
                }
                if (trackSize < TRACK_INFO_SIZE) {
                    throw new IOException("Extended DSK track size is smaller than the track information block");
                }
                if (offset + trackSize > image.length) {
                    throw new IOException("Extended DSK image is truncated");
                }
                tracks[track][side] = parseTrack(image, offset, trackSize, true);
                offset += trackSize;
            }
        }
        return tracks;
    }

    private static CpcDiskTrack parseTrack(byte[] image, int offset, int trackSize, boolean extended) throws IOException {
        if (!hasAsciiPrefix(image, offset, TRACK_SIGNATURE)) {
            throw new IOException("Missing DSK track information block at offset " + offset);
        }

        int trackNumber = unsigned(image[offset + 0x10]);
        int sideNumber = unsigned(image[offset + 0x11]);
        int sectorSizeCode = unsigned(image[offset + 0x14]);
        int sectorCount = unsigned(image[offset + 0x15]);
        int gap3Length = unsigned(image[offset + 0x16]);
        int fillerByte = unsigned(image[offset + 0x17]);
        int allocatedSectorSize = sectorSize(sectorSizeCode);
        int sectorDataOffset = offset + TRACK_INFO_SIZE;
        int sectorDataLimit = offset + trackSize;

        if (SECTOR_INFO_OFFSET + (sectorCount * SECTOR_INFO_SIZE) > TRACK_INFO_SIZE) {
            throw new IOException("DSK track sector information list is too large");
        }
        if (sectorDataOffset + ((long) sectorCount * allocatedSectorSize) > sectorDataLimit) {
            throw new IOException("DSK track sector data exceeds track block size");
        }

        List<CpcDiskSector> sectors = new ArrayList<>(sectorCount);
        int nextExtendedSectorOffset = sectorDataOffset;
        for (int index = 0; index < sectorCount; index++) {
            int infoOffset = offset + SECTOR_INFO_OFFSET + (index * SECTOR_INFO_SIZE);
            int sectorTrack = unsigned(image[infoOffset]);
            int sectorSide = unsigned(image[infoOffset + 1]);
            int sectorId = unsigned(image[infoOffset + 2]);
            int sizeCode = unsigned(image[infoOffset + 3]);
            int status1 = unsigned(image[infoOffset + 4]);
            int status2 = unsigned(image[infoOffset + 5]);
            int dataOffset;
            int dataLength;
            if (extended) {
                dataOffset = nextExtendedSectorOffset;
                dataLength = littleEndian16(image, infoOffset + 6);
                nextExtendedSectorOffset += dataLength;
            } else {
                dataOffset = sectorDataOffset + (index * allocatedSectorSize);
                dataLength = Math.min(sectorSize(sizeCode), allocatedSectorSize);
            }
            if (dataOffset + dataLength > sectorDataLimit) {
                throw new IOException("DSK sector data exceeds track block size");
            }
            byte[] data = Arrays.copyOfRange(image, dataOffset, dataOffset + dataLength);
            sectors.add(new CpcDiskSector(sectorTrack, sectorSide, sectorId, sizeCode, status1, status2, data));
        }

        return new CpcDiskTrack(trackNumber, sideNumber, sectorSizeCode, gap3Length, fillerByte, sectors);
    }

    private static boolean hasAsciiPrefix(byte[] bytes, int offset, String expected) {
        byte[] signature = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset + signature.length > bytes.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static int sectorSize(int sizeCode) throws IOException {
        if (sizeCode < 0) {
            throw new IOException("Unsupported DSK sector size code: " + sizeCode);
        }
        return 128 << (sizeCode & 0x07);
    }

    private static int littleEndian16(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8);
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }
}
