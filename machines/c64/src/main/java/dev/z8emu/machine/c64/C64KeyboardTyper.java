package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.device.C64KeyMap;
import java.util.Objects;

public final class C64KeyboardTyper {
    public static final int PRESS_FRAMES = 2;
    public static final int GAP_FRAMES = 2;

    private C64KeyboardTyper() {
    }

    public static long typeCharacter(C64Machine machine, char character) {
        Objects.requireNonNull(machine, "machine");
        C64KeyMap.KeyChord chord = C64KeyMap.forCharacter(character);
        setChordState(machine, chord, true);
        long executed = runFrames(machine, PRESS_FRAMES);
        setChordState(machine, chord, false);
        return executed + runFrames(machine, GAP_FRAMES);
    }

    public static long runFrames(C64Machine machine, int frames) {
        Objects.requireNonNull(machine, "machine");
        long executed = 0;
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            long targetTState = machine.currentTState() + machine.frameTStates();
            while (machine.currentTState() < targetTState) {
                machine.runInstruction();
                executed++;
            }
        }
        return executed;
    }

    private static void setChordState(C64Machine machine, C64KeyMap.KeyChord chord, boolean pressed) {
        for (C64KeyMap.MatrixKey key : chord.keys()) {
            machine.board().keyboard().setKeyPressed(key.portABit(), key.portBBit(), pressed);
        }
    }
}
