package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.Apple2Memory;
import dev.z8emu.machine.apple2.Apple2ModelConfig;
import dev.z8emu.machine.apple2.disk.Apple2Disk2Controller;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class Apple2RomImageLoader {
    private static final String ENHANCED_IIE_CD_ROM = "342-0304-a.e10";
    private static final String ENHANCED_IIE_EF_ROM = "342-0303-a.e8";

    private Apple2RomImageLoader() {
    }

    static Apple2ModelConfig modelConfigFor(DesktopMachineKind kind) {
        return switch (kind) {
            case APPLE2 -> Apple2ModelConfig.appleIIPlus();
            case APPLE2E -> Apple2ModelConfig.appleIIe128K();
            case SPECTRUM48, SPECTRUM128, RADIO86RK, CPC6128 ->
                    throw new IllegalArgumentException("Expected Apple II machine kind: " + kind);
        };
    }

    static byte[] load(DesktopMachineKind kind, Path imagePath) throws IOException {
        if (kind == DesktopMachineKind.APPLE2E) {
            byte[] bundle = tryLoadEnhancedIIeBundle(imagePath);
            if (bundle != null) {
                return bundle;
            }
        }
        return Files.readAllBytes(imagePath);
    }

    static byte[] loadDisk2SlotRom(Path romPath) throws IOException {
        byte[] rom = Files.readAllBytes(romPath);
        if (rom.length != Apple2Disk2Controller.SLOT_ROM_SIZE) {
            throw new IllegalArgumentException("Disk II slot ROM must be exactly 256 bytes: " + romPath);
        }
        return rom;
    }

    private static byte[] tryLoadEnhancedIIeBundle(Path imagePath) throws IOException {
        Path directory;
        if (Files.isDirectory(imagePath)) {
            directory = imagePath;
        } else if (isEnhancedIIeRomPart(imagePath)) {
            directory = imagePath.getParent();
        } else {
            return null;
        }
        if (directory == null) {
            directory = Path.of(".");
        }

        Path cdRom = directory.resolve(ENHANCED_IIE_CD_ROM);
        Path efRom = directory.resolve(ENHANCED_IIE_EF_ROM);
        if (!Files.isRegularFile(cdRom) || !Files.isRegularFile(efRom)) {
            return null;
        }

        byte[] cd = Files.readAllBytes(cdRom);
        byte[] ef = Files.readAllBytes(efRom);
        if (cd.length != Apple2Memory.SYSTEM_ROM_SIZE_8K || ef.length != Apple2Memory.SYSTEM_ROM_SIZE_8K) {
            throw new IllegalArgumentException(
                    "Enhanced Apple IIe ROM halves must be exactly 8 KB: %s, %s".formatted(cdRom, efRom)
            );
        }

        byte[] bundle = new byte[Apple2Memory.SYSTEM_ROM_SIZE_16K];
        System.arraycopy(cd, 0, bundle, 0, cd.length);
        System.arraycopy(ef, 0, bundle, cd.length, ef.length);
        return bundle;
    }

    private static boolean isEnhancedIIeRomPart(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.toString().toLowerCase(Locale.ROOT);
        return normalized.equals(ENHANCED_IIE_CD_ROM) || normalized.equals(ENHANCED_IIE_EF_ROM);
    }

}
