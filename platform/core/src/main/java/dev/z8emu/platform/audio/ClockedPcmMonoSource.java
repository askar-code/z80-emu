package dev.z8emu.platform.audio;

import dev.z8emu.platform.device.TimedDevice;
import java.util.Objects;

/**
 * Shared 16-bit little-endian PCM stream for audio devices driven by machine clock ticks.
 */
public abstract class ClockedPcmMonoSource implements TimedDevice, PcmMonoSource {
    private static final int BYTES_PER_SAMPLE = Short.BYTES;
    private static final int DEFAULT_BUFFER_MILLIS = 200;
    private static final int MILLIS_PER_SECOND = 1_000;

    private final long sourceClockHz;
    private final int sampleRate;
    private final byte[] audioBuffer;

    private long sampleRemainder;
    private int readIndex;
    private int writeIndex;
    private int bufferedBytes;

    protected ClockedPcmMonoSource(long sourceClockHz, int sampleRate) {
        this(sourceClockHz, sampleRate, defaultBufferCapacity(sampleRate));
    }

    protected ClockedPcmMonoSource(long sourceClockHz, int sampleRate, int bufferCapacityBytes) {
        if (sourceClockHz <= 0) {
            throw new IllegalArgumentException("sourceClockHz must be positive");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        if (bufferCapacityBytes < BYTES_PER_SAMPLE || (bufferCapacityBytes % BYTES_PER_SAMPLE) != 0) {
            throw new IllegalArgumentException("bufferCapacityBytes must fit whole 16-bit samples");
        }

        this.sourceClockHz = sourceClockHz;
        this.sampleRate = sampleRate;
        this.audioBuffer = new byte[bufferCapacityBytes];
    }

    @Override
    public final int sampleRate() {
        return sampleRate;
    }

    @Override
    public final synchronized int drainAudio(byte[] target, int offset, int length) {
        Objects.requireNonNull(target, "target");
        Objects.checkFromIndexSize(offset, length, target.length);

        int requestedBytes = length & ~(BYTES_PER_SAMPLE - 1);
        int copied = Math.min(requestedBytes, bufferedBytes);
        for (int i = 0; i < copied; i++) {
            target[offset + i] = audioBuffer[readIndex];
            readIndex = (readIndex + 1) % audioBuffer.length;
        }
        bufferedBytes -= copied;
        return copied;
    }

    public final synchronized int availableAudioBytes() {
        return bufferedBytes;
    }

    @Override
    public final synchronized void onTStatesElapsed(int tStates) {
        int samplesToGenerate = samplesForElapsedTicks(tStates);
        for (int i = 0; i < samplesToGenerate; i++) {
            writeSample(nextPcmSample());
        }
    }

    protected abstract short nextPcmSample();

    protected final synchronized void resetPcmAudio() {
        sampleRemainder = 0;
        readIndex = 0;
        writeIndex = 0;
        bufferedBytes = 0;
    }

    private int samplesForElapsedTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must not be negative");
        }

        long total = sampleRemainder + ((long) ticks * sampleRate);
        int samplesToGenerate = (int) (total / sourceClockHz);
        sampleRemainder = total % sourceClockHz;
        return samplesToGenerate;
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

    private static int defaultBufferCapacity(int sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        return (sampleRate * BYTES_PER_SAMPLE * DEFAULT_BUFFER_MILLIS) / MILLIS_PER_SECOND;
    }
}
