package dev.z8emu.app.desktop;

import java.io.IOException;
import java.nio.file.Path;

interface DesktopMachineDefinition {
    DesktopMachineKind kind();

    void validateRom(byte[] romImage, Path romPath);

    void validateArgumentCount(int positionalCount);

    default DesktopLaunchConfig.LoadedMedia loadMedia(String rawPath) throws IOException {
        throw new IllegalArgumentException("Usage: " + usage());
    }

    void open(DesktopLaunchConfig config);

    String usage();
}
