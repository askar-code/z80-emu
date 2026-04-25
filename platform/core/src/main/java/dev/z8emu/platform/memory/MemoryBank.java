package dev.z8emu.platform.memory;

public interface MemoryBank {
    int length();

    int read(int offset);

    void write(int offset, int value);

    default void reset() {
    }
}
