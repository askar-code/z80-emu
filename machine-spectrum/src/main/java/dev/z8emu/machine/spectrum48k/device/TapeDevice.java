package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.machine.spectrum48k.tape.TapeBlock;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import dev.z8emu.platform.device.TimedDevice;
import java.util.Arrays;

public final class TapeDevice implements TimedDevice {
    private static final int EAR_BIT_MASK = 0x40;
    private static final boolean TOGGLE_EAR_AT_DATA_BLOCK_START =
            Boolean.getBoolean("z8emu.tapeDataBlockInitialEdge")
                    && !Boolean.getBoolean("z8emu.tapeDataBlockNoInitialEdge");
    private static final double TSTATE_SCALE = Double.parseDouble(System.getProperty("z8emu.tapeTStateScale", "1.0"));
    private static final boolean INVERT_EAR_INPUT = Boolean.getBoolean("z8emu.invertTapeEar");
    private static final int MAX_PAUSE_MS = Integer.getInteger("z8emu.maxTapePauseMs", Integer.MAX_VALUE);
    private static final boolean SKIP_DATA_BLOCK_PAUSES = Boolean.getBoolean("z8emu.skipDataBlockPauses");

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
        rewindInternal(0);
    }

    public synchronized void stop() {
        playing = false;
    }

    public synchronized void play() {
        if (tapeFile != null && !tapeFile.blocks().isEmpty()) {
            if (runtime == null) {
                rewindInternal(0);
            }
            playing = true;
        }
    }

    public synchronized void rewind() {
        rewindInternal(0);
    }

    public synchronized boolean isPlaying() {
        return playing;
    }

    public synchronized boolean isLoaded() {
        return tapeFile != null;
    }

    public synchronized byte[] currentBlockData() {
        if (tapeFile == null || runtime == null) {
            return null;
        }
        return Arrays.copyOf(tapeFile.blocks().get(blockIndex).data(), tapeFile.blocks().get(blockIndex).data().length);
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

    public synchronized void advanceToNextBlock() {
        if (runtime != null) {
            moveToNextBlock();
        }
    }

    public synchronized int applyEarBitToPortRead(int portValue) {
        boolean earSignalHigh = playing && runtime != null && runtime.earHigh();
        if (INVERT_EAR_INPUT) {
            earSignalHigh = !earSignalHigh;
        }
        if (earSignalHigh) {
            return portValue ^ EAR_BIT_MASK;
        }
        return portValue;
    }

    @Override
    public synchronized void reset() {
        rewindInternal(0);
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

    private void rewindInternal(long timelineTState) {
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
                        stateRemaining = scaleDurationTStates(block.prefixPulseLengthsTStates()[pulseIndex]);
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
            stateRemaining = scaleDurationTStates(currentBitPulseLength());
        }

        private void enterPause() {
            if (SKIP_DATA_BLOCK_PAUSES && block.hasData() && isCustomTimedDataBlock()) {
                state = STATE_FINISHED;
                stateRemaining = 0;
                return;
            }
            int effectivePauseMillis = effectivePauseMillis(block.pauseAfterMillis());
            if (effectivePauseMillis <= 0) {
                state = STATE_FINISHED;
                stateRemaining = 0;
                return;
            }
            state = STATE_PAUSE;
            stateRemaining = pauseDurationTStates(effectivePauseMillis);
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
            return scaleDurationTStates((int) Math.round((pauseMillis / 1000.0) * cpuClockHz));
        }

        private void initializeState(boolean initialEarHigh) {
            earHigh = initialEarHigh;
            pulseIndex = 0;
            bitPosition = 0;
            halfPulseIndex = 0;

            if (block.hasPrefixPulses()) {
                state = STATE_PREFIX;
                earHigh = !earHigh;
                stateRemaining = scaleDurationTStates(block.prefixPulseLengthsTStates()[0]);
                return;
            }

            if (block.hasData()) {
                state = STATE_DATA;
                if (TOGGLE_EAR_AT_DATA_BLOCK_START) {
                    earHigh = !earHigh;
                }
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

        private int scaleDurationTStates(int durationTStates) {
            if (durationTStates <= 0) {
                return durationTStates;
            }
            return Math.max(1, (int) Math.round(durationTStates * TSTATE_SCALE));
        }

        private boolean isCustomTimedDataBlock() {
            return block.zeroBitPulseLengthTStates() != 855 || block.oneBitPulseLengthTStates() != 1_710;
        }

        private int effectivePauseMillis(int pauseMillis) {
            return Math.min(pauseMillis, MAX_PAUSE_MS);
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
