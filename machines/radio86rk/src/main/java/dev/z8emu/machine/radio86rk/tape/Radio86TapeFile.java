package dev.z8emu.machine.radio86rk.tape;

public record Radio86TapeFile(boolean[] lowLevels, int[] halfWaveDurationsTStates) {
    public Radio86TapeFile {
        if (lowLevels == null || halfWaveDurationsTStates == null) {
            throw new IllegalArgumentException("Tape data must not be null");
        }
        if (lowLevels.length != halfWaveDurationsTStates.length) {
            throw new IllegalArgumentException("Tape level and duration arrays must have the same length");
        }
    }

    public int halfWaveCount() {
        return halfWaveDurationsTStates.length;
    }
}
