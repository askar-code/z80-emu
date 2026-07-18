package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.machine.spectrum48k.tape.TapeBlock;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import dev.z8emu.platform.device.TimedDevice;
import java.util.Objects;

public final class TapeDevice implements TimedDevice {
    private static final int EAR_BIT_MASK = 0x40;
    // TZX and TAP pulse durations are defined in ticks of a 3.5 MHz reference clock.
    private static final long TAPE_TIMING_REFERENCE_HZ = 3_500_000L;

    private final long cpuClockHz;
    private final boolean stopTapeIf48kModeEnabled;
    private TapeFile tapeFile;
    private boolean playing;
    private int blockIndex;
    private TapeBlockRuntime runtime;
    private long elapsedTStates;
    private long pulseScaleReferenceHz;
    private long pulseScaleRemainder;

    public TapeDevice(long cpuClockHz, boolean stopTapeIf48kModeEnabled) {
        if (cpuClockHz <= 0) {
            throw new IllegalArgumentException("cpuClockHz must be positive");
        }
        this.cpuClockHz = cpuClockHz;
        this.stopTapeIf48kModeEnabled = stopTapeIf48kModeEnabled;
    }

    public synchronized void load(TapeFile tapeFile) {
        this.tapeFile = Objects.requireNonNull(tapeFile, "tapeFile");
        rewindPosition();
    }

    /**
     * Removes the current tape without changing the device's absolute machine clock.
     * Playback stops immediately and the EAR source becomes inactive.
     */
    public synchronized void eject() {
        tapeFile = null;
        playing = false;
        blockIndex = 0;
        runtime = null;
        resetPulseScaling();
    }

    public synchronized void stop() {
        playing = false;
    }

    public synchronized void play() {
        if (tapeFile != null && !tapeFile.blocks().isEmpty()) {
            if (runtime == null) {
                rewindPosition();
            }
            playing = true;
        }
    }

    public synchronized void rewind() {
        rewindPosition();
    }

    /**
     * Positions the stopped tape at a zero-based block index. The next play starts
     * at the beginning of that block and the absolute machine clock is preserved.
     */
    public synchronized void selectBlock(int zeroBasedBlockIndex) {
        if (tapeFile == null) {
            throw new IllegalStateException("No tape is loaded");
        }
        if (zeroBasedBlockIndex < 0 || zeroBasedBlockIndex >= tapeFile.blocks().size()) {
            throw new IllegalArgumentException(
                    "Tape block index out of range: " + zeroBasedBlockIndex
                            + " (total " + tapeFile.blocks().size() + ")"
            );
        }

        playing = false;
        blockIndex = zeroBasedBlockIndex;
        resetPulseScaling();
        runtime = new TapeBlockRuntime(tapeFile.blocks().get(blockIndex), false, false);
    }

    public synchronized boolean isPlaying() {
        return playing;
    }

    public synchronized boolean isLoaded() {
        return tapeFile != null;
    }

    public synchronized boolean earHigh() {
        return runtime != null && runtime.earHigh();
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

    /**
     * Returns the zero-based next/current block position, or the block count at EOF.
     * An unloaded or empty tape reports zero.
     */
    public synchronized int blockPosition() {
        return tapeFile == null ? 0 : blockIndex;
    }

    public synchronized boolean isAtEnd() {
        return tapeFile != null && runtime == null && !playing;
    }

    public synchronized TransportStatus transportStatus() {
        return new TransportStatus(
                tapeFile != null,
                playing,
                tapeFile != null && runtime == null && !playing,
                currentBlockIndex(),
                tapeFile == null ? 0 : tapeFile.blocks().size(),
                runtime != null && runtime.earHigh()
        );
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
        elapsedTStates = 0;
        rewindPosition();
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
        while (remaining > 0 && playing && runtime != null) {
            int consumed = runtime.advance(remaining);
            remaining -= consumed;

            if (runtime.finished()) {
                moveToNextBlock();
            }
        }
    }

    public synchronized boolean syncAndReadEarLevel(long targetTState) {
        syncToTState(targetTState);
        return playing && runtime != null && runtime.earHigh();
    }

    private void moveToNextBlock() {
        boolean stopAfterBlock = runtime != null
                && (runtime.stopTapeAfterBlock()
                || (stopTapeIf48kModeEnabled && runtime.stopTapeIf48kMode()));
        boolean nextEarLevel = runtime != null && runtime.earHigh();
        boolean nextLevelNeedsPauseEdge = runtime != null && runtime.rawSignalLevelNeedsPauseEdge();
        blockIndex++;
        if (tapeFile == null || blockIndex >= tapeFile.blocks().size()) {
            runtime = null;
            playing = false;
            return;
        }
        runtime = new TapeBlockRuntime(
                tapeFile.blocks().get(blockIndex),
                nextEarLevel,
                nextLevelNeedsPauseEdge
        );
        if (stopAfterBlock) {
            playing = false;
        }
    }

    private void rewindPosition() {
        playing = false;
        blockIndex = 0;
        resetPulseScaling();
        runtime = tapeFile == null || tapeFile.blocks().isEmpty()
                ? null
                : new TapeBlockRuntime(tapeFile.blocks().get(0), false, false);
    }

    private void resetPulseScaling() {
        pulseScaleReferenceHz = TAPE_TIMING_REFERENCE_HZ;
        pulseScaleRemainder = TAPE_TIMING_REFERENCE_HZ / 2;
    }

    private long scalePulseLength(long sourceTicks, long referenceHz) {
        if (sourceTicks < 0 || referenceHz <= 0) {
            throw new IllegalArgumentException("Tape timing values must be non-negative and use a positive clock");
        }
        if (pulseScaleReferenceHz != referenceHz) {
            pulseScaleReferenceHz = referenceHz;
            pulseScaleRemainder = referenceHz / 2;
        }

        // Split the calculation to keep large direct-recording runs away from long multiplication overflow.
        long wholeSeconds = sourceTicks / referenceHz;
        long fractionalTicks = sourceTicks % referenceHz;
        long fractionalNumerator = Math.addExact(
                Math.multiplyExact(fractionalTicks, cpuClockHz),
                pulseScaleRemainder
        );
        long scaledTStates = Math.addExact(
                Math.multiplyExact(wholeSeconds, cpuClockHz),
                fractionalNumerator / referenceHz
        );
        pulseScaleRemainder = fractionalNumerator % referenceHz;
        return scaledTStates;
    }

    public record TransportStatus(
            boolean loaded,
            boolean playing,
            boolean atEnd,
            int currentBlockIndex,
            int totalBlocks,
            boolean earHigh
    ) {
    }

    private final class TapeBlockRuntime {
        private static final int STATE_PREFIX = 0;
        private static final int STATE_DATA = 1;
        private static final int STATE_DIRECT = 2;
        private static final int STATE_CSW = 3;
        private static final int STATE_PAUSE_LEAD_IN = 4;
        private static final int STATE_PAUSE_LOW = 5;
        private static final int STATE_FINISHED = 6;

        private final TapeBlock block;

        private int state;
        private boolean earHigh;
        private long stateRemaining;
        private int pulseIndex;
        private int bitPosition;
        private int halfPulseIndex;
        private int directSampleIndex;
        private int directRunEndSampleIndex;
        private long pauseLowRemaining;
        private boolean rawSignalLevelNeedsPauseEdge;

        TapeBlockRuntime(
                TapeBlock block,
                boolean initialEarHigh,
                boolean initialLevelNeedsPauseEdge
        ) {
            this.block = block;
            initializeState(initialEarHigh, initialLevelNeedsPauseEdge);
        }

        boolean earHigh() {
            return earHigh;
        }

        boolean stopTapeAfterBlock() {
            return block.stopTapeAfterBlock();
        }

        boolean stopTapeIf48kMode() {
            return block.stopTapeIf48kMode();
        }

        boolean rawSignalLevelNeedsPauseEdge() {
            return rawSignalLevelNeedsPauseEdge;
        }

        boolean finished() {
            return state == STATE_FINISHED;
        }

        int advance(int availableTStates) {
            if (state == STATE_FINISHED) {
                return 0;
            }

            int consumed = (int) Math.min((long) availableTStates, stateRemaining);
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
                        stateRemaining = scalePulseLength(
                                block.prefixPulseLengthsTStates()[pulseIndex],
                                TAPE_TIMING_REFERENCE_HZ
                        );
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
                case STATE_DIRECT -> advanceDirectRecording();
                case STATE_CSW -> advanceCswRecording();
                case STATE_PAUSE_LEAD_IN -> {
                    earHigh = false;
                    if (pauseLowRemaining > 0) {
                        state = STATE_PAUSE_LOW;
                        stateRemaining = pauseLowRemaining;
                        pauseLowRemaining = 0;
                    } else {
                        state = STATE_FINISHED;
                    }
                }
                case STATE_PAUSE_LOW -> {
                    earHigh = false;
                    state = STATE_FINISHED;
                }
                default -> state = STATE_FINISHED;
            }
        }

        private void setupCurrentBitPulse() {
            stateRemaining = currentBitPulseLength();
        }

        private void setupDirectRun(TapeBlock.DirectRecording recording) {
            boolean runLevel = recording.sampleHigh(directSampleIndex);
            directRunEndSampleIndex = directSampleIndex + 1;
            while (directRunEndSampleIndex < recording.totalSamples()
                    && recording.sampleHigh(directRunEndSampleIndex) == runLevel) {
                directRunEndSampleIndex++;
            }
            long runSamples = directRunEndSampleIndex - directSampleIndex;
            long runReferenceTStates = Math.multiplyExact(runSamples, recording.tStatesPerSample());
            stateRemaining = scalePulseLength(runReferenceTStates, TAPE_TIMING_REFERENCE_HZ);
        }

        private void advanceDirectRecording() {
            TapeBlock.DirectRecording recording = (TapeBlock.DirectRecording) block.rawSignal();
            directSampleIndex = directRunEndSampleIndex;
            if (directSampleIndex < recording.totalSamples()) {
                earHigh = recording.sampleHigh(directSampleIndex);
                setupDirectRun(recording);
                return;
            }
            finishRawSignal();
        }

        private void setupCswPulse(TapeBlock.CswRecording recording) {
            stateRemaining = scalePulseLength(
                    recording.pulseLengthInSamples(pulseIndex),
                    recording.samplingRateHz()
            );
        }

        private void advanceCswRecording() {
            TapeBlock.CswRecording recording = (TapeBlock.CswRecording) block.rawSignal();
            pulseIndex++;
            if (pulseIndex < recording.pulseCount()) {
                earHigh = !earHigh;
                setupCswPulse(recording);
                return;
            }
            finishRawSignal();
        }

        private void finishRawSignal() {
            if (block.pauseAfterMillis() > 0) {
                // Direct and CSW leave the current level at the last level played. A following pause starts opposite.
                earHigh = !earHigh;
                rawSignalLevelNeedsPauseEdge = false;
                enterPause();
            } else {
                rawSignalLevelNeedsPauseEdge = true;
                state = STATE_FINISHED;
                stateRemaining = 0;
            }
        }

        private void enterPause() {
            if (block.pauseAfterMillis() <= 0) {
                state = STATE_FINISHED;
                stateRemaining = 0;
                return;
            }
            long pauseDuration = pauseDurationTStates(block.pauseAfterMillis());
            long leadInDuration = Math.min(pauseDuration, pauseDurationTStates(1));
            // The final pulse has already changed to its terminating level; hold it for 1 ms, then settle LOW.
            state = STATE_PAUSE_LEAD_IN;
            stateRemaining = leadInDuration;
            pauseLowRemaining = pauseDuration - leadInDuration;
        }

        private long currentBitPulseLength() {
            int referenceTStates = currentBitValue()
                    ? block.oneBitPulseLengthTStates()
                    : block.zeroBitPulseLengthTStates();
            return scalePulseLength(referenceTStates, TAPE_TIMING_REFERENCE_HZ);
        }

        private boolean currentBitValue() {
            int currentByte = block.data()[bitPosition / 8] & 0xFF;
            int shift = 7 - (bitPosition % 8);
            return ((currentByte >>> shift) & 0x01) != 0;
        }

        private long pauseDurationTStates(int pauseMillis) {
            if (pauseMillis <= 0) {
                return 0;
            }
            long numerator = (pauseMillis * cpuClockHz) + 500;
            return numerator / 1_000;
        }

        private void initializeState(boolean initialEarHigh, boolean initialLevelNeedsPauseEdge) {
            earHigh = initialEarHigh;
            rawSignalLevelNeedsPauseEdge = initialLevelNeedsPauseEdge;
            pulseIndex = 0;
            bitPosition = 0;
            halfPulseIndex = 0;
            directSampleIndex = 0;
            directRunEndSampleIndex = 0;
            pauseLowRemaining = 0;

            if (block.setsSignalLevel()) {
                earHigh = block.signalLevel() == TapeBlock.SignalLevel.HIGH;
                rawSignalLevelNeedsPauseEdge = false;
                state = STATE_FINISHED;
                stateRemaining = 0;
                return;
            }

            if (block.rawSignal() instanceof TapeBlock.DirectRecording recording) {
                if (recording.totalSamples() > 0) {
                    rawSignalLevelNeedsPauseEdge = false;
                    state = STATE_DIRECT;
                    earHigh = recording.sampleHigh(0);
                    setupDirectRun(recording);
                } else if (block.pauseAfterMillis() > 0) {
                    startStandalonePause();
                } else {
                    state = STATE_FINISHED;
                    stateRemaining = 0;
                }
                return;
            }

            if (block.rawSignal() instanceof TapeBlock.CswRecording recording) {
                if (recording.pulseCount() > 0) {
                    rawSignalLevelNeedsPauseEdge = false;
                    state = STATE_CSW;
                    setupCswPulse(recording);
                } else if (block.pauseAfterMillis() > 0) {
                    startStandalonePause();
                } else {
                    state = STATE_FINISHED;
                    stateRemaining = 0;
                }
                return;
            }

            if (block.hasPrefixPulses()) {
                rawSignalLevelNeedsPauseEdge = false;
                state = STATE_PREFIX;
                earHigh = !earHigh;
                stateRemaining = scalePulseLength(
                        block.prefixPulseLengthsTStates()[0],
                        TAPE_TIMING_REFERENCE_HZ
                );
                return;
            }

            if (block.hasData()) {
                rawSignalLevelNeedsPauseEdge = false;
                state = STATE_DATA;
                setupCurrentBitPulse();
                return;
            }

            if (block.pauseAfterMillis() > 0) {
                startStandalonePause();
                return;
            }

            state = STATE_FINISHED;
            stateRemaining = 0;
        }

        private void startStandalonePause() {
            if (rawSignalLevelNeedsPauseEdge) {
                earHigh = !earHigh;
            }
            rawSignalLevelNeedsPauseEdge = false;
            enterPause();
        }
    }

}
