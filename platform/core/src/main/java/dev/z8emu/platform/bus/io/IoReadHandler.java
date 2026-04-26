package dev.z8emu.platform.bus.io;

@FunctionalInterface
public interface IoReadHandler {
    int read(IoAccess access);
}
