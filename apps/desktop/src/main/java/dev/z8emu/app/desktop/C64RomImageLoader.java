package dev.z8emu.app.desktop;

import dev.z8emu.machine.c64.C64Memory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class C64RomImageLoader {
    static final String BASIC_ROM = "basic.901226-01.bin";
    static final String KERNAL_ROM = "kernal.901227-03.bin";
    static final String CHARGEN_ROM = "characters.901225-01.bin";

    private C64RomImageLoader() {
    }

    static C64RomSet load(Path romDirectoryOrFile) throws IOException {
        Path directory = romDirectory(romDirectoryOrFile);
        Path basicPath = overriddenPath("c64.basicRom", directory.resolve(BASIC_ROM));
        Path kernalPath = overriddenPath("c64.kernalRom", directory.resolve(KERNAL_ROM));
        Path chargenPath = overriddenPath("c64.chargenRom", directory.resolve(CHARGEN_ROM));
        return new C64RomSet(
                readRom(basicPath, C64Memory.BASIC_ROM_SIZE),
                readRom(kernalPath, C64Memory.KERNAL_ROM_SIZE),
                readRom(chargenPath, C64Memory.CHAR_ROM_SIZE)
        );
    }

    private static Path romDirectory(Path path) {
        if (Files.isDirectory(path)) {
            return path;
        }
        if (isKnownRomFile(path)) {
            Path parent = path.getParent();
            return parent == null ? Path.of(".") : parent;
        }
        return path;
    }

    private static boolean isKnownRomFile(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.toString().toLowerCase(Locale.ROOT);
        return normalized.equals(BASIC_ROM) || normalized.equals(KERNAL_ROM) || normalized.equals(CHARGEN_ROM);
    }

    private static Path overriddenPath(String propertyName, Path defaultPath) {
        String override = System.getProperty(propertyName);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return defaultPath;
    }

    private static byte[] readRom(Path path, int expectedSize) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Missing C64 ROM: " + path);
        }
        byte[] image = Files.readAllBytes(path);
        if (image.length != expectedSize) {
            throw new IllegalArgumentException(
                    "C64 ROM must be exactly " + expectedSize + " bytes: " + path
            );
        }
        return image;
    }

    record C64RomSet(byte[] basic, byte[] kernal, byte[] chargen) {
    }
}
