package dev.z8emu.machine.spectrum.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectrumContentionModelTest {
    private static final int[] CONTENTION_PATTERN = {6, 5, 4, 3, 2, 1, 0, 0};
    private static final int[] CONTENDED_EVEN_IO_PATTERN = {6, 5, 4, 3, 2, 1, 0, 6};
    private static final int[] CONTENDED_ODD_IO_PATTERN = {12, 11, 10, 9, 8, 7, 6, 12};
    private static final int[] EVEN_UNCONTENDED_IO_PATTERN = {5, 4, 3, 2, 1, 0, 0, 6};

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

    @Test
    void sequences48kIoContentionAfterAlreadyInsertedWaits() {
        SpectrumContentionModel model = new SpectrumContentionModel(69_888, 14_335, 224);

        assertIoContentionPatterns(model, 14_335);
        assertEquals(
                6,
                model.ioPortDelay(14_334, 0, 0x00FE),
                "The N:1,C:3 boundary must have one sequential contention point, not a 6+5+4 sum"
        );
    }

    @Test
    void sequences128kIoContentionAtItsOwnFrameBoundary() {
        SpectrumContentionModel model = new SpectrumContentionModel(70_908, 14_361, 228);

        assertIoContentionPatterns(model, 14_361);
        assertEquals(6, model.ioPortDelay(14_360, 0, 0x00FE));
    }

    @Test
    void sequencesEveryInternalNoMreqTStateAfterEarlierWaits() {
        SpectrumContentionModel model = new SpectrumContentionModel(69_888, 14_335, 224);

        assertEquals(12, model.internalMemoryDelay(14_335, 0, true, 3));
        assertEquals(11, model.internalMemoryDelay(14_335, 1, true, 3));
        assertEquals(6, model.internalMemoryDelay(14_335, 7, true, 3));
        assertEquals(0, model.internalMemoryDelay(14_335, 0, false, 3));
        assertEquals(0, model.internalMemoryDelay(14_335, 0, true, 0));
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

    private static void assertIoContentionPatterns(SpectrumContentionModel model, int startTState) {
        for (int phase = 0; phase < CONTENTION_PATTERN.length; phase++) {
            assertEquals(
                    CONTENDED_ODD_IO_PATTERN[phase],
                    model.ioPortDelay(startTState, phase, 0x40FF),
                    "contended high byte, phase " + phase
            );
            assertEquals(
                    CONTENDED_EVEN_IO_PATTERN[phase],
                    model.ioPortDelay(startTState, phase, 0x40FE),
                    "contended high byte and even port, phase " + phase
            );
            assertEquals(
                    EVEN_UNCONTENDED_IO_PATTERN[phase],
                    model.ioPortDelay(startTState, phase, 0x00FE),
                    "uncontended high byte and even port, phase " + phase
            );
            assertEquals(
                    0,
                    model.ioPortDelay(startTState, phase, 0x00FF),
                    "uncontended high byte and odd port, phase " + phase
            );
        }
    }
}
