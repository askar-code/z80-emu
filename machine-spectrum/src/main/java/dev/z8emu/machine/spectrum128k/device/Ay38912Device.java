package dev.z8emu.machine.spectrum128k.device;

import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.device.TimedDevice;
import java.util.Arrays;

public final class Ay38912Device implements TimedDevice, PcmMonoSource {
    public static final int SAMPLE_RATE = 44_100;

    private static final int BYTES_PER_SAMPLE = 2;
    private static final int BUFFER_CAPACITY = SAMPLE_RATE * BYTES_PER_SAMPLE / 5;
    private static final int REGISTER_COUNT = 16;
    private static final int CHANNEL_COUNT = 3;
    private static final float HIGH_PASS_ALPHA = 0.995f;
    private static final int[] VOLUME_LEVELS = {
            0, 32, 48, 72,
            108, 162, 243, 364,
            546, 819, 1_228, 1_842,
            2_763, 4_145, 6_217, 9_326
    };

    private final long cpuClockHz;
    private final long psgClockHz;
    private final byte[] registers = new byte[REGISTER_COUNT];
    private final byte[] audioBuffer = new byte[BUFFER_CAPACITY];
    private final double[] tonePhases = new double[CHANNEL_COUNT];

    private int selectedRegister;
    private long sampleRemainder;
    private int readIndex;
    private int writeIndex;
    private int bufferedBytes;
    private double noisePhase;
    private int noiseShiftRegister;
    private boolean noiseOutputHigh;
    private double envelopePhase;
    private int envelopeLevelCounter;
    private int envelopeAttackMask;
    private boolean envelopeHold;
    private boolean envelopeAlternate;
    private boolean envelopeHolding;
    private float previousInputLevel;
    private float filteredLevel;

    public Ay38912Device(long cpuClockHz) {
        if (cpuClockHz <= 0) {
            throw new IllegalArgumentException("cpuClockHz must be positive");
        }
        this.cpuClockHz = cpuClockHz;
        this.psgClockHz = cpuClockHz / 2;
    }

    public synchronized void selectRegister(int registerIndex) {
        selectedRegister = registerIndex & 0x0F;
    }

    public synchronized int selectedRegister() {
        return selectedRegister;
    }

    public synchronized int readSelectedRegister() {
        return registers[selectedRegister] & 0xFF;
    }

    public synchronized void writeSelectedRegister(int value) {
        int normalized = normalizeRegisterValue(selectedRegister, value);
        registers[selectedRegister] = (byte) normalized;
        if (selectedRegister == 13) {
            restartEnvelope(normalized);
        }
    }

    public synchronized int registerValue(int registerIndex) {
        validateRegisterIndex(registerIndex);
        return registers[registerIndex] & 0xFF;
    }

    @Override
    public int sampleRate() {
        return SAMPLE_RATE;
    }

    @Override
    public synchronized int drainAudio(byte[] target, int offset, int length) {
        int copied = Math.min(length, bufferedBytes) & ~0x01;
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
        Arrays.fill(registers, (byte) 0x00);
        Arrays.fill(tonePhases, 0.0d);
        selectedRegister = 0;
        sampleRemainder = 0;
        readIndex = 0;
        writeIndex = 0;
        bufferedBytes = 0;
        noisePhase = 0.0d;
        noiseShiftRegister = 0x1FFFF;
        noiseOutputHigh = true;
        envelopePhase = 0.0d;
        envelopeLevelCounter = 0x0F;
        envelopeAttackMask = 0x00;
        envelopeHold = false;
        envelopeAlternate = false;
        envelopeHolding = false;
        previousInputLevel = 0.0f;
        filteredLevel = 0.0f;
    }

    @Override
    public synchronized void onTStatesElapsed(int tStates) {
        long total = sampleRemainder + ((long) tStates * SAMPLE_RATE);
        int samplesToGenerate = (int) (total / cpuClockHz);
        sampleRemainder = total % cpuClockHz;

        for (int i = 0; i < samplesToGenerate; i++) {
            writeSample(nextSample());
        }
    }

    private short nextSample() {
        advanceTonePhases();
        advanceNoise();
        advanceEnvelope();

        // The AY outputs positive amplitudes that are typically AC-coupled later
        // in the analogue path, so we approximate that here with a simple high-pass.
        float inputLevel = channelSample(0) + channelSample(1) + channelSample(2);
        filteredLevel = HIGH_PASS_ALPHA * (filteredLevel + inputLevel - previousInputLevel);
        previousInputLevel = inputLevel;

        int mixed = Math.round(filteredLevel);
        if (mixed > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (mixed < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) mixed;
    }

    private void advanceTonePhases() {
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            int period = Math.max(1, tonePeriod(channel));
            double frequency = (double) psgClockHz / (16.0d * period);
            tonePhases[channel] += frequency / SAMPLE_RATE;
            tonePhases[channel] -= Math.floor(tonePhases[channel]);
        }
    }

    private void advanceNoise() {
        int period = Math.max(1, registerValue(6) & 0x1F);
        double frequency = (double) psgClockHz / (16.0d * period);
        noisePhase += frequency / SAMPLE_RATE;
        int steps = (int) noisePhase;
        noisePhase -= steps;
        while (steps-- > 0) {
            int feedback = ((noiseShiftRegister >>> 0) ^ (noiseShiftRegister >>> 3)) & 0x01;
            noiseShiftRegister = (noiseShiftRegister >>> 1) | (feedback << 16);
            noiseOutputHigh = (noiseShiftRegister & 0x01) != 0;
        }
    }

    private void advanceEnvelope() {
        int period = Math.max(1, envelopePeriod());
        double frequency = (double) psgClockHz / (256.0d * period);
        envelopePhase += frequency / SAMPLE_RATE;
        int steps = (int) envelopePhase;
        envelopePhase -= steps;
        while (steps-- > 0) {
            stepEnvelope();
        }
    }

    private void stepEnvelope() {
        if (envelopeHolding) {
            return;
        }

        envelopeLevelCounter--;
        if (envelopeLevelCounter >= 0) {
            return;
        }

        if (envelopeHold) {
            if (envelopeAlternate) {
                envelopeAttackMask ^= 0x0F;
            }
            envelopeHolding = true;
            envelopeLevelCounter = 0;
            return;
        }

        if (envelopeAlternate) {
            envelopeAttackMask ^= 0x0F;
        }
        envelopeLevelCounter &= 0x0F;
    }

    private int channelSample(int channel) {
        int amplitudeRegister = registerValue(8 + channel);
        int level = (amplitudeRegister & 0x10) != 0
                ? VOLUME_LEVELS[currentEnvelopeLevel()]
                : VOLUME_LEVELS[amplitudeRegister & 0x0F];
        if (level == 0) {
            return 0;
        }

        int mixer = registerValue(7);
        boolean toneEnabled = (mixer & (1 << channel)) == 0;
        boolean noiseEnabled = (mixer & (1 << (channel + 3))) == 0;
        boolean toneHigh = !toneEnabled || tonePhases[channel] < 0.5d;
        boolean noiseHigh = !noiseEnabled || noiseOutputHigh;
        return toneHigh && noiseHigh ? level : 0;
    }

    private int currentEnvelopeLevel() {
        return envelopeLevelCounter ^ envelopeAttackMask;
    }

    private int tonePeriod(int channel) {
        int fine = registerValue(channel * 2);
        int coarse = registerValue((channel * 2) + 1) & 0x0F;
        return (coarse << 8) | fine;
    }

    private int envelopePeriod() {
        return (registerValue(12) << 8) | registerValue(11);
    }

    private void restartEnvelope(int shape) {
        boolean continueFlag = (shape & 0x08) != 0;
        envelopeHold = (shape & 0x01) != 0;
        envelopeAlternate = (shape & 0x02) != 0;
        envelopeAttackMask = (shape & 0x04) != 0 ? 0x0F : 0x00;
        if (!continueFlag) {
            envelopeHold = true;
            envelopeAlternate = envelopeAttackMask != 0;
        }
        envelopeLevelCounter = 0x0F;
        envelopeHolding = false;
        envelopePhase = 0.0d;
    }

    private int normalizeRegisterValue(int registerIndex, int value) {
        int normalized = value & 0xFF;
        return switch (registerIndex) {
            case 1, 3, 5, 13 -> normalized & 0x0F;
            case 6 -> normalized & 0x1F;
            case 8, 9, 10 -> normalized & 0x1F;
            default -> normalized;
        };
    }

    private void validateRegisterIndex(int registerIndex) {
        if (registerIndex < 0 || registerIndex >= REGISTER_COUNT) {
            throw new IllegalArgumentException("AY register index out of range: " + registerIndex);
        }
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
