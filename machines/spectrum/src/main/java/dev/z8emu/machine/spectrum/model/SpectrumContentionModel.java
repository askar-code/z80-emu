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
        int highByte = (port >>> 8) & 0xFF;
        boolean highByteContended = highByte >= 0x40 && highByte <= 0x7F;
        return ioPortDelay(currentTState, phaseTStates, port, highByteContended);
    }

    public int ioPortDelay(
            long currentTState,
            int phaseTStates,
            int port,
            boolean highByteContended
    ) {
        boolean lowBitReset = (port & 0x0001) == 0;
        if (!lowBitReset && !highByteContended) {
            return 0;
        }

        long cycleStart = currentTState + Math.max(0, phaseTStates);
        int delay = 0;

        // A contended high address performs a C:1 early phase. The ULA wait
        // stretches that phase, so the following contention point must use
        // the already-delayed time rather than the original cycle offsets.
        if (highByteContended) {
            delay = contentionDelayAt(cycleStart);
        }

        // An even (ULA-decoded) port performs one late contention check and
        // then two free t-states.
        if (lowBitReset) {
            delay += contentionDelayAt(cycleStart + 1L + delay);
            return delay;
        }

        // An odd port whose high address is contended performs three distinct
        // late checks. Each intervening t-state, including all earlier waits,
        // shifts the next point on the contention pattern.
        if (highByteContended) {
            for (int nominalOffset = 1; nominalOffset <= 3; nominalOffset++) {
                delay += contentionDelayAt(cycleStart + nominalOffset + delay);
            }
        }
        return delay;
    }

    public int memoryDelay(long currentTState, int phaseTStates, boolean contended) {
        if (!contended) {
            return 0;
        }
        return contentionDelayAt(currentTState + Math.max(0, phaseTStates));
    }

    public int internalMemoryDelay(
            long currentTState,
            int phaseTStates,
            boolean contended,
            int tStates
    ) {
        if (!contended || tStates <= 0) {
            return 0;
        }

        long cycleStart = currentTState + Math.max(0, phaseTStates);
        int delay = 0;
        for (int nominalOffset = 0; nominalOffset < tStates; nominalOffset++) {
            delay += contentionDelayAt(cycleStart + nominalOffset + delay);
        }
        return delay;
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
