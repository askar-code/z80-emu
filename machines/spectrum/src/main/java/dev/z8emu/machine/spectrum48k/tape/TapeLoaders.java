package dev.z8emu.machine.spectrum48k.tape;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class TapeLoaders {
    private TapeLoaders() {
    }

    public static TapeFile load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return load(path.getFileName().toString(), input);
        }
    }

    public static TapeFile load(String sourceName, InputStream input) throws IOException {
        String normalized = sourceName == null ? "" : sourceName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".tap")) {
            return TapLoader.load(input);
        }
        if (normalized.endsWith(".tzx")) {
            return TzxLoader.load(input);
        }
        throw new IllegalArgumentException("Unsupported tape format: " + sourceName);
    }
}
