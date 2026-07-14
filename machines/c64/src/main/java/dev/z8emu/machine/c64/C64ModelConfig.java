package dev.z8emu.machine.c64;

public record C64ModelConfig(
        String modelName,
        long cpuClockHz,
        int frameTStates,
        int frameWidth,
        int frameHeight
) {
    public static C64ModelConfig pal() {
        return new C64ModelConfig(
                "Commodore 64 (PAL)",
                985_248,
                19_656,
                384,
                272
        );
    }
}
