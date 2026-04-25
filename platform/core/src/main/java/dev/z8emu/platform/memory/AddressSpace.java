package dev.z8emu.platform.memory;

public interface AddressSpace {
    int read(int address);

    void write(int address, int value);

    void reset();
}
