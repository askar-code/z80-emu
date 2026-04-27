package dev.z8emu.machine.apple2.disk;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Apple2WozDiskImage {
    public static final int TRACK_COUNT = 35;
    public static final int QUARTER_TRACK_COUNT = 160;
    public static final int EMPTY_TRACK_INDEX = 0xFF;

    private static final byte[] WOZ1_HEADER = {
            'W', 'O', 'Z', '1',
            (byte) 0xFF, 0x0A, 0x0D, 0x0A
    };
    private static final int INFO_SIZE = 60;
    private static final int INFO_DISK_TYPE_5_25 = 1;
    private static final int TMAP_SIZE = QUARTER_TRACK_COUNT;
    private static final int WOZ1_TRACK_ENTRY_SIZE = 6656;
    private static final int WOZ1_TRACK_BITS_SIZE = 6646;
    private static final int EMPTY_TRACK_BYTES = Apple2DiskNibblizer.TRACK_BYTES;

    private final byte[] tmap;
    private final byte[] trks;
    private final byte[][] trackStreams = new byte[TRACK_COUNT][];
    private final boolean writeProtected;
    private final String creator;
    private final String metadata;

    private Apple2WozDiskImage(byte[] tmap, byte[] trks, boolean writeProtected, String creator, String metadata) {
        this.tmap = Arrays.copyOf(tmap, tmap.length);
        this.trks = Arrays.copyOf(trks, trks.length);
        this.writeProtected = writeProtected;
        this.creator = creator;
        this.metadata = metadata;
    }

    public static Apple2WozDiskImage fromWoz1Bytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (!hasWoz1Header(bytes)) {
            throw new IllegalArgumentException("Apple II WOZ image must start with a WOZ1 header");
        }

        byte[] info = null;
        byte[] tmap = null;
        byte[] trks = null;
        String metadata = "";
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String id = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            int size = littleEndianInt(bytes, offset + 4);
            int dataOffset = offset + 8;
            int nextOffset = dataOffset + size;
            if (size < 0 || nextOffset < dataOffset || nextOffset > bytes.length) {
                throw new IllegalArgumentException("WOZ chunk is outside image bounds: " + id);
            }
            switch (id) {
                case "INFO" -> info = Arrays.copyOfRange(bytes, dataOffset, nextOffset);
                case "TMAP" -> tmap = Arrays.copyOfRange(bytes, dataOffset, nextOffset);
                case "TRKS" -> trks = Arrays.copyOfRange(bytes, dataOffset, nextOffset);
                case "META" -> metadata = new String(bytes, dataOffset, size, StandardCharsets.UTF_8);
                default -> {
                    // Unknown chunks are forward-compatible and can be ignored.
                }
            }
            offset = nextOffset;
        }

        if (info == null || info.length != INFO_SIZE) {
            throw new IllegalArgumentException("WOZ1 image is missing a 60-byte INFO chunk");
        }
        if (Byte.toUnsignedInt(info[1]) != INFO_DISK_TYPE_5_25) {
            throw new IllegalArgumentException("Only WOZ1 5.25-inch Apple II images are supported");
        }
        if (tmap == null || tmap.length != TMAP_SIZE) {
            throw new IllegalArgumentException("WOZ1 image is missing a 160-byte TMAP chunk");
        }
        if (trks == null || trks.length == 0 || trks.length % WOZ1_TRACK_ENTRY_SIZE != 0) {
            throw new IllegalArgumentException("WOZ1 image TRKS chunk must contain fixed 6656-byte track entries");
        }

        String creator = nullPaddedAscii(info, 5, 32);
        return new Apple2WozDiskImage(tmap, trks, info[2] != 0, creator, metadata);
    }

    public static boolean hasWoz1Header(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return bytes.length >= 12 && startsWith(bytes, WOZ1_HEADER);
    }

    public boolean writeProtected() {
        return writeProtected;
    }

    public String creator() {
        return creator;
    }

    public String metadata() {
        return metadata;
    }

    public int trackIndex(int track) {
        validateTrack(track);
        return Byte.toUnsignedInt(tmap[track * 4]);
    }

    public byte[] trackStream(int track) {
        validateTrack(track);
        byte[] stream = trackStreams[track];
        if (stream == null) {
            stream = buildTrackStream(track);
            trackStreams[track] = stream;
        }
        return Arrays.copyOf(stream, stream.length);
    }

    private byte[] buildTrackStream(int track) {
        int trackIndex = trackIndex(track);
        if (trackIndex == EMPTY_TRACK_INDEX) {
            return emptyTrack();
        }
        int trackOffset = trackIndex * WOZ1_TRACK_ENTRY_SIZE;
        if (trackOffset + WOZ1_TRACK_ENTRY_SIZE > trks.length) {
            throw new IllegalArgumentException("WOZ TMAP references missing TRKS entry: " + trackIndex);
        }

        int bytesUsed = littleEndianWord(trks, trackOffset + WOZ1_TRACK_BITS_SIZE);
        int bitCount = littleEndianWord(trks, trackOffset + WOZ1_TRACK_BITS_SIZE + 2);
        if (bytesUsed < 0 || bytesUsed > WOZ1_TRACK_BITS_SIZE) {
            throw new IllegalArgumentException("WOZ track bytes-used field is out of range: " + bytesUsed);
        }
        int availableBits = bytesUsed * 8;
        int bitsToRead = bitCount == 0 ? availableBits : Math.min(bitCount, availableBits);
        List<Integer> nibbles = new ArrayList<>(bytesUsed);
        int shiftRegister = 0;
        int bitsSinceLatch = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int bitIndex = 0; bitIndex < bitsToRead; bitIndex++) {
                int sourceByte = Byte.toUnsignedInt(trks[trackOffset + (bitIndex >>> 3)]);
                int bit = (sourceByte >>> (7 - (bitIndex & 0x07))) & 0x01;
                shiftRegister = ((shiftRegister << 1) | bit) & 0xFF;
                bitsSinceLatch++;
                if (bitsSinceLatch >= 8 && (shiftRegister & 0x80) != 0) {
                    if (pass == 1) {
                        nibbles.add(shiftRegister);
                    }
                    shiftRegister = 0;
                    bitsSinceLatch = 0;
                }
            }
        }
        if (nibbles.isEmpty()) {
            return emptyTrack();
        }

        byte[] stream = new byte[nibbles.size()];
        for (int i = 0; i < nibbles.size(); i++) {
            stream[i] = (byte) (nibbles.get(i) & 0xFF);
        }
        return stream;
    }

    private static byte[] emptyTrack() {
        byte[] stream = new byte[EMPTY_TRACK_BYTES];
        Arrays.fill(stream, (byte) 0xFF);
        return stream;
    }

    private static void validateTrack(int track) {
        if (track < 0 || track >= TRACK_COUNT) {
            throw new IllegalArgumentException("Apple II WOZ track out of range: " + track);
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String nullPaddedAscii(byte[] bytes, int offset, int length) {
        int end = offset + length;
        while (end > offset && (bytes[end - 1] == 0 || bytes[end - 1] == ' ')) {
            end--;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private static int littleEndianWord(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset]) | (Byte.toUnsignedInt(data[offset + 1]) << 8);
    }

    private static int littleEndianInt(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset])
                | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                | (Byte.toUnsignedInt(data[offset + 2]) << 16)
                | (Byte.toUnsignedInt(data[offset + 3]) << 24);
    }
}
