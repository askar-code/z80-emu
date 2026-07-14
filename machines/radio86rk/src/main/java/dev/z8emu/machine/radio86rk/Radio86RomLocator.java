package dev.z8emu.machine.radio86rk;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared discovery policy for local Radio-86RK ROM files: an explicit
 * system-property override wins, otherwise walk up from the working
 * directory looking for the default file name.
 */
public final class Radio86RomLocator {
    private Radio86RomLocator() {
    }

    public static Path locate(String overrideProperty, String defaultFileName) {
        String explicitPath = System.getProperty(overrideProperty);
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path path = Path.of(explicitPath).toAbsolutePath().normalize();
            return Files.exists(path) ? path : null;
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(defaultFileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }
}
