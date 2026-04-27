package dev.z8emu.machine.apple2.disk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Apple2WozDiskImageLoader {
    private Apple2WozDiskImageLoader() {
    }

    public static Apple2WozDiskImage load(Path path) throws IOException {
        try {
            return Apple2WozDiskImage.fromWoz1Bytes(Files.readAllBytes(path));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Unsupported Apple II WOZ disk image: " + path, failure);
        }
    }
}
