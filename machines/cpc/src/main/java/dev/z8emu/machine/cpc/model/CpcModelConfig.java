package dev.z8emu.machine.cpc.model;

public record CpcModelConfig(
        String modelName,
        long cpuClockHz,
        long psgClockHz,
        int frameTStates,
        int ramBankCount,
        int visibleFrameWidth,
        int visibleFrameHeight
) {
    public static CpcModelConfig cpc6128() {
        return new CpcModelConfig(
                "Amstrad CPC 6128",
                4_000_000,
                1_000_000,
                79_872,
                8,
                768,
                272
        );
    }
}
