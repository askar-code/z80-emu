package dev.z8emu.machine.spectrum48k.tape;

import java.util.Arrays;

public record TapeBlock(
        int[] prefixPulseLengthsTStates,
        int zeroBitPulseLengthTStates,
        int oneBitPulseLengthTStates,
        int usedBitsInLastByte,
        int pauseAfterMillis,
        boolean stopTapeAfterBlock,
        boolean stopTapeIf48kMode,
        SignalLevel signalLevel,
        RawSignal rawSignal,
        byte[] data
) {
    public enum SignalLevel {
        UNCHANGED,
        LOW,
        HIGH
    }

    /** A level-sensitive recording which cannot be represented by the normal toggle-pulse data fields. */
    public sealed interface RawSignal permits NoRawSignal, DirectRecording, CswRecording {
    }

    public enum NoRawSignal implements RawSignal {
        INSTANCE
    }

    /** TZX 0x15: every bit is an explicit EAR level held for {@code tStatesPerSample}. */
    public record DirectRecording(
            int tStatesPerSample,
            int usedBitsInLastByte,
            byte[] samples
    ) implements RawSignal {
        public DirectRecording {
            if (tStatesPerSample <= 0) {
                throw new IllegalArgumentException("tStatesPerSample must be positive");
            }
            if (usedBitsInLastByte < 1 || usedBitsInLastByte > 8) {
                throw new IllegalArgumentException("usedBitsInLastByte must be in range 1..8");
            }
            samples = samples == null ? new byte[0] : Arrays.copyOf(samples, samples.length);
        }

        @Override
        public byte[] samples() {
            return Arrays.copyOf(samples, samples.length);
        }

        public int totalSamples() {
            if (samples.length == 0) {
                return 0;
            }
            return ((samples.length - 1) * 8) + usedBitsInLastByte;
        }

        public boolean sampleHigh(int sampleIndex) {
            if (sampleIndex < 0 || sampleIndex >= totalSamples()) {
                throw new IndexOutOfBoundsException(sampleIndex);
            }
            int currentByte = samples[sampleIndex / 8] & 0xFF;
            int shift = 7 - (sampleIndex % 8);
            return ((currentByte >>> shift) & 0x01) != 0;
        }
    }

    /** TZX 0x18: alternating pulse lengths expressed in ticks of the source sampling rate. */
    public record CswRecording(
            int samplingRateHz,
            int[] pulseLengthsInSamples
    ) implements RawSignal {
        public CswRecording {
            if (samplingRateHz <= 0) {
                throw new IllegalArgumentException("samplingRateHz must be positive");
            }
            pulseLengthsInSamples = pulseLengthsInSamples == null
                    ? new int[0]
                    : Arrays.copyOf(pulseLengthsInSamples, pulseLengthsInSamples.length);
            for (int pulseLength : pulseLengthsInSamples) {
                if (pulseLength <= 0) {
                    throw new IllegalArgumentException("CSW pulse lengths must be positive");
                }
            }
        }

        @Override
        public int[] pulseLengthsInSamples() {
            return Arrays.copyOf(pulseLengthsInSamples, pulseLengthsInSamples.length);
        }

        public int pulseCount() {
            return pulseLengthsInSamples.length;
        }

        public int pulseLengthInSamples(int pulseIndex) {
            return pulseLengthsInSamples[pulseIndex];
        }
    }

    public TapeBlock {
        prefixPulseLengthsTStates = prefixPulseLengthsTStates == null
                ? new int[0]
                : Arrays.copyOf(prefixPulseLengthsTStates, prefixPulseLengthsTStates.length);
        signalLevel = signalLevel == null ? SignalLevel.UNCHANGED : signalLevel;
        rawSignal = rawSignal == null ? NoRawSignal.INSTANCE : rawSignal;
        data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);

        if (usedBitsInLastByte < 0 || usedBitsInLastByte > 8) {
            throw new IllegalArgumentException("usedBitsInLastByte must be in range 0..8");
        }
        if (data.length > 0 && usedBitsInLastByte == 0) {
            throw new IllegalArgumentException("usedBitsInLastByte must be non-zero when data is present");
        }
        if (!(rawSignal instanceof NoRawSignal)
                && (prefixPulseLengthsTStates.length > 0 || data.length > 0)) {
            throw new IllegalArgumentException("raw signal blocks cannot also contain pulse-encoded data");
        }
    }

    public static TapeBlock dataBlock(
            int[] prefixPulseLengthsTStates,
            int zeroBitPulseLengthTStates,
            int oneBitPulseLengthTStates,
            int usedBitsInLastByte,
            int pauseAfterMillis,
            byte[] data
    ) {
        return new TapeBlock(
                prefixPulseLengthsTStates,
                zeroBitPulseLengthTStates,
                oneBitPulseLengthTStates,
                usedBitsInLastByte,
                pauseAfterMillis,
                false,
                false,
                SignalLevel.UNCHANGED,
                NoRawSignal.INSTANCE,
                data
        );
    }

    public static TapeBlock directRecordingBlock(
            int tStatesPerSample,
            int usedBitsInLastByte,
            int pauseAfterMillis,
            byte[] samples
    ) {
        return new TapeBlock(
                new int[0],
                0,
                0,
                0,
                pauseAfterMillis,
                false,
                false,
                SignalLevel.UNCHANGED,
                new DirectRecording(tStatesPerSample, usedBitsInLastByte, samples),
                new byte[0]
        );
    }

    public static TapeBlock cswRecordingBlock(
            int samplingRateHz,
            int[] pulseLengthsInSamples,
            int pauseAfterMillis
    ) {
        return new TapeBlock(
                new int[0],
                0,
                0,
                0,
                pauseAfterMillis,
                false,
                false,
                SignalLevel.UNCHANGED,
                new CswRecording(samplingRateHz, pulseLengthsInSamples),
                new byte[0]
        );
    }

    public static TapeBlock pauseBlock(int pauseAfterMillis, boolean stopTapeAfterBlock) {
        return new TapeBlock(
                new int[0],
                0,
                0,
                0,
                pauseAfterMillis,
                stopTapeAfterBlock,
                false,
                SignalLevel.UNCHANGED,
                NoRawSignal.INSTANCE,
                new byte[0]
        );
    }

    public static TapeBlock stopTapeIf48kModeBlock() {
        return new TapeBlock(
                new int[0],
                0,
                0,
                0,
                0,
                false,
                true,
                SignalLevel.UNCHANGED,
                NoRawSignal.INSTANCE,
                new byte[0]
        );
    }

    public static TapeBlock signalLevelBlock(boolean high) {
        return new TapeBlock(
                new int[0],
                0,
                0,
                0,
                0,
                false,
                false,
                high ? SignalLevel.HIGH : SignalLevel.LOW,
                NoRawSignal.INSTANCE,
                new byte[0]
        );
    }

    public boolean hasPrefixPulses() {
        return prefixPulseLengthsTStates.length > 0;
    }

    public boolean hasData() {
        return data.length > 0;
    }

    public boolean setsSignalLevel() {
        return signalLevel != SignalLevel.UNCHANGED;
    }

    public boolean hasRawSignal() {
        return !(rawSignal instanceof NoRawSignal);
    }

    public int totalDataBits() {
        if (data.length == 0) {
            return 0;
        }
        return ((data.length - 1) * 8) + usedBitsInLastByte;
    }
}
