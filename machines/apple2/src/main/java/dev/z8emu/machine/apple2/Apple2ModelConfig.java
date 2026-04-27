package dev.z8emu.machine.apple2;

import dev.z8emu.cpu.mos6502.Mos6502Variant;

public record Apple2ModelConfig(
        String modelName,
        long cpuClockHz,
        int frameTStates,
        int ramSize,
        int frameWidth,
        int frameHeight,
        Mos6502Variant cpuVariant,
        boolean supports16KSystemRom
) {
    public static Apple2ModelConfig appleIIPlus() {
        return new Apple2ModelConfig(
                "Apple II Plus",
                1_023_000,
                17_030,
                Apple2Memory.ADDRESS_SPACE_SIZE,
                Apple2VideoDevice.FRAME_WIDTH,
                Apple2VideoDevice.FRAME_HEIGHT,
                Mos6502Variant.NMOS_6502,
                false
        );
    }

    public static Apple2ModelConfig appleIIe128K() {
        return new Apple2ModelConfig(
                "Apple IIe 128K",
                1_023_000,
                17_030,
                128 * 1024,
                Apple2VideoDevice.FRAME_WIDTH,
                Apple2VideoDevice.FRAME_HEIGHT,
                Mos6502Variant.CMOS_65C02,
                true
        );
    }

    public boolean supportsSystemRomSize(int length) {
        if (length == Apple2Memory.SYSTEM_ROM_SIZE_16K) {
            return supports16KSystemRom;
        }
        return length == Apple2Memory.SYSTEM_ROM_SIZE_4K
                || length == Apple2Memory.SYSTEM_ROM_SIZE_8K
                || length == Apple2Memory.SYSTEM_ROM_SIZE_12K;
    }

    public boolean supportsLaunchImageSize(int length) {
        return length == Apple2Memory.ADDRESS_SPACE_SIZE || supportsSystemRomSize(length);
    }
}
