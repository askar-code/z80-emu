package dev.z8emu.platform.bus.io;

@FunctionalInterface
public interface IoWriteHandler {
    void write(IoAccess access, int value);
}
