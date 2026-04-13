package dev.z8emu.platform.cpu;

public interface Cpu {
    void reset();

    void requestMaskableInterrupt();

    void requestNonMaskableInterrupt();

    int runInstruction();
}

