package dev.z8emu.platform.cpu;

public interface Cpu {
    void reset();

    void requestMaskableInterrupt();

    default void clearMaskableInterrupt() {
    }

    void requestNonMaskableInterrupt();

    int runInstruction();
}
