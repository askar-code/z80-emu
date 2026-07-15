package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.device.C64KeyMap;
import dev.z8emu.platform.bus.CpuBus;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64KeyboardTyperTest {
    @Test
    void runFramesAdvancesByRequestedFrameTStatesWithAtMostOneInstructionOvershoot() {
        C64Machine machine = bootableMachine();
        long startTState = machine.currentTState();
        int frames = 3;

        long instructions = C64KeyboardTyper.runFrames(machine, frames);

        long targetTState = startTState + (long) frames * machine.frameTStates();
        assertTrue(instructions > 0);
        assertTrue(machine.currentTState() >= targetTState);
        assertTrue(machine.currentTState() <= targetTState + 2);
    }

    @Test
    void typeCharacterReleasesTheMatrixAfterTheGap() {
        C64Machine machine = bootableMachine();
        CpuBus bus = machine.board().cpuBus();
        configureKernalKeyboardScan(bus);

        C64KeyboardTyper.typeCharacter(machine, 'P');

        assertEquals(0xFF, bus.readMemory(0xDC01));
    }

    @Test
    void heldChordIsVisibleUntilReleasedWithoutUsingTheTyper() {
        C64Machine machine = bootableMachine();
        CpuBus bus = machine.board().cpuBus();
        configureKernalKeyboardScan(bus);
        C64KeyMap.KeyChord chord = C64KeyMap.forCharacter('P');

        setChordState(machine, chord, true);
        assertEquals(0xFD, bus.readMemory(0xDC01));

        setChordState(machine, chord, false);
        assertEquals(0xFF, bus.readMemory(0xDC01));
    }

    private static void configureKernalKeyboardScan(CpuBus bus) {
        bus.writeMemory(0xDC02, 0xFF);
        bus.writeMemory(0xDC00, 0x00);
        bus.writeMemory(0xDC03, 0x00);
    }

    private static void setChordState(C64Machine machine, C64KeyMap.KeyChord chord, boolean pressed) {
        for (C64KeyMap.MatrixKey key : chord.keys()) {
            machine.board().keyboard().setKeyPressed(key.portABit(), key.portBBit(), pressed);
        }
    }

    private static C64Machine bootableMachine() {
        byte[] kernalRom = new byte[C64Memory.KERNAL_ROM_SIZE];
        Arrays.fill(kernalRom, (byte) 0xEA);
        kernalRom[0x1FFC] = 0x00;
        kernalRom[0x1FFD] = (byte) 0xE0;
        return new C64Machine(
                new byte[C64Memory.BASIC_ROM_SIZE],
                kernalRom,
                new byte[C64Memory.CHAR_ROM_SIZE]
        );
    }
}
