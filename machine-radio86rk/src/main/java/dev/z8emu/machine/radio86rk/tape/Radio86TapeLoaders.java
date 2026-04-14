package dev.z8emu.machine.radio86rk.tape;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class Radio86TapeLoaders {
    private Radio86TapeLoaders() {
    }

    public static Radio86TapeFile load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return load(path.getFileName().toString(), input);
        }
    }

    public static Radio86TapeFile load(String sourceName, InputStream input) throws IOException {
        String normalized = sourceName == null ? "" : sourceName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".rk") || normalized.endsWith(".rkr")) {
            return Radio86RkTapeLoader.load(input);
        }
        throw new IllegalArgumentException("Unsupported Radio-86RK tape format: " + sourceName);
    }
}
