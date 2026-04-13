package dev.z8emu.cpu.z80;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZexHarnessTest {
    @Test
    void prelimCompletesAsFastSmokeTest() throws Exception {
        ZexHarness.ZexResult result = ZexHarness.runResource("prelim.com", 200_000L);

        assertTrue(result.finished(), "prelim.com should terminate via CP/M trap");
        assertNull(result.failure(), () -> "prelim.com failed with output:\n" + result.output());
        assertTrue(
                result.output().contains("Preliminary tests complete"),
                () -> "Unexpected prelim output:\n" + result.output()
        );
    }

    @Test
    @Tag("zex")
    void zexdocCompletes() throws Exception {
        ZexHarness.ZexResult result = ZexHarness.runResource("zexdoc.cim", 8_000_000_000L);

        assertTrue(result.finished(), () -> formatFailure("zexdoc", result));
        assertNull(result.failure(), () -> formatFailure("zexdoc", result));
        assertTrue(result.output().contains("Tests complete"), () -> "Unexpected zexdoc output:\n" + result.output());
    }

    @Test
    @Tag("zex")
    void zexallCompletes() throws Exception {
        ZexHarness.ZexResult result = ZexHarness.runResource("zexall.cim", 8_000_000_000L);

        assertTrue(result.finished(), () -> formatFailure("zexall", result));
        assertNull(result.failure(), () -> formatFailure("zexall", result));
        assertTrue(result.output().contains("Tests complete"), () -> "Unexpected zexall output:\n" + result.output());
    }

    private static String formatFailure(String name, ZexHarness.ZexResult result) {
        return name
                + " did not complete cleanly."
                + "\ninstructions=" + result.instructions()
                + "\ntStates=" + result.tStates()
                + "\nfinished=" + result.finished()
                + "\noutput:\n" + result.output()
                + (result.failure() == null ? "" : "\nfailure=" + result.failure());
    }
}
