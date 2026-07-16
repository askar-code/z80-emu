package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.device.C64VideoDevice;

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
                C64VideoDevice.CYCLES_PER_LINE * C64VideoDevice.LINES_PER_FRAME,
                C64VideoDevice.FRAME_WIDTH,
                C64VideoDevice.FRAME_HEIGHT
        );
    }
}
