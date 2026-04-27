package dev.z8emu.machine.apple2.disk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Apple2Gcr35Media {
    public static final int TRACK_COUNT = 80;
    public static final int HEAD_COUNT = 2;
    public static final int MAX_SECTORS_PER_TRACK = 12;
    public static final int SECTOR_SIZE = Apple2ProDosBlockImage.BLOCK_SIZE;

    private static final int INITIAL_SYNC_BYTES = 64;
    private static final int ADDRESS_SYNC_BYTES = 16;
    private static final int ADDRESS_TO_DATA_SYNC_BYTES = 6;
    private static final int DATA_TO_ADDRESS_SYNC_BYTES = 8;
    private static final int FORMAT_DOUBLE_SIDED = 0x22;

    private final Apple2ProDosBlockImage image;
    private final byte[][][] trackStreams = new byte[TRACK_COUNT][HEAD_COUNT][];
    private final int[][] trackPositions = new int[TRACK_COUNT][HEAD_COUNT];

    private Apple2Gcr35Media(Apple2ProDosBlockImage image) {
        this.image = Objects.requireNonNull(image, "image");
    }

    public static Apple2Gcr35Media fromProDosBlockImage(Apple2ProDosBlockImage image) {
        return new Apple2Gcr35Media(image);
    }

    public void reset() {
        for (int track = 0; track < TRACK_COUNT; track++) {
            Arrays.fill(trackPositions[track], 0);
        }
    }

    public int nextByte(int track, int head) {
        byte[] stream = cachedTrackStream(track, head);
        int position = trackPositions[track][head];
        int value = Byte.toUnsignedInt(stream[position]);
        trackPositions[track][head] = (position + 1) % stream.length;
        return value;
    }

    public int currentPosition(int track, int head) {
        validateTrack(track);
        validateHead(head);
        return trackPositions[track][head];
    }

    public byte[] trackStream(int track, int head) {
        byte[] stream = cachedTrackStream(track, head);
        return Arrays.copyOf(stream, stream.length);
    }

    private byte[] cachedTrackStream(int track, int head) {
        validateTrack(track);
        validateHead(head);
        byte[] stream = trackStreams[track][head];
        if (stream == null) {
            stream = buildTrack(track, head);
            trackStreams[track][head] = stream;
        }
        return stream;
    }

    public static int sectorsPerTrack(int track) {
        validateTrack(track);
        return MAX_SECTORS_PER_TRACK - (track / 16);
    }

    public static int blockIndex(int track, int head, int logicalSector) {
        validateTrack(track);
        validateHead(head);
        int sectors = sectorsPerTrack(track);
        if (logicalSector < 0 || logicalSector >= sectors) {
            throw new IllegalArgumentException("Apple 3.5 sector out of range: " + logicalSector);
        }
        int block = 0;
        for (int previousTrack = 0; previousTrack < track; previousTrack++) {
            block += sectorsPerTrack(previousTrack) * HEAD_COUNT;
        }
        return block + (head * sectors) + logicalSector;
    }

    private byte[] buildTrack(int track, int head) {
        int sectors = sectorsPerTrack(track);
        Sector[] physicalSectors = physicalSectors(track, head, sectors);
        List<Integer> bytes = new ArrayList<>(INITIAL_SYNC_BYTES + (sectors * 800));
        appendSync(bytes, INITIAL_SYNC_BYTES);
        for (Sector sector : physicalSectors) {
            appendSync(bytes, ADDRESS_SYNC_BYTES);
            appendAddressField(bytes, track, head, sector.logicalSector());
            appendSync(bytes, ADDRESS_TO_DATA_SYNC_BYTES);
            appendDataField(bytes, sector.logicalSector(), sector.data());
            appendSync(bytes, DATA_TO_ADDRESS_SYNC_BYTES);
        }
        byte[] stream = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            stream[i] = (byte) (bytes.get(i) & 0xFF);
        }
        return stream;
    }

    private Sector[] physicalSectors(int track, int head, int sectors) {
        Sector[] physicalSectors = new Sector[sectors];
        int physicalIndex = 0;
        for (int logicalSector = 0; logicalSector < sectors; logicalSector++) {
            physicalSectors[physicalIndex] = new Sector(
                    logicalSector,
                    image.readBlock(blockIndex(track, head, logicalSector))
            );
            physicalIndex = (physicalIndex + 2) % sectors;
            if (physicalIndex == 0) {
                physicalIndex++;
            }
        }
        return physicalSectors;
    }

    private static void appendAddressField(List<Integer> bytes, int track, int head, int sector) {
        int side = ((track & 0x40) != 0 ? 0x01 : 0x00) | (head != 0 ? 0x20 : 0x00);
        int check = track ^ sector ^ side ^ FORMAT_DOUBLE_SIDED;
        bytes.add(0xD5);
        bytes.add(0xAA);
        bytes.add(0x96);
        bytes.add(Apple2GcrEncoding.encode6And2(track));
        bytes.add(Apple2GcrEncoding.encode6And2(sector));
        bytes.add(Apple2GcrEncoding.encode6And2(side));
        bytes.add(Apple2GcrEncoding.encode6And2(FORMAT_DOUBLE_SIDED));
        bytes.add(Apple2GcrEncoding.encode6And2(check));
        bytes.add(0xDE);
        bytes.add(0xAA);
        bytes.add(0xFF);
    }

    private static void appendDataField(List<Integer> bytes, int sector, byte[] data) {
        if (data.length != SECTOR_SIZE) {
            throw new IllegalArgumentException("Apple 3.5 sector must be exactly 512 bytes");
        }
        byte[] payload = new byte[12 + SECTOR_SIZE];
        System.arraycopy(data, 0, payload, 12, data.length);

        bytes.add(0xD5);
        bytes.add(0xAA);
        bytes.add(0xAD);
        bytes.add(Apple2GcrEncoding.encode6And2(sector));

        int checksumA = 0;
        int checksumB = 0;
        int checksumC = 0;
        for (int i = 0; i < 175; i++) {
            int valueA = Byte.toUnsignedInt(payload[3 * i]);
            int valueB = Byte.toUnsignedInt(payload[(3 * i) + 1]);
            int valueC = i == 174 ? 0 : Byte.toUnsignedInt(payload[(3 * i) + 2]);

            checksumC = ((checksumC << 1) | (checksumC >>> 7)) & 0xFF;
            int sumA = checksumA + valueA + (checksumC & 0x01);
            checksumA = sumA & 0xFF;
            valueA ^= checksumC;
            int sumB = checksumB + valueB + (sumA >>> 8);
            checksumB = sumB & 0xFF;
            valueB ^= checksumA;
            if (i != 174) {
                checksumC = (checksumC + valueC + (sumB >>> 8)) & 0xFF;
            }
            valueC ^= checksumB;

            appendEncodedTriple(bytes, valueA, valueB, valueC, i == 174 ? 3 : 4);
        }
        appendEncodedTriple(bytes, checksumA, checksumB, checksumC, 4);
        bytes.add(0xDE);
        bytes.add(0xAA);
        bytes.add(0xFF);
    }

    private static void appendEncodedTriple(List<Integer> bytes, int a, int b, int c, int byteCount) {
        int encoded = Apple2GcrEncoding.encodeMacTriple(a, b, c);
        for (int shift = 24; shift >= 32 - (byteCount * 8); shift -= 8) {
            bytes.add((encoded >>> shift) & 0xFF);
        }
    }

    private static void appendSync(List<Integer> bytes, int count) {
        for (int i = 0; i < count; i++) {
            bytes.add(0xFF);
        }
    }

    private static void validateTrack(int track) {
        if (track < 0 || track >= TRACK_COUNT) {
            throw new IllegalArgumentException("Apple 3.5 track out of range: " + track);
        }
    }

    private static void validateHead(int head) {
        if (head < 0 || head >= HEAD_COUNT) {
            throw new IllegalArgumentException("Apple 3.5 head out of range: " + head);
        }
    }

    private record Sector(int logicalSector, byte[] data) {
    }
}
