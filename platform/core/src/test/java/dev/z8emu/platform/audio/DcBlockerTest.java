package dev.z8emu.platform.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DcBlockerTest {
    @Test
    void steadyInitialLevelProducesSilence() {
        DcBlocker dcBlocker = new DcBlocker(DcBlocker.DEFAULT_ALPHA, -17_000);

        assertEquals(0, dcBlocker.nextSample(-17_000));
    }

    @Test
    void levelStepProducesImpulseThenDecaysToSilence() {
        DcBlocker dcBlocker = new DcBlocker(DcBlocker.DEFAULT_ALPHA, 0);

        assertTrue(dcBlocker.nextSample(12_000) > 0);

        short settled = 0;
        for (int i = 0; i < 5_000; i++) {
            settled = dcBlocker.nextSample(12_000);
        }
        assertEquals(0, settled);
    }

    @Test
    void resetChangesTheSilentReferenceLevel() {
        DcBlocker dcBlocker = new DcBlocker(DcBlocker.DEFAULT_ALPHA, 0);
        dcBlocker.nextSample(12_000);

        dcBlocker.reset(12_000);

        assertEquals(0, dcBlocker.nextSample(12_000));
    }

    @Test
    void rejectsUnstableAlphaValues() {
        assertThrows(IllegalArgumentException.class, () -> new DcBlocker(-0.1d, 0));
        assertThrows(IllegalArgumentException.class, () -> new DcBlocker(1.0d, 0));
        assertThrows(IllegalArgumentException.class, () -> new DcBlocker(Double.NaN, 0));
    }
}
