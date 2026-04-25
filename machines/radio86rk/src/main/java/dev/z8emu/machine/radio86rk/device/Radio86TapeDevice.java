package dev.z8emu.machine.radio86rk.device;

import dev.z8emu.machine.radio86rk.tape.Radio86TapeFile;

public final class Radio86TapeDevice {
    private Radio86TapeFile tapeFile;
    private boolean playing;
    private int halfWaveIndex;
    private int remainingTStates;
    private boolean inputLow;
    private boolean outputHigh;
    private long elapsedTStates;

    public synchronized void load(Radio86TapeFile tapeFile) {
        this.tapeFile = tapeFile;
        rewindInternal();
    }

    public synchronized void play() {
        if (tapeFile != null && tapeFile.halfWaveCount() > 0) {
            if (halfWaveIndex >= tapeFile.halfWaveCount()) {
                rewindInternal();
            }
            playing = true;
            primeCurrentHalfWave();
        }
    }

    public synchronized void stop() {
        playing = false;
    }

    public synchronized void rewind() {
        rewindInternal();
    }

    public synchronized boolean isPlaying() {
        return playing;
    }

    public synchronized boolean isLoaded() {
        return tapeFile != null;
    }

    public synchronized boolean isAtEnd() {
        return tapeFile != null && !playing && halfWaveIndex >= tapeFile.halfWaveCount();
    }

    public synchronized boolean inputLow() {
        return inputLow;
    }

    public synchronized int currentHalfWaveIndex() {
        return Math.min(halfWaveIndex, tapeFile == null ? 0 : tapeFile.halfWaveCount());
    }

    public synchronized int totalHalfWaves() {
        return tapeFile == null ? 0 : tapeFile.halfWaveCount();
    }

    public synchronized void setOutputHigh(boolean outputHigh) {
        this.outputHigh = outputHigh;
    }

    public synchronized boolean outputHigh() {
        return outputHigh;
    }

    public synchronized void reset() {
        rewindInternal();
        outputHigh = false;
    }

    public synchronized void syncToTState(long targetTState) {
        if (targetTState <= elapsedTStates) {
            return;
        }

        int delta = (int) Math.min(Integer.MAX_VALUE, targetTState - elapsedTStates);
        elapsedTStates += delta;

        if (!playing || tapeFile == null || tapeFile.halfWaveCount() == 0) {
            return;
        }

        int remaining = delta;
        while (remaining > 0 && playing) {
            if (remainingTStates <= 0) {
                primeCurrentHalfWave();
                if (!playing) {
                    break;
                }
            }

            int consumed = Math.min(remaining, remainingTStates);
            remaining -= consumed;
            remainingTStates -= consumed;
            if (remainingTStates == 0) {
                halfWaveIndex++;
                if (halfWaveIndex >= tapeFile.halfWaveCount()) {
                    playing = false;
                    inputLow = false;
                }
            }
        }
    }

    private void rewindInternal() {
        playing = false;
        halfWaveIndex = 0;
        remainingTStates = 0;
        inputLow = false;
        elapsedTStates = 0;
        primeCurrentHalfWave();
    }

    private void primeCurrentHalfWave() {
        if (tapeFile == null || halfWaveIndex >= tapeFile.halfWaveCount()) {
            inputLow = false;
            remainingTStates = 0;
            return;
        }
        inputLow = tapeFile.lowLevels()[halfWaveIndex];
        remainingTStates = tapeFile.halfWaveDurationsTStates()[halfWaveIndex];
    }
}
