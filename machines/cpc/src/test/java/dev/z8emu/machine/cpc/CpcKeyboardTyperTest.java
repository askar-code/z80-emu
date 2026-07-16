package dev.z8emu.machine.cpc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpcKeyboardTyperTest {
    private static final int BASIC_SETTLE_FRAMES = 300;

    @Test
    void runFramesAdvancesByRequestedFrameTStatesWithAtMostOneInstructionOvershoot() {
        CpcMachine machine = CpcMachine.withBlankRom();
        long startTState = machine.currentTState();
        int frames = 3;

        long instructions = CpcKeyboardTyper.runFrames(machine, frames);

        long targetTState = startTState + (long) frames * machine.frameTStates();
        assertTrue(instructions > 0);
        assertTrue(machine.currentTState() >= targetTState);
        assertTrue(machine.currentTState() <= targetTState + 3);
    }

    @Test
    void typeCharacterHoldsAndReleasesTheWholeChordForTheFrozenCadence() {
        CpcMachine machine = CpcMachine.withBlankRom();
        List<Integer> requestedFrames = new ArrayList<>();
        List<Integer> shiftLineValues = new ArrayList<>();
        List<Integer> digitLineValues = new ArrayList<>();

        long executed = CpcKeyboardTyper.typeCharacter(machine, '"', frames -> {
            requestedFrames.add(frames);
            shiftLineValues.add(machine.board().keyboard().readLine(2));
            digitLineValues.add(machine.board().keyboard().readLine(8));
            return frames * 10L;
        });

        assertEquals(List.of(CpcKeyboardTyper.PRESS_FRAMES, CpcKeyboardTyper.GAP_FRAMES), requestedFrames);
        assertEquals(List.of(0xDF, 0xFF), shiftLineValues);
        assertEquals(List.of(0xFD, 0xFF), digitLineValues);
        assertEquals((CpcKeyboardTyper.PRESS_FRAMES + CpcKeyboardTyper.GAP_FRAMES) * 10L, executed);
    }

    @RepeatedTest(3)
    void firmwareAcceptsPokeCommandTypedThroughTheMatrix() throws IOException {
        Path romPath = CpcRomLocator.locate(
                CpcRomLocator.CPC6128_ROM_PROPERTY,
                CpcRomLocator.CPC6128_ROM
        );
        Assumptions.assumeTrue(romPath != null, "CPC 6128 ROM is unavailable");
        CpcMachine machine = new CpcMachine(Files.readAllBytes(romPath));

        CpcKeyboardTyper.runFrames(machine, BASIC_SETTLE_FRAMES);
        String command = "POKE &4000,42\r";
        for (int index = 0; index < command.length(); index++) {
            CpcKeyboardTyper.typeCharacter(machine, command.charAt(index));
        }
        CpcKeyboardTyper.runFrames(machine, 20);

        assertEquals(42, machine.board().memory().read(0x4000));
    }
}
