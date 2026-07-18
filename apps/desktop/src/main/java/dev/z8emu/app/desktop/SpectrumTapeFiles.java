package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import dev.z8emu.machine.spectrum48k.tape.TapeLoaders;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

final class SpectrumTapeFiles {
    private SpectrumTapeFiles() {
    }

    static LoadedTape load(Path source) throws IOException {
        Path normalized = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        return new LoadedTape(normalized, TapeLoaders.load(normalized));
    }

    static String displayName(String sourceLabel) {
        if (sourceLabel == null || sourceLabel.isBlank()) {
            return "inserted";
        }
        try {
            Path fileName = Path.of(sourceLabel).getFileName();
            return fileName == null ? sourceLabel : fileName.toString();
        } catch (RuntimeException invalidPath) {
            return sourceLabel;
        }
    }

    record LoadedTape(Path source, TapeFile tapeFile) {
        LoadedTape {
            source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
            tapeFile = Objects.requireNonNull(tapeFile, "tapeFile");
        }
    }
}
