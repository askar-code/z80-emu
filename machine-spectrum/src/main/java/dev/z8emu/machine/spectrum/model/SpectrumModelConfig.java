package dev.z8emu.machine.spectrum.model;

import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import java.util.Arrays;

public record SpectrumModelConfig(
        String modelName,
        long cpuClockHz,
        int frameTStates,
        int romBankCount,
        int ramBankCount,
        int slotSize,
        int fixedLowerRamBankIndex,
        int fixedMiddleRamBankIndex,
        int[] screenBankIndices,
        int defaultRomIndex,
        int defaultTopRamBankIndex,
        int defaultScreenBankIndex,
        boolean pagingSupported,
        boolean aySupported
) {
    public SpectrumModelConfig {
        if (romBankCount <= 0) {
            throw new IllegalArgumentException("romBankCount must be positive");
        }
        if (ramBankCount <= 0) {
            throw new IllegalArgumentException("ramBankCount must be positive");
        }
        if (slotSize <= 0) {
            throw new IllegalArgumentException("slotSize must be positive");
        }
        screenBankIndices = screenBankIndices == null ? new int[0] : Arrays.copyOf(screenBankIndices, screenBankIndices.length);
        validateIndex("defaultRomIndex", defaultRomIndex, romBankCount);
        validateIndex("fixedLowerRamBankIndex", fixedLowerRamBankIndex, ramBankCount);
        validateIndex("fixedMiddleRamBankIndex", fixedMiddleRamBankIndex, ramBankCount);
        validateIndex("defaultTopRamBankIndex", defaultTopRamBankIndex, ramBankCount);
        if (screenBankIndices.length == 0) {
            throw new IllegalArgumentException("screenBankIndices must not be empty");
        }
        for (int screenBankIndex : screenBankIndices) {
            validateIndex("screenBankIndex", screenBankIndex, ramBankCount);
        }
        validateIndex("defaultScreenBankIndex", defaultScreenBankIndex, screenBankIndices.length);
    }

    public static SpectrumModelConfig spectrum48k() {
        return new SpectrumModelConfig(
                "ZX Spectrum 48K",
                3_500_000L,
                SpectrumUlaDevice.T_STATES_PER_FRAME,
                1,
                3,
                16 * 1024,
                0,
                1,
                new int[]{0},
                0,
                2,
                0,
                false,
                false
        );
    }

    public static SpectrumModelConfig spectrum128() {
        return new SpectrumModelConfig(
                "ZX Spectrum 128",
                3_546_900L,
                70_908,
                2,
                8,
                16 * 1024,
                5,
                2,
                new int[]{5, 7},
                0,
                0,
                0,
                true,
                true
        );
    }

    public int screenBankIndex(int optionIndex) {
        return screenBankIndices[optionIndex];
    }

    private static void validateIndex(String name, int index, int size) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("%s out of range: %d".formatted(name, index));
        }
    }
}
