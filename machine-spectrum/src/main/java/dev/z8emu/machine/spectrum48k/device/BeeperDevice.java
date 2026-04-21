package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.device.TimedDevice;

public final class BeeperDevice implements TimedDevice, PcmMonoSource {
    public static final int SAMPLE_RATE = 44_100;

    private static final int CPU_CLOCK_HZ = 3_500_000;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int BUFFER_CAPACITY = SAMPLE_RATE * BYTES_PER_SAMPLE / 5;
    private static final short LEVEL_EAR_1_MIC_1 = 12_000;
    private static final short LEVEL_EAR_1_MIC_0 = 11_000;
    private static final short LEVEL_EAR_0_MIC_1 = -8_000;
    private static final short LEVEL_EAR_0_MIC_0 = -12_000;
    private static final short TAPE_INPUT_LEVEL = 5_000;

    private boolean micHigh;
    private boolean earHigh;
    private boolean tapeInputHigh;
    private long sampleRemainder;
    private int lastPortFeValue = 0x00;

    private final byte[] audioBuffer = new byte[BUFFER_CAPACITY];
    private int readIndex;
    private int writeIndex;
    private int bufferedBytes;

    public synchronized void writeFromPortFe(int value) {
        lastPortFeValue = value & 0xFF;
        micHigh = (value & 0x08) == 0;
        earHigh = (value & 0x10) != 0;
    }

    public synchronized boolean micHigh() {
        return micHigh;
    }

    public synchronized boolean earHigh() {
        return earHigh;
    }

    public synchronized void setTapeInputLevel(boolean tapeInputHigh) {
        this.tapeInputHigh = tapeInputHigh;
    }

    public synchronized int drainAudio(byte[] target, int offset, int length) {
        int copied = Math.min(length, bufferedBytes) & ~0x01;
        for (int i = 0; i < copied; i++) {
            target[offset + i] = audioBuffer[readIndex];
            readIndex = (readIndex + 1) % audioBuffer.length;
        }
        bufferedBytes -= copied;
        return copied;
    }

    @Override
    public int sampleRate() {
        return SAMPLE_RATE;
    }

    public synchronized int availableAudioBytes() {
        return bufferedBytes;
    }

    @Override
    public synchronized void reset() {
        micHigh = false;
        earHigh = false;
        tapeInputHigh = false;
        sampleRemainder = 0;
        readIndex = 0;
        writeIndex = 0;
        bufferedBytes = 0;
        lastPortFeValue = 0x00;
    }

    @Override
    public synchronized void onTStatesElapsed(int tStates) {
        long total = sampleRemainder + ((long) tStates * SAMPLE_RATE);
        int samplesToGenerate = (int) (total / CPU_CLOCK_HZ);
        sampleRemainder = total % CPU_CLOCK_HZ;

        short sample = mixedOutputLevel();
        for (int i = 0; i < samplesToGenerate; i++) {
            writeSample(sample);
        }
    }

    private short mixedOutputLevel() {
        boolean earBit = (lastPortFeValue & 0x10) != 0;
        boolean micBit = (lastPortFeValue & 0x08) != 0;

        int speakerLevel;
        if (earBit) {
            speakerLevel = micBit ? LEVEL_EAR_1_MIC_1 : LEVEL_EAR_1_MIC_0;
        } else {
            speakerLevel = micBit ? LEVEL_EAR_0_MIC_1 : LEVEL_EAR_0_MIC_0;
        }

        int tapeLevel = tapeInputHigh ? TAPE_INPUT_LEVEL : -TAPE_INPUT_LEVEL;
        int mixed = speakerLevel + tapeLevel;
        if (mixed > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (mixed < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) mixed;
    }

    private void writeSample(short sample) {
        ensureCapacityForSample();
        writeByteUnchecked((byte) (sample & 0xFF));
        writeByteUnchecked((byte) ((sample >>> 8) & 0xFF));
    }

    private void ensureCapacityForSample() {
        while (bufferedBytes > audioBuffer.length - BYTES_PER_SAMPLE) {
            readIndex = (readIndex + 1) % audioBuffer.length;
            bufferedBytes--;
        }
    }

    private void writeByteUnchecked(byte value) {
        audioBuffer[writeIndex] = value;
        writeIndex = (writeIndex + 1) % audioBuffer.length;
        bufferedBytes++;
    }
}
