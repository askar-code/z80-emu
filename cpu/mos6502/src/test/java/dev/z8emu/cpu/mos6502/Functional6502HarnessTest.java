package dev.z8emu.cpu.mos6502;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Functional6502HarnessTest {
    @Test
    @Tag("klaus")
    void klausFunctionalTestPasses() {
        Path imagePath = Path.of(System.getProperty("z8emu.klaus.image", "media/6502_functional_test.bin"));
        Assumptions.assumeTrue(
                Files.exists(imagePath),
                "media/6502_functional_test.bin not present — see docs/commodore-64-plan.md Phase 0"
        );
        int successPc = Integer.decode(System.getProperty("z8emu.klaus.successPc", "0x3469"));

        Functional6502Harness.FunctionalResult result = Functional6502Harness.runImage(
                imagePath,
                500_000_000L
        );

        assertTrue(result.trapped(), () -> formatFailure(result));
        assertNull(result.failure(), () -> formatFailure(result));
        assertEquals(successPc, result.trapPc(), () -> formatFailure(result));
        System.out.println(
                "klaus: instructions=" + result.instructions()
                        + " tStates=" + result.tStates()
                        + " trapPc=0x" + Integer.toHexString(result.trapPc())
        );
    }

    private static String formatFailure(Functional6502Harness.FunctionalResult result) {
        return "Klaus functional test did not complete cleanly."
                + "\ninstructions=" + result.instructions()
                + "\ntStates=" + result.tStates()
                + "\ntrapPc=0x" + Integer.toHexString(result.trapPc())
                + "\n" + result.registerDump()
                + (result.failure() == null ? "" : "\nfailure=" + result.failure());
    }
}
