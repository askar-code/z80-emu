package dev.z8emu.machine.apple2.disk;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static dev.z8emu.machine.apple2.disk.Apple2WozTestImages.wozImage;
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

}
