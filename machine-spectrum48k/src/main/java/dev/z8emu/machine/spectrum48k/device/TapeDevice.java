package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.machine.spectrum48k.tape.TapeBlock;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import dev.z8emu.platform.device.TimedDevice;
import java.util.Arrays;

public final class TapeDevice implements TimedDevice {
    private static final int EAR_BIT_MASK = 0x40;
    private static final int INITIAL_EAR_ON_PORT_FE = 0xBF;

    private TapeFile tapeFile;
    private boolean playing;
    private int blockIndex;
    private TapeBlockRuntime runtime;
    private long elapsedTStates;

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

    public synchronized byte[] currentBlockData() {
        if (tapeFile == null || runtime == null) {
            return null;
        }
        return Arrays.copyOf(tapeFile.blocks().get(blockIndex).data(), tapeFile.blocks().get(blockIndex).data().length);
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
        if (runtime != null && runtime.earHigh()) {
            return portValue & ~EAR_BIT_MASK;
        }
        return portValue | EAR_BIT_MASK;
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
        blockIndex++;
        if (tapeFile == null || blockIndex >= tapeFile.blocks().size()) {
            runtime = null;
            playing = false;
            return;
        }
        runtime = new TapeBlockRuntime(tapeFile.blocks().get(blockIndex));
    }

    private void rewindInternal() {
        playing = false;
        blockIndex = 0;
        elapsedTStates = 0;
        runtime = tapeFile == null || tapeFile.blocks().isEmpty() ? null : new TapeBlockRuntime(tapeFile.blocks().get(0));
    }

    private static final class TapeBlockRuntime {
        private static final int STATE_PILOT = 0;
        private static final int STATE_SYNC_1 = 1;
        private static final int STATE_SYNC_2 = 2;
        private static final int STATE_DATA = 3;
        private static final int STATE_PAUSE = 4;
        private static final int STATE_FINISHED = 5;

        private final TapeBlock block;

        private int state = STATE_PILOT;
        private boolean earHigh;
        private int stateRemaining;
        private int pilotPulsesRemaining;
        private int byteIndex;
        private int bitIndex;
        private int halfPulseIndex;

        TapeBlockRuntime(TapeBlock block) {
            this.block = block;
            this.earHigh = false;
            this.pilotPulsesRemaining = block.pilotTonePulses();
            this.stateRemaining = block.pilotPulseLengthTStates();
        }

        boolean earHigh() {
            return earHigh;
        }

        String stateName() {
            return switch (state) {
                case STATE_PILOT -> "PILOT";
                case STATE_SYNC_1 -> "SYNC1";
                case STATE_SYNC_2 -> "SYNC2";
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
            return pilotPulsesRemaining;
        }

        int byteIndex() {
            return byteIndex;
        }

        int bitIndex() {
            return bitIndex;
        }

        int halfPulseIndex() {
            return halfPulseIndex;
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
                case STATE_PILOT -> {
                    earHigh = !earHigh;
                    pilotPulsesRemaining--;
                    if (pilotPulsesRemaining > 0) {
                        stateRemaining = block.pilotPulseLengthTStates();
                    } else {
                        state = STATE_SYNC_1;
                        stateRemaining = block.syncFirstPulseLengthTStates();
                    }
                }
                case STATE_SYNC_1 -> {
                    earHigh = !earHigh;
                    state = STATE_SYNC_2;
                    stateRemaining = block.syncSecondPulseLengthTStates();
                }
                case STATE_SYNC_2 -> {
                    earHigh = !earHigh;
                    state = STATE_DATA;
                    setupCurrentBitPulse();
                }
                case STATE_DATA -> {
                    earHigh = !earHigh;
                    halfPulseIndex++;
                    if (halfPulseIndex < 2) {
                        stateRemaining = currentBitPulseLength();
                        return;
                    }

                    halfPulseIndex = 0;
                    bitIndex++;
                    if (bitIndex >= 8) {
                        bitIndex = 0;
                        byteIndex++;
                    }

                    if (byteIndex >= block.data().length) {
                        if (block.pauseAfterMillis() > 0) {
                            state = STATE_PAUSE;
                            earHigh = false;
                            stateRemaining = pauseDurationTStates(block.pauseAfterMillis());
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

        private int currentBitPulseLength() {
            return currentBitValue() ? block.oneBitPulseLengthTStates() : block.zeroBitPulseLengthTStates();
        }

        private boolean currentBitValue() {
            int currentByte = block.data()[byteIndex] & 0xFF;
            int shift = 7 - bitIndex;
            return ((currentByte >>> shift) & 0x01) != 0;
        }

        private int pauseDurationTStates(int pauseMillis) {
            return (int) ((pauseMillis / 1000.0) * 3_500_000);
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
