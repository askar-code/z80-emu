package dev.z8emu.machine.c64;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class C64JoystickBasicTest {
    @Test
    void basicPeekReadsPortTwoFireHeldAndReleased() throws Exception {
        Path basicPath = C64RomLocator.locate(C64RomLocator.BASIC_ROM_PROPERTY, C64RomLocator.BASIC_ROM);
        Path kernalPath = C64RomLocator.locate(C64RomLocator.KERNAL_ROM_PROPERTY, C64RomLocator.KERNAL_ROM);
        Path chargenPath = C64RomLocator.locate(C64RomLocator.CHARGEN_ROM_PROPERTY, C64RomLocator.CHARGEN_ROM);
        Assumptions.assumeTrue(
                basicPath != null && kernalPath != null && chargenPath != null,
                () -> "Skipping: C64 ROM set not found"
        );

        C64Machine machine = new C64Machine(
                Files.readAllBytes(basicPath),
                Files.readAllBytes(kernalPath),
                Files.readAllBytes(chargenPath)
        );
        assertTrue(runUntilScreenContains(machine, "READY.", 500), "C64 did not reach READY.");

        // BASIC leaves $DC00 latch=$7F and DDRA=$FF: $7F & $EF = 111 held, $7F & $FF = 127 released.
        machine.board().keyboard().setJoystickPressed(2, 4, true);
        type(machine, "PRINT PEEK(56320)\r");
        assertTrue(runUntilScreenContains(machine, "111", 200), "BASIC did not read held fire as 111");

        machine.board().keyboard().setJoystickPressed(2, 4, false);
        type(machine, "PRINT PEEK(56320)\r");
        assertTrue(runUntilScreenContains(machine, "127", 200), "BASIC did not read released fire as 127");
    }

    private static void type(C64Machine machine, String text) {
        for (int index = 0; index < text.length(); index++) {
            C64KeyboardTyper.typeCharacter(machine, text.charAt(index));
        }
    }

    private static boolean runUntilScreenContains(C64Machine machine, String expected, int maxFrames) {
        for (int frame = 0; frame < maxFrames; frame++) {
            C64KeyboardTyper.runFrames(machine, 1);
            if (C64ScreenText.contains(machine, expected)) {
                return true;
            }
        }
        return false;
    }
}
