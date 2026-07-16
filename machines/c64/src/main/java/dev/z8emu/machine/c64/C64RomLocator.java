package dev.z8emu.machine.c64;

import java.nio.file.Files;
import java.nio.file.Path;

public final class C64RomLocator {
    public static final String BASIC_ROM = "basic.901226-01.bin";
    public static final String KERNAL_ROM = "kernal.901227-03.bin";
    public static final String CHARGEN_ROM = "characters.901225-01.bin";
    public static final String BASIC_ROM_PROPERTY = "c64.basicRom";
    public static final String KERNAL_ROM_PROPERTY = "c64.kernalRom";
    public static final String CHARGEN_ROM_PROPERTY = "c64.chargenRom";

    private C64RomLocator() {
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
