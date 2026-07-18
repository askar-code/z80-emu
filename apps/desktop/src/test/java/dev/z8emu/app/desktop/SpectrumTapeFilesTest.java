package dev.z8emu.app.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpectrumTapeFilesTest {
    @Test
    void parsesTapBeforeItIsSubmittedToTheRuntimeController(@TempDir Path tempDir) throws Exception {
        Path tape = tempDir.resolve("side-b.tap");
        Files.write(tape, new byte[]{1, 0, (byte) 0xFF});

        SpectrumTapeFiles.LoadedTape loaded = SpectrumTapeFiles.load(tape);

        assertEquals(tape.toAbsolutePath(), loaded.source());
        assertEquals(1, loaded.tapeFile().blocks().size());
    }

    @Test
    void malformedReplacementFailsBeforeAControllerCanReceiveIt(@TempDir Path tempDir) throws Exception {
        Path tape = tempDir.resolve("broken.tap");
        Files.write(tape, new byte[]{2, 0, (byte) 0xFF});

        assertThrows(Exception.class, () -> SpectrumTapeFiles.load(tape));
    }
}
