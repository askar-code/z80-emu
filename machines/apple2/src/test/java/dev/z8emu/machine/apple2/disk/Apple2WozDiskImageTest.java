package dev.z8emu.machine.apple2.disk;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Apple2WozDiskImageTest {
    @Test
    void readsWoz1InfoTrackMapAndTrackBits() {
        Apple2WozDiskImage image = Apple2WozDiskImage.fromWoz1Bytes(wozImage(
                1,
                true,
                "Synthetic WOZ",
                new byte[]{(byte) 0xD5, (byte) 0xAA, (byte) 0x96, (byte) 0xFF},
                "title\tSynthetic\n"
        ));

        assertTrue(image.writeProtected());
        assertEquals("Synthetic WOZ", image.creator());
        assertEquals("title\tSynthetic\n", image.metadata());
        assertEquals(0, image.trackIndex(0));
        assertEquals(Apple2WozDiskImage.EMPTY_TRACK_INDEX, image.trackIndex(1));

        byte[] track = image.trackStream(0);
        assertArrayEquals(
                new byte[]{(byte) 0xD5, (byte) 0xAA, (byte) 0x96, (byte) 0xFF},
                Arrays.copyOf(track, 4)
        );
        assertEquals((byte) 0xFF, image.trackStream(1)[0]);
    }

    @Test
    void rejectsUnsupportedDiskType() {
        byte[] image = wozImage(
                2,
                false,
                "Synthetic WOZ",
                new byte[]{(byte) 0xFF},
                ""
        );

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> Apple2WozDiskImage.fromWoz1Bytes(image)
        );
        assertTrue(failure.getMessage().contains("5.25-inch"));
    }

    @Test
    void carriesLssStateAcrossCircularTrackBoundary() {
        Apple2WozDiskImage image = Apple2WozDiskImage.fromWoz1Bytes(wozImage(
                1,
                true,
                "Synthetic WOZ",
                new byte[]{0x00, 0x05},
                ""
        ));

        assertEquals(0xA0, Byte.toUnsignedInt(image.trackStream(0)[0]));
    }

    @Test
    void detectsWoz1Header() {
        byte[] image = wozImage(
                1,
                false,
                "Synthetic WOZ",
                new byte[]{(byte) 0xFF},
                ""
        );

        assertTrue(Apple2WozDiskImage.hasWoz1Header(image));
        assertFalse(Apple2WozDiskImage.hasWoz1Header(new byte[]{'W', 'O', 'Z', '2'}));
    }

    static byte[] wozImage(int diskType, boolean writeProtected, String creator, byte[] trackBits, String metadata) {
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
