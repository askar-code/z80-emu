package dev.z8emu.machine.cpc;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CpcRomLocator {
    public static final String CPC6128_ROM = "cpc6128.rom";
    public static final String CPC6128_ROM_PROPERTY = "cpc.rom";

    private CpcRomLocator() {
    }

    public static Path locate(String overrideProperty, String defaultFileName) {
        String explicitPath = System.getProperty(overrideProperty);
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path path = Path.of(explicitPath).toAbsolutePath().normalize();
            return Files.exists(path) ? path : null;
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("media").resolve(defaultFileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            candidate = current.resolve(defaultFileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }
}
