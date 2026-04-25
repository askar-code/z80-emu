package dev.z8emu.machine.radio86rk.model;

import dev.z8emu.machine.radio86rk.device.Radio86VideoDevice;

public record Radio86ModelConfig(
        String modelName,
        long cpuClockHz,
        int frameTStates
) {
    public static Radio86ModelConfig radio86rk() {
        return new Radio86ModelConfig(
                "Radio-86RK",
                16_000_000L / 9L,
                Radio86VideoDevice.T_STATES_PER_FRAME
        );
    }
}
