package dev.z8emu.machine.apple2;

public record Apple2ModelConfig(
        String modelName,
        long cpuClockHz,
        int frameTStates,
        int ramSize,
        int frameWidth,
        int frameHeight
) {
    public static Apple2ModelConfig appleIIPlus() {
        return new Apple2ModelConfig(
                "Apple II Plus",
                1_023_000,
                17_030,
                Apple2Memory.ADDRESS_SPACE_SIZE,
                Apple2VideoDevice.FRAME_WIDTH,
                Apple2VideoDevice.FRAME_HEIGHT
        );
    }
}
