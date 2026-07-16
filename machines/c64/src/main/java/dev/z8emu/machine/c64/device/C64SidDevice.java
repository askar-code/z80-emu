package dev.z8emu.machine.c64.device;

import dev.z8emu.platform.audio.ClockedPcmMonoSource;
import dev.z8emu.platform.audio.DcBlocker;
import dev.z8emu.platform.audio.PcmMonoSource;
import java.util.Arrays;

/**
 * Minimal three-voice SID approximation.
 *
 * <p>SYNC, RING, filter routing, resonance, and filter modes are stored but
 * deliberately inert. Combined waveforms use a digital AND approximation;
 * the real analogue artifacts are outside this first SID phase. Write-only
 * reads return zero instead of emulating the real chip's decaying data bus,
 * and the real ADSR freeze/delay bugs are not modeled.</p>
 */
public final class C64SidDevice extends ClockedPcmMonoSource {
    private static final int ATTACK = 0;
    private static final int DECAY_SUSTAIN = 1;
    private static final int RELEASE = 2;

    private static final int GATE = 0x01;
    private static final int SYNC = 0x02;
    private static final int RING = 0x04;
    private static final int TEST = 0x08;
    private static final int TRIANGLE = 0x10;
    private static final int SAW = 0x20;
    private static final int PULSE = 0x40;
    private static final int NOISE = 0x80;
    private static final int WAVEFORM_MASK = TRIANGLE | SAW | PULSE | NOISE;

    private static final int ACCUMULATOR_MASK = 0xFFFFFF;
    private static final int ACCUMULATOR_BIT_19 = 0x080000;
    private static final int LFSR_MASK = 0x7FFFFF;
    private static final int LFSR_SEED = 0x7FFFF8;

    private final long cpuClockHz;
    private final DcBlocker dcBlocker = new DcBlocker(DcBlocker.DEFAULT_ALPHA, 0);

    private final int[] frequency = new int[3];
    private final int[] pulseWidth = new int[3];
    private final int[] control = new int[3];
    private final int[] attackDecay = new int[3];
    private final int[] sustainRelease = new int[3];
    private final int[] accumulator = new int[3];
    private final int[] lfsr = new int[3];
    private final int[] envelopeState = new int[3];
    private final int[] envelopeLevel = new int[3];
    private final int[] rateCounter = new int[3];
    private final int[] exponentialCounter = new int[3];

    // Stored for compatibility; filtering is deliberately not applied yet.
    private int filterCutoffLow;
    private int filterCutoffHigh;
    private int resonanceFilterRouting;
    private int filterModeAndVolume;

    private long cycleAccumulator;

    public C64SidDevice(long cpuClockHz) {
        super(cpuClockHz, PcmMonoSource.DEFAULT_SAMPLE_RATE);
        this.cpuClockHz = cpuClockHz;
        reset();
    }

    public synchronized void writeRegister(int offset, int value) {
        int register = offset & 0x1F;
        int byteValue = value & 0xFF;
        if (register <= 0x14) {
            writeVoiceRegister(register / 7, register % 7, byteValue);
            return;
        }

        switch (register) {
            case 0x15 -> filterCutoffLow = byteValue;
            case 0x16 -> filterCutoffHigh = byteValue;
            case 0x17 -> resonanceFilterRouting = byteValue;
            case 0x18 -> filterModeAndVolume = byteValue;
            default -> {
                // POT/OSC/ENV registers and the unused tail are read-only.
            }
        }
    }

    public synchronized int readRegister(int offset) {
        return switch (offset & 0x1F) {
            case 0x19, 0x1A, 0x1D, 0x1E, 0x1F -> 0xFF;
            case 0x1B -> waveformOutput(2) >>> 4;
            case 0x1C -> envelopeLevel[2];
            default -> 0x00;
        };
    }

    @Override
    public synchronized void reset() {
        Arrays.fill(frequency, 0);
        Arrays.fill(pulseWidth, 0);
        Arrays.fill(control, 0);
        Arrays.fill(attackDecay, 0);
        Arrays.fill(sustainRelease, 0);
        Arrays.fill(accumulator, 0);
        Arrays.fill(lfsr, LFSR_SEED);
        Arrays.fill(envelopeState, RELEASE);
        Arrays.fill(envelopeLevel, 0);
        Arrays.fill(rateCounter, 0);
        Arrays.fill(exponentialCounter, 0);

        filterCutoffLow = 0;
        filterCutoffHigh = 0;
        resonanceFilterRouting = 0;
        filterModeAndVolume = 0;
        cycleAccumulator = 0;
        dcBlocker.reset(0);
        super.reset();
    }

    @Override
    protected short nextPcmSample() {
        cycleAccumulator += cpuClockHz;
        int cycles = (int) (cycleAccumulator / sampleRate());
        cycleAccumulator %= sampleRate();
        for (int cycle = 0; cycle < cycles; cycle++) {
            stepCycle();
        }

        int waveform0 = waveformOutput(0);
        int waveform1 = waveformOutput(1);
        int waveform2 = waveformOutput(2);
        long mix = (long) (waveform0 - 0x800) * envelopeLevel[0]
                + (long) (waveform1 - 0x800) * envelopeLevel[1]
                + (long) (waveform2 - 0x800) * envelopeLevel[2];
        int scaled = (int) (mix * (filterModeAndVolume & 0x0F) / (15L * 64));
        return dcBlocker.nextSample(scaled);
    }

    private void writeVoiceRegister(int voice, int register, int value) {
        switch (register) {
            case 0 -> frequency[voice] = (frequency[voice] & 0xFF00) | value;
            case 1 -> frequency[voice] = (frequency[voice] & 0x00FF) | (value << 8);
            case 2 -> pulseWidth[voice] = (pulseWidth[voice] & 0xF00) | value;
            case 3 -> pulseWidth[voice] = (pulseWidth[voice] & 0x0FF) | ((value & 0x0F) << 8);
            case 4 -> writeControl(voice, value);
            case 5 -> attackDecay[voice] = value;
            case 6 -> sustainRelease[voice] = value;
            default -> throw new IllegalStateException("Unexpected SID voice register: " + register);
        }
    }

    private void writeControl(int voice, int value) {
        int previous = control[voice];
        boolean previousTest = (previous & TEST) != 0;
        boolean nextTest = (value & TEST) != 0;
        if (!previousTest && nextTest) {
            accumulator[voice] = 0;
            lfsr[voice] = 0;
        } else if (previousTest && !nextTest) {
            lfsr[voice] = LFSR_SEED;
        }

        boolean previousGate = (previous & GATE) != 0;
        boolean nextGate = (value & GATE) != 0;
        if (!previousGate && nextGate) {
            envelopeState[voice] = ATTACK;
            exponentialCounter[voice] = 0;
        } else if (previousGate && !nextGate) {
            envelopeState[voice] = RELEASE;
            exponentialCounter[voice] = 0;
        }

        // SYNC and RING are retained in the control byte but are inert stubs.
        control[voice] = value & (GATE | SYNC | RING | TEST | WAVEFORM_MASK);
    }

    private void stepCycle() {
        stepVoice(0);
        stepVoice(1);
        stepVoice(2);
    }

    private void stepVoice(int voice) {
        int currentControl = control[voice];
        int previousAccumulator = accumulator[voice];
        int nextAccumulator = (currentControl & TEST) != 0
                ? 0
                : (previousAccumulator + frequency[voice]) & ACCUMULATOR_MASK;
        accumulator[voice] = nextAccumulator;
        if ((previousAccumulator & ACCUMULATOR_BIT_19) == 0
                && (nextAccumulator & ACCUMULATOR_BIT_19) != 0) {
            int currentLfsr = lfsr[voice];
            int feedback = ((currentLfsr >>> 22) ^ (currentLfsr >>> 17)) & 0x01;
            lfsr[voice] = ((currentLfsr << 1) | feedback) & LFSR_MASK;
        }
        tickEnvelope(voice);
    }

    private void tickEnvelope(int voice) {
        int counter = rateCounter[voice] + 1;
        int period = ratePeriod(activeRate(voice));
        if (counter < period) {
            rateCounter[voice] = counter;
            return;
        }
        rateCounter[voice] = 0;

        int state = envelopeState[voice];
        int level = envelopeLevel[voice];
        if (state == ATTACK) {
            if (level < 0xFF) {
                level++;
                envelopeLevel[voice] = level;
            }
            if (level == 0xFF) {
                envelopeState[voice] = DECAY_SUSTAIN;
                exponentialCounter[voice] = 0;
            }
            return;
        }

        if (state == DECAY_SUSTAIN) {
            int sustain = ((sustainRelease[voice] >>> 4) & 0x0F) * 0x11;
            if (level > sustain && exponentialStepDue(voice, level)) {
                envelopeLevel[voice] = level - 1;
            }
            return;
        }

        if (level > 0 && exponentialStepDue(voice, level)) {
            envelopeLevel[voice] = level - 1;
        }
    }

    private boolean exponentialStepDue(int voice, int level) {
        int divider = exponentialDivider(level);
        if (divider == 0) {
            return false;
        }
        int counter = exponentialCounter[voice] + 1;
        if (counter >= divider) {
            exponentialCounter[voice] = 0;
            return true;
        }
        exponentialCounter[voice] = counter;
        return false;
    }

    private int activeRate(int voice) {
        int state = envelopeState[voice];
        if (state == ATTACK) {
            return (attackDecay[voice] >>> 4) & 0x0F;
        }
        if (state == DECAY_SUSTAIN) {
            return attackDecay[voice] & 0x0F;
        }
        return sustainRelease[voice] & 0x0F;
    }

    private int waveformOutput(int voice) {
        int currentControl = control[voice];
        if ((currentControl & WAVEFORM_MASK) == 0) {
            return 0x800;
        }

        int currentAccumulator = accumulator[voice];
        int output = 0xFFF;
        if ((currentControl & TRIANGLE) != 0) {
            int folded = (currentAccumulator & 0x800000) != 0
                    ? ~currentAccumulator
                    : currentAccumulator;
            output &= (folded >>> 11) & 0xFFF;
        }
        if ((currentControl & SAW) != 0) {
            output &= currentAccumulator >>> 12;
        }
        if ((currentControl & PULSE) != 0) {
            int pulseOutput = (currentControl & TEST) != 0
                    ? 0xFFF
                    : ((currentAccumulator >>> 12) < pulseWidth[voice] ? 0x000 : 0xFFF);
            output &= pulseOutput;
        }
        if ((currentControl & NOISE) != 0) {
            output &= noiseOutput(lfsr[voice]);
        }
        return output;
    }

    private static int noiseOutput(int lfsr) {
        return ((lfsr & 0x400000) >>> 11)
                | ((lfsr & 0x100000) >>> 10)
                | ((lfsr & 0x010000) >>> 7)
                | ((lfsr & 0x002000) >>> 5)
                | ((lfsr & 0x000800) >>> 4)
                | ((lfsr & 0x000080) >>> 1)
                | ((lfsr & 0x000010) << 1)
                | ((lfsr & 0x000004) << 2);
    }

    private static int exponentialDivider(int level) {
        if (level > 0x5D) {
            return 1;
        }
        if (level > 0x36) {
            return 2;
        }
        if (level > 0x1A) {
            return 4;
        }
        if (level > 0x0E) {
            return 8;
        }
        if (level > 0x06) {
            return 16;
        }
        return level > 0 ? 30 : 0;
    }

    private static int ratePeriod(int rate) {
        return switch (rate & 0x0F) {
            case 0 -> 9;
            case 1 -> 32;
            case 2 -> 63;
            case 3 -> 95;
            case 4 -> 149;
            case 5 -> 220;
            case 6 -> 267;
            case 7 -> 313;
            case 8 -> 392;
            case 9 -> 977;
            case 10 -> 1_954;
            case 11 -> 3_126;
            case 12 -> 3_907;
            case 13 -> 11_720;
            case 14 -> 19_532;
            case 15 -> 31_251;
            default -> throw new IllegalStateException("Unreachable SID rate");
        };
    }
}
