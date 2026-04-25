package dev.z8emu.platform.device;

public interface TimedDevice {
    default void reset() {
    }

    default void onTStatesElapsed(int tStates) {
    }
}

