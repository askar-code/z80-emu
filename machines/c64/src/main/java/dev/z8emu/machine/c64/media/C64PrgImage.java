package dev.z8emu.machine.c64.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public record C64PrgImage(int loadAddress, byte[] payload) {
    public C64PrgImage {
        Objects.requireNonNull(payload, "payload");
        if ((long) loadAddress + payload.length > 0x10000L) {
            throw new IllegalArgumentException("C64 PRG payload exceeds the 64K address space");
        }
        payload = Arrays.copyOf(payload, payload.length);
    }

    public static C64PrgImage parse(byte[] fileBytes) {
        Objects.requireNonNull(fileBytes, "fileBytes");
        if (fileBytes.length < 3) {
            throw new IllegalArgumentException("C64 PRG file must contain a 2-byte load address and payload");
        }
        int loadAddress = Byte.toUnsignedInt(fileBytes[0])
                | (Byte.toUnsignedInt(fileBytes[1]) << 8);
        return new C64PrgImage(loadAddress, Arrays.copyOfRange(fileBytes, 2, fileBytes.length));
    }

    public static C64PrgImage load(Path path) throws IOException {
        return parse(Files.readAllBytes(path));
    }

    public int endAddress() {
        return loadAddress + payload.length;
    }
}
