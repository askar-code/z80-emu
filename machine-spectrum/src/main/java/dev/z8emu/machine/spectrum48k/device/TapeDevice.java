package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.machine.spectrum48k.tape.TapeBlock;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import dev.z8emu.platform.device.TimedDevice;

public final class TapeDevice implements TimedDevice {
    private static final int EAR_BIT_MASK = 0x40;

    private final long cpuClockHz;
    private final boolean stopTapeIf48kModeEnabled;
    private TapeFile tapeFile;
    private boolean playing;
    private int blockIndex;
    private TapeBlockRuntime runtime;
    private long elapsedTStates;

    public TapeDevice() {
        this(3_500_000L, true);
    }

    public TapeDevice(boolean stopTapeIf48kModeEnabled) {
        this(3_500_000L, stopTapeIf48kModeEnabled);
    }

    public TapeDevice(long cpuClockHz, boolean stopTapeIf48kModeEnabled) {
        this.cpuClockHz = cpuClockHz;
        this.stopTapeIf48kModeEnabled = stopTapeIf48kModeEnabled;
    }

    public synchronized void load(TapeFile tapeFile) {
        this.tapeFile = tapeFile;
        rewindInternal();
    }

    public synchronized void stop() {
        playing = false;
    }

    public synchronized void play() {
        if (tapeFile != null && !tapeFile.blocks().isEmpty()) {
            if (runtime == null) {
                rewindInternal();
            }
            playing = true;
        }
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

    public synchronized TapeBlock debugBlock(int index) {
        if (tapeFile == null || index < 0 || index >= tapeFile.blocks().size()) {
            return null;
        }
        return tapeFile.blocks().get(index);
    }

    public synchronized boolean earHigh() {
        return runtime != null && runtime.earHigh();
    }

    public synchronized TapeDebugSnapshot debugSnapshot() {
        if (runtime == null) {
            return new TapeDebugSnapshot(
                    currentBlockIndex(),
                    totalBlocks(),
                    playing,
                    false,
                    "IDLE",
                    0,
                    0,
                    0,
                    0,
                    0,
                    elapsedTStates
            );
        }

        return new TapeDebugSnapshot(
                currentBlockIndex(),
                totalBlocks(),
                playing,
                runtime.earHigh(),
                runtime.stateName(),
                runtime.stateRemaining(),
                runtime.pilotPulsesRemaining(),
                runtime.byteIndex(),
                runtime.bitIndex(),
                runtime.halfPulseIndex(),
                elapsedTStates
        );
    }

    public synchronized int currentBlockIndex() {
        if (tapeFile == null) {
            return 0;
        }
        if (runtime == null) {
            return tapeFile.blocks().isEmpty() ? 0 : tapeFile.blocks().size();
        }
        return blockIndex + 1;
    }

    public synchronized int totalBlocks() {
        return tapeFile == null ? 0 : tapeFile.blocks().size();
    }

    public synchronized boolean isAtEnd() {
        return tapeFile != null && runtime == null && !playing;
    }

    public synchronized int applyEarBitToPortRead(int portValue) {
        boolean earSignalHigh = playing && runtime != null && runtime.earHigh();
        if (earSignalHigh) {
            return portValue ^ EAR_BIT_MASK;
        }
        return portValue;
    }

    @Override
    public synchronized void reset() {
        rewindInternal();
    }

    @Override
    public synchronized void onTStatesElapsed(int tStates) {
        syncToTState(elapsedTStates + tStates);
    }

    public synchronized void syncToTState(long targetTState) {
        if (targetTState <= elapsedTStates) {
            return;
        }

        int delta = (int) Math.min(Integer.MAX_VALUE, targetTState - elapsedTStates);
        elapsedTStates += delta;

        if (!playing || runtime == null) {
            return;
        }

        int remaining = delta;
        while (remaining > 0 && runtime != null) {
            int consumed = runtime.advance(remaining);
            remaining -= consumed;

            if (runtime.finished()) {
                moveToNextBlock();
            }
        }
    }

    private void moveToNextBlock() {
        boolean stopAfterBlock = runtime != null
                && (runtime.stopTapeAfterBlock()
                || (stopTapeIf48kModeEnabled && runtime.stopTapeIf48kMode()));
        boolean nextEarLevel = runtime != null && runtime.earHigh();
        blockIndex++;
        if (tapeFile == null || blockIndex >= tapeFile.blocks().size()) {
            runtime = null;
            playing = false;
            return;
        }
        runtime = new TapeBlockRuntime(tapeFile.blocks().get(blockIndex), nextEarLevel);
        if (stopAfterBlock) {
            playing = false;
        }
    }

    private void rewindInternal() {
        playing = false;
        blockIndex = 0;
        elapsedTStates = 0;
        runtime = tapeFile == null || tapeFile.blocks().isEmpty() ? null : new TapeBlockRuntime(tapeFile.blocks().get(0), false);
    }

    private final class TapeBlockRuntime {
        private static final int STATE_PREFIX = 0;
        private static final int STATE_DATA = 1;
        private static final int STATE_PAUSE = 2;
        private static final int STATE_FINISHED = 3;

        private final TapeBlock block;

        private int state;
        private boolean earHigh;
        private int stateRemaining;
        private int pulseIndex;
        private int bitPosition;
        private int halfPulseIndex;

        TapeBlockRuntime(TapeBlock block, boolean initialEarHigh) {
            this.block = block;
            initializeState(initialEarHigh);
        }

        boolean earHigh() {
            return earHigh;
        }

        String stateName() {
            return switch (state) {
                case STATE_PREFIX -> "PULSES";
                case STATE_DATA -> "DATA";
                case STATE_PAUSE -> "PAUSE";
                case STATE_FINISHED -> "FINISHED";
                default -> "UNKNOWN";
            };
        }

        int stateRemaining() {
            return stateRemaining;
        }

        int pilotPulsesRemaining() {
            if (!block.hasPrefixPulses()) {
                return 0;
            }
            return Math.max(0, block.prefixPulseLengthsTStates().length - pulseIndex);
        }

        int byteIndex() {
            return bitPosition / 8;
        }

        int bitIndex() {
            return bitPosition % 8;
        }

        int halfPulseIndex() {
            return halfPulseIndex;
        }

        boolean stopTapeAfterBlock() {
            return block.stopTapeAfterBlock();
        }

        boolean stopTapeIf48kMode() {
            return block.stopTapeIf48kMode();
        }

        boolean finished() {
            return state == STATE_FINISHED;
        }

        int advance(int availableTStates) {
            if (state == STATE_FINISHED) {
                return availableTStates;
            }

            int consumed = Math.min(availableTStates, stateRemaining);
            stateRemaining -= consumed;

            if (stateRemaining == 0) {
                advanceStateMachine();
            }

            return consumed;
        }

        private void advanceStateMachine() {
            switch (state) {
                case STATE_PREFIX -> {
                    earHigh = !earHigh;
                    pulseIndex++;
                    if (pulseIndex < block.prefixPulseLengthsTStates().length) {
                        stateRemaining = block.prefixPulseLengthsTStates()[pulseIndex];
                    } else if (block.hasData()) {
                        state = STATE_DATA;
                        setupCurrentBitPulse();
                    } else if (block.pauseAfterMillis() > 0) {
                        enterPause();
                    } else {
                        state = STATE_FINISHED;
                    }
                }
                case STATE_DATA -> {
                    earHigh = !earHigh;
                    halfPulseIndex++;
                    if (halfPulseIndex < 2) {
                        stateRemaining = currentBitPulseLength();
                        return;
                    }

                    halfPulseIndex = 0;
                    bitPosition++;
                    if (bitPosition >= block.totalDataBits()) {
                        if (block.pauseAfterMillis() > 0) {
                            enterPause();
                        } else {
                            state = STATE_FINISHED;
                        }
                    } else {
                        setupCurrentBitPulse();
                    }
                }
                case STATE_PAUSE -> state = STATE_FINISHED;
                default -> state = STATE_FINISHED;
            }
        }

        private void setupCurrentBitPulse() {
            stateRemaining = currentBitPulseLength();
        }

        private void enterPause() {
            if (block.pauseAfterMillis() <= 0) {
                state = STATE_FINISHED;
                stateRemaining = 0;
                return;
            }
            state = STATE_PAUSE;
            stateRemaining = pauseDurationTStates(block.pauseAfterMillis());
        }

        private int currentBitPulseLength() {
            return currentBitValue() ? block.oneBitPulseLengthTStates() : block.zeroBitPulseLengthTStates();
        }

        private boolean currentBitValue() {
            int currentByte = block.data()[bitPosition / 8] & 0xFF;
            int shift = 7 - (bitPosition % 8);
            return ((currentByte >>> shift) & 0x01) != 0;
        }

        private int pauseDurationTStates(int pauseMillis) {
            if (pauseMillis <= 0) {
                return 0;
            }
            return (int) Math.round((pauseMillis / 1000.0) * cpuClockHz);
        }

        private void initializeState(boolean initialEarHigh) {
            earHigh = initialEarHigh;
            pulseIndex = 0;
            bitPosition = 0;
            halfPulseIndex = 0;

            if (block.hasPrefixPulses()) {
                state = STATE_PREFIX;
                earHigh = !earHigh;
                stateRemaining = block.prefixPulseLengthsTStates()[0];
                return;
            }

            if (block.hasData()) {
                state = STATE_DATA;
                setupCurrentBitPulse();
                return;
            }

            if (block.pauseAfterMillis() > 0) {
                enterPause();
                return;
            }

            state = STATE_FINISHED;
            stateRemaining = 0;
        }
    }

    public record TapeDebugSnapshot(
            int currentBlockIndex,
            int totalBlocks,
            boolean playing,
            boolean earHigh,
            String state,
            int stateRemaining,
            int pilotPulsesRemaining,
            int byteIndex,
            int bitIndex,
            int halfPulseIndex,
            long elapsedTStates
    ) {
    }
}
