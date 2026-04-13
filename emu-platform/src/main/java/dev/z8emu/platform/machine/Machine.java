package dev.z8emu.platform.machine;

public interface Machine {
    void reset();

    int runInstruction();

    long currentTState();
}

