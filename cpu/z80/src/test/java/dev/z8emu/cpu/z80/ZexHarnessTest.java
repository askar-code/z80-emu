package dev.z8emu.cpu.z80;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZexHarnessTest {
    private static final Pattern FAILURE_MARKER = Pattern.compile(
            "\\b(?:ERROR|FAIL(?:ED|URE)?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Test
    void prelimCompletesAsFastSmokeTest() throws Exception {
        ZexHarness.ZexResult result = ZexHarness.runResource("prelim.com", 200_000L);

        assertSuccessful("prelim", result, "Preliminary tests complete");
    }

    @Test
    @Tag("zex")
    void zexdocCompletes() throws Exception {
        ZexHarness.ZexResult result = ZexHarness.runResource("zexdoc.cim", 8_000_000_000L);

        assertSuccessful("zexdoc", result, "Tests complete");
    }

    @Test
    @Tag("zex")
    void zexallCompletes() throws Exception {
        ZexHarness.ZexResult result = ZexHarness.runResource("zexall.cim", 8_000_000_000L);

        assertSuccessful("zexall", result, "Tests complete");
    }

    private static void assertSuccessful(
            String name,
            ZexHarness.ZexResult result,
            String expectedCompletionText
    ) {
        assertTrue(result.finished(), () -> formatFailure(name, result));
        assertNull(result.failure(), () -> formatFailure(name, result));
        assertTrue(
                result.output().contains(expectedCompletionText),
                () -> formatFailure(name, result)
        );
        assertFalse(
                FAILURE_MARKER.matcher(result.output()).find(),
                () -> formatFailure(name, result)
        );
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
