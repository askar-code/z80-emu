package dev.z8emu.app.desktop;

import java.io.IOException;
import java.nio.file.Path;

interface DesktopMachineDefinition {
    DesktopMachineKind kind();

    void validateRom(byte[] romImage, Path romPath);

    void validateArgumentCount(int positionalCount);

    default void validateLaunchOptions(DesktopLaunchOptions options, int positionalCount) {
        if (options.hasAny()) {
            throw new IllegalArgumentException("Usage: " + usage());
        }
    }

    default DesktopLaunchConfig.LoadedMedia loadMedia(String rawPath) throws IOException {
        throw new IllegalArgumentException("Usage: " + usage());
    }

    default DesktopLaunchConfig.LoadedMedia loadMedia(String rawPath, DesktopLaunchOptions options) throws IOException {
        validateLaunchOptions(options, 2);
        return loadMedia(rawPath);
    }

    void open(DesktopLaunchConfig config);

    String usage();
}
