package dev.z8emu.machine.cpc;

import dev.z8emu.machine.cpc.device.CpcKeyMap;
import java.util.Objects;

public final class CpcKeyboardTyper {
    public static final int PRESS_FRAMES = 2;
    public static final int GAP_FRAMES = 2;

    private CpcKeyboardTyper() {
    }

    public static long typeCharacter(CpcMachine machine, char character) {
        Objects.requireNonNull(machine, "machine");
        CpcKeyMap.KeyChord chord = CpcKeyMap.forCharacter(character);
        setChordState(machine, chord, true);
        long executed;
        try {
            executed = runFrames(machine, PRESS_FRAMES);
        } finally {
            setChordState(machine, chord, false);
        }
        return executed + runFrames(machine, GAP_FRAMES);
    }

    public static long typeCharacter(CpcMachine machine, char character, FrameRunner frameRunner) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(frameRunner, "frameRunner");
        CpcKeyMap.KeyChord chord = CpcKeyMap.forCharacter(character);
        setChordState(machine, chord, true);
        long executed;
        try {
            executed = frameRunner.runFrames(PRESS_FRAMES);
        } finally {
            setChordState(machine, chord, false);
        }
        return executed + frameRunner.runFrames(GAP_FRAMES);
    }

    public static long runFrames(CpcMachine machine, int frames) {
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

    private static void setChordState(CpcMachine machine, CpcKeyMap.KeyChord chord, boolean pressed) {
        for (CpcKeyMap.MatrixKey key : chord.keys()) {
            machine.board().keyboard().setKeyPressed(key.line(), key.bit(), pressed);
        }
    }

    @FunctionalInterface
    public interface FrameRunner {
        long runFrames(int frames);
    }
}
