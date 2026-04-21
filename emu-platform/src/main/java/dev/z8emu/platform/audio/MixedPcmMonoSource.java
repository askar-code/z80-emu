package dev.z8emu.platform.audio;

import java.util.Arrays;
import java.util.Objects;

public final class MixedPcmMonoSource implements PcmMonoSource {
    private final PcmMonoSource[] sources;
    private final byte[][] scratchBuffers;
    private final int[] scratchLengths;
    private final int sampleRate;

    public MixedPcmMonoSource(PcmMonoSource... sources) {
        Objects.requireNonNull(sources, "sources");
        if (sources.length == 0) {
            throw new IllegalArgumentException("At least one PCM source is required");
        }

        this.sources = new PcmMonoSource[sources.length];
        this.scratchBuffers = new byte[sources.length][];
        this.scratchLengths = new int[sources.length];

        int detectedSampleRate = -1;
        for (int i = 0; i < sources.length; i++) {
            PcmMonoSource source = Objects.requireNonNull(sources[i], "sources[" + i + "]");
            if (detectedSampleRate < 0) {
                detectedSampleRate = source.sampleRate();
            } else if (detectedSampleRate != source.sampleRate()) {
                throw new IllegalArgumentException("All PCM sources must use the same sample rate");
            }
            this.sources[i] = source;
            this.scratchBuffers[i] = new byte[0];
        }

        this.sampleRate = detectedSampleRate;
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public synchronized int drainAudio(byte[] target, int offset, int length) {
        Objects.requireNonNull(target, "target");
        if (length <= 0) {
            return 0;
        }

        int requested = length & ~0x01;
        if (requested == 0) {
            return 0;
        }

        int mixedBytes = 0;
        for (int i = 0; i < sources.length; i++) {
            ensureScratchCapacity(i, requested);
            int copied = sources[i].drainAudio(scratchBuffers[i], 0, requested) & ~0x01;
            scratchLengths[i] = copied;
            if (copied > mixedBytes) {
                mixedBytes = copied;
            }
        }

        for (int sampleOffset = 0; sampleOffset < mixedBytes; sampleOffset += 2) {
            int mixedSample = 0;
            for (int i = 0; i < sources.length; i++) {
                if (scratchLengths[i] <= sampleOffset + 1) {
                    continue;
                }
                byte[] scratch = scratchBuffers[i];
                short sample = (short) (((scratch[sampleOffset + 1] & 0xFF) << 8) | (scratch[sampleOffset] & 0xFF));
                mixedSample += sample;
            }

            if (mixedSample > Short.MAX_VALUE) {
                mixedSample = Short.MAX_VALUE;
            } else if (mixedSample < Short.MIN_VALUE) {
                mixedSample = Short.MIN_VALUE;
            }

            target[offset + sampleOffset] = (byte) (mixedSample & 0xFF);
            target[offset + sampleOffset + 1] = (byte) ((mixedSample >>> 8) & 0xFF);
        }

        return mixedBytes;
    }

    private void ensureScratchCapacity(int sourceIndex, int requiredCapacity) {
        if (scratchBuffers[sourceIndex].length >= requiredCapacity) {
            return;
        }
        scratchBuffers[sourceIndex] = Arrays.copyOf(scratchBuffers[sourceIndex], requiredCapacity);
    }
}
