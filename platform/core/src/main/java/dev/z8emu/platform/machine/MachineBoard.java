package dev.z8emu.platform.machine;

import dev.z8emu.platform.bus.CpuBus;

public interface MachineBoard {
    CpuBus cpuBus();

    void reset();

    void onTStatesElapsed(int tStates, long currentTState);

    default boolean maskableInterruptLineActive(long currentTState) {
        return false;
    }
}
