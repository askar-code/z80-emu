package dev.z8emu.platform.time;

public final class TStateCounter {
    private long value;

    public long value() {
        return value;
    }

    public void reset() {
        value = 0;
    }

    public void advance(int tStates) {
        if (tStates < 0) {
            throw new IllegalArgumentException("tStates must be non-negative");
        }

        value += tStates;
    }
}

