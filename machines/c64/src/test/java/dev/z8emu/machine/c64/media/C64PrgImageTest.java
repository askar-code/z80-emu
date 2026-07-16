package dev.z8emu.machine.c64.media;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class C64PrgImageTest {
    private static final byte[] HELLO_PRG = {
            0x01, 0x08, 0x0E, 0x08, 0x0A, 0x00, (byte) 0x99, 0x22,
            0x48, 0x45, 0x4C, 0x4C, 0x4F, 0x22, 0x00, 0x00, 0x00
    };

    @Test
    void parsesAndLoadsPrgImage(@TempDir Path tempDir) throws Exception {
        C64PrgImage parsed = C64PrgImage.parse(HELLO_PRG);
        Path path = tempDir.resolve("hello.prg");
        Files.write(path, HELLO_PRG);
        C64PrgImage loaded = C64PrgImage.load(path);

        assertEquals(0x0801, parsed.loadAddress());
        assertEquals(15, parsed.payload().length);
        assertEquals(0x0810, parsed.endAddress());
        assertEquals(parsed.loadAddress(), loaded.loadAddress());
        assertArrayEquals(parsed.payload(), loaded.payload());
    }

    @Test
    void constructorDefensivelyCopiesPayload() {
        byte[] payload = {0x01};
        C64PrgImage image = new C64PrgImage(0x2000, payload);

        payload[0] = 0x02;

        assertArrayEquals(new byte[]{0x01}, image.payload());
    }

    @Test
    void rejectsTooShortFile() {
        assertThrows(IllegalArgumentException.class, () -> C64PrgImage.parse(new byte[2]));
    }

    @Test
    void rejectsPayloadOverflow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> C64PrgImage.parse(new byte[]{(byte) 0xFF, (byte) 0xFF, 0x01, 0x02})
        );
    }
}
