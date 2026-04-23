package dev.z8emu.machine.spectrum.model;

public final class SpectrumContentionModel {
    private static final int[] CONTENTION_PATTERN = {6, 5, 4, 3, 2, 1, 0, 0};

    private final int frameTStates;
    private final int screenContentionStartTState;
    private final int tStatesPerScanline;

    public SpectrumContentionModel(int frameTStates, int screenContentionStartTState, int tStatesPerScanline) {
        if (frameTStates <= 0) {
            throw new IllegalArgumentException("frameTStates must be positive");
        }
        if (screenContentionStartTState < 0) {
            throw new IllegalArgumentException("screenContentionStartTState must be non-negative");
        }
        if (tStatesPerScanline <= 0) {
            throw new IllegalArgumentException("tStatesPerScanline must be positive");
        }
        this.frameTStates = frameTStates;
        this.screenContentionStartTState = screenContentionStartTState;
        this.tStatesPerScanline = tStatesPerScanline;
    }

    public int ioPortDelay(long currentTState, int phaseTStates, int port) {
        int lowBitReset = (port & 0x0001) == 0 ? 1 : 0;
        int highByteContended = (((port >>> 8) & 0xFF) >= 0x40 && ((port >>> 8) & 0xFF) <= 0x7F) ? 1 : 0;

        if (lowBitReset == 0 && highByteContended == 0) {
            return 0;
        }

        long cycle = currentTState + Math.max(0, phaseTStates);
        if (lowBitReset != 0 && highByteContended == 0) {
            return contentionDelayAt(cycle + 1) + contentionDelayAt(cycle + 2) + contentionDelayAt(cycle + 3);
        }
        if (lowBitReset == 0) {
            return contentionDelayAt(cycle) + contentionDelayAt(cycle + 1)
                    + contentionDelayAt(cycle + 2) + contentionDelayAt(cycle + 3);
        }
        return contentionDelayAt(cycle) + contentionDelayAt(cycle + 1)
                + contentionDelayAt(cycle + 2) + contentionDelayAt(cycle + 3);
    }

    public int memoryDelay(long currentTState, int phaseTStates, boolean contended) {
        if (!contended) {
            return 0;
        }
        return contentionDelayAt(currentTState + Math.max(0, phaseTStates));
    }

    private int contentionDelayAt(long absoluteTState) {
        int frameOffset = Math.floorMod((int) (absoluteTState % frameTStates), frameTStates);
        int screenOffset = frameOffset - screenContentionStartTState;
        if (screenOffset < 0) {
            return 0;
        }

        int scanline = screenOffset / tStatesPerScanline;
        if (scanline < 0 || scanline >= 192) {
            return 0;
        }

        int lineOffset = screenOffset % tStatesPerScanline;
        if (lineOffset < 0 || lineOffset >= 128) {
            return 0;
        }

        return CONTENTION_PATTERN[lineOffset & 0x07];
    }
}
