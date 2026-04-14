package dev.z8emu.machine.radio86rk.device;

import dev.z8emu.platform.device.TimedDevice;

public final class Radio86AudioDevice implements TimedDevice {
    public static final int SAMPLE_RATE = 44_100;

    private static final boolean MONITOR_TAPE_OUTPUT = Boolean.getBoolean("z8emu.radioTapeMonitorAudio");
    private static final int CPU_CLOCK_HZ = 16_000_000 / 9;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int BUFFER_CAPACITY = SAMPLE_RATE * BYTES_PER_SAMPLE / 5;
    private static final float SPEAKER_LEVEL_HIGH = 9_000.0f;
    private static final float TAPE_OUTPUT_LEVEL = 3_000.0f;
    private static final float HIGH_PASS_ALPHA = 0.995f;

    private final byte[] audioBuffer = new byte[BUFFER_CAPACITY];

    private boolean speakerHigh;
    private boolean tapeOutputHigh;
    private long sampleRemainder;
    private int readIndex;
    private int writeIndex;
    private int bufferedBytes;
    private float previousInputLevel;
    private float filteredLevel;

    public synchronized void setSpeakerHigh(boolean speakerHigh) {
        this.speakerHigh = speakerHigh;
    }

    public synchronized void setTapeOutputHigh(boolean tapeOutputHigh) {
        this.tapeOutputHigh = tapeOutputHigh;
    }

    public synchronized int drainAudio(byte[] target, int offset, int length) {
        int copied = Math.min(length, bufferedBytes);
        for (int i = 0; i < copied; i++) {
            target[offset + i] = audioBuffer[readIndex];
            readIndex = (readIndex + 1) % audioBuffer.length;
        }
        bufferedBytes -= copied;
        return copied;
    }

    public synchronized int availableAudioBytes() {
        return bufferedBytes;
    }

    @Override
    public synchronized void reset() {
        speakerHigh = false;
        tapeOutputHigh = false;
        sampleRemainder = 0;
        readIndex = 0;
        writeIndex = 0;
        bufferedBytes = 0;
        previousInputLevel = 0.0f;
        filteredLevel = 0.0f;
    }

    @Override
    public synchronized void onTStatesElapsed(int tStates) {
        long total = sampleRemainder + ((long) tStates * SAMPLE_RATE);
        int samplesToGenerate = (int) (total / CPU_CLOCK_HZ);
        sampleRemainder = total % CPU_CLOCK_HZ;

        for (int i = 0; i < samplesToGenerate; i++) {
            writeSample(nextSample());
        }
    }

    private short nextSample() {
        float inputLevel = mixedInputLevel();
        filteredLevel = HIGH_PASS_ALPHA * (filteredLevel + inputLevel - previousInputLevel);
        previousInputLevel = inputLevel;

        int sample = Math.round(filteredLevel);
        if (sample > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (sample < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) sample;
    }

    private float mixedInputLevel() {
        float mixed = speakerHigh ? SPEAKER_LEVEL_HIGH : 0.0f;
        if (MONITOR_TAPE_OUTPUT && tapeOutputHigh) {
            mixed += TAPE_OUTPUT_LEVEL;
        }
        return mixed;
    }

    private void writeSample(short sample) {
        writeByte((byte) (sample & 0xFF));
        writeByte((byte) ((sample >>> 8) & 0xFF));
    }

    private void writeByte(byte value) {
        if (bufferedBytes == audioBuffer.length) {
            readIndex = (readIndex + 1) % audioBuffer.length;
            bufferedBytes--;
        }

        audioBuffer[writeIndex] = value;
        writeIndex = (writeIndex + 1) % audioBuffer.length;
        bufferedBytes++;
    }
}
