package dev.z8emu.machine.apple2.disk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Apple2DosDiskImageLoader {
    private Apple2DosDiskImageLoader() {
    }

    public static Apple2DosDiskImage load(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return Apple2DosDiskImage.fromDosOrderedBytes(bytes);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Unsupported Apple II DOS-order disk image: " + path, failure);
        }
    }
}
