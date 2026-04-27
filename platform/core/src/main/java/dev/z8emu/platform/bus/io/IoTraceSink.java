package dev.z8emu.platform.bus.io;

@FunctionalInterface
public interface IoTraceSink {
    IoTraceSink NONE = (mappingName, read, access, value) -> {
    };

    void traceIo(String mappingName, boolean read, IoAccess access, int value);
}
