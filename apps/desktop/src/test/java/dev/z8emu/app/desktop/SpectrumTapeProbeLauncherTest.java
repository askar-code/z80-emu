package dev.z8emu.app.desktop;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumTapeProbeLauncherTest {
    @Test
    void acceptsAnExactPositiveProbeResult() {
        SpectrumTapeProbeLauncher.ExpectedState expected =
                new SpectrumTapeProbeLauncher.ExpectedState(0x880E, true, 6, 6, 0x1234ABCDL);
        SpectrumTapeProbeLauncher.ActualState actual =
                new SpectrumTapeProbeLauncher.ActualState(0x880E, true, 6, 6, 0x1234ABCDL);

        assertTrue(SpectrumTapeProbeLauncher.expectationFailures(expected, actual).isEmpty());
    }

    @Test
    void reportsEveryMismatchedProbeExpectation() {
        SpectrumTapeProbeLauncher.ExpectedState expected =
                new SpectrumTapeProbeLauncher.ExpectedState(0x880E, true, 6, 6, 0x1234ABCDL);
        SpectrumTapeProbeLauncher.ActualState actual =
                new SpectrumTapeProbeLauncher.ActualState(0x1234, false, 2, 7, 0xDEADBEEFL);

        List<String> failures = SpectrumTapeProbeLauncher.expectationFailures(expected, actual);

        assertEquals(5, failures.size());
        assertTrue(failures.get(0).contains("expected=0x880E actual=0x1234"));
        assertTrue(failures.get(4).contains("expected=0x1234ABCD actual=0xDEADBEEF"));
    }
}
