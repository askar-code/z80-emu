package dev.z8emu.machine.apple2.disk;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Apple2WozTestImages {
    private Apple2WozTestImages() {
    }

    public static byte[] wozImage(int diskType, boolean writeProtected, String creator, byte[] trackBits, String metadata) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[]{'W', 'O', 'Z', '1', (byte) 0xFF, 0x0A, 0x0D, 0x0A, 0, 0, 0, 0});

        byte[] info = new byte[60];
        info[0] = 1;
        info[1] = (byte) diskType;
        info[2] = (byte) (writeProtected ? 1 : 0);
        byte[] creatorBytes = creator.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(creatorBytes, 0, info, 5, Math.min(creatorBytes.length, 32));
        writeChunk(output, "INFO", info);

        byte[] tmap = new byte[160];
        Arrays.fill(tmap, (byte) Apple2WozDiskImage.EMPTY_TRACK_INDEX);
        tmap[0] = 0;
        writeChunk(output, "TMAP", tmap);

        byte[] trks = new byte[6656];
        System.arraycopy(trackBits, 0, trks, 0, trackBits.length);
        putWord(trks, 6646, trackBits.length);
        putWord(trks, 6648, trackBits.length * 8);
        writeChunk(output, "TRKS", trks);

        if (!metadata.isEmpty()) {
            writeChunk(output, "META", metadata.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream output, String id, byte[] data) {
        output.writeBytes(id.getBytes(StandardCharsets.US_ASCII));
        putInt(output, data.length);
        output.writeBytes(data);
    }

    private static void putWord(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void putInt(ByteArrayOutputStream output, int value) {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 24) & 0xFF);
    }
}
