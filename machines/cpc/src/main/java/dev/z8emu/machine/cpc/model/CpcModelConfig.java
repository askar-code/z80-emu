package dev.z8emu.machine.cpc.model;

import dev.z8emu.machine.cpc.device.CpcGateArrayDevice;

public record CpcModelConfig(
        String modelName,
        long cpuClockHz,
        long psgClockHz,
        int frameTStates
) {
    public static CpcModelConfig cpc6128() {
        return new CpcModelConfig(
                "Amstrad CPC 6128",
                4_000_000,
                1_000_000,
                CpcGateArrayDevice.FRAME_TSTATES
        );
    }
}
