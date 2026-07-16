package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.media.C64PrgImage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class C64PrgBootTest {
    @Test
    void tokenizedBasicPrgPrintsHello() throws Exception {
        Path basicPath = C64RomLocator.locate("c64.basicRom", C64RomLocator.BASIC_ROM);
        Path kernalPath = C64RomLocator.locate("c64.kernalRom", C64RomLocator.KERNAL_ROM);
        Path chargenPath = C64RomLocator.locate("c64.chargenRom", C64RomLocator.CHARGEN_ROM);
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

        C64PrgImage image = C64PrgImage.parse(new byte[]{
                0x01, 0x08, 0x0E, 0x08, 0x0A, 0x00, (byte) 0x99, 0x22,
                0x48, 0x45, 0x4C, 0x4C, 0x4F, 0x22, 0x00, 0x00, 0x00
        });
        C64PrgLoader.inject(machine, image);
        String command = C64PrgLoader.startCommand(image, null);
        for (int index = 0; index < command.length(); index++) {
            C64KeyboardTyper.typeCharacter(machine, command.charAt(index));
        }

        assertTrue(runUntilScreenContains(machine, "HELLO", 200), "C64 did not print HELLO");
        assertTrue(C64ScreenText.contains(machine, "RUN"), "C64 screen did not retain the RUN command");
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
