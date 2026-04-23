package dev.z8emu.machine.spectrum.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectrumContentionModelTest {
    private static final int[] CONTENTION_PATTERN = {6, 5, 4, 3, 2, 1, 0, 0};

    @Test
    void applies48kScreenContentionPatternFromFirstVisibleLine() {
        SpectrumContentionModel model = new SpectrumContentionModel(69_888, 14_335, 224);

        assertContentionPattern(model, 14_335);
        assertEquals(6, model.memoryDelay(14_335 + 224, 0, true));
    }

    @Test
    void applies128kScreenContentionPatternFromFirstVisibleLine() {
        SpectrumContentionModel model = new SpectrumContentionModel(70_908, 14_361, 228);

        assertContentionPattern(model, 14_361);
        assertEquals(6, model.memoryDelay(14_361 + 228, 0, true));
    }

    @Test
    void doesNotDelayOutsideVisibleScreenFetchWindow() {
        SpectrumContentionModel model = new SpectrumContentionModel(70_908, 14_361, 228);

        assertEquals(0, model.memoryDelay(14_360, 0, true));
        assertEquals(0, model.memoryDelay(14_361 + 128, 0, true));
        assertEquals(0, model.memoryDelay(14_361 + 192 * 228, 0, true));
    }

    @Test
    void ignoresUncontendedMemoryAndWrapsAtFrameBoundary() {
        SpectrumContentionModel model = new SpectrumContentionModel(70_908, 14_361, 228);

        assertEquals(0, model.memoryDelay(14_361, 0, false));
        assertEquals(6, model.memoryDelay(70_908 + 14_361, 0, true));
    }

    private static void assertContentionPattern(SpectrumContentionModel model, int startTState) {
        for (int phase = 0; phase < CONTENTION_PATTERN.length; phase++) {
            assertEquals(
                    CONTENTION_PATTERN[phase],
                    model.memoryDelay(startTState, phase, true),
                    "phase " + phase
            );
        }
    }
}
