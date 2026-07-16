package dev.z8emu.machine.c64.device;

import dev.z8emu.platform.audio.ClockedPcmMonoSource;
import dev.z8emu.platform.audio.DcBlocker;
import dev.z8emu.platform.audio.PcmMonoSource;

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

    private int frequency0;
    private int pulseWidth0;
    private int control0;
    private int attackDecay0;
    private int sustainRelease0;
    private int accumulator0;
    private int lfsr0;
    private int envelopeState0;
    private int envelopeLevel0;
    private int rateCounter0;
    private int exponentialCounter0;

    private int frequency1;
    private int pulseWidth1;
    private int control1;
    private int attackDecay1;
    private int sustainRelease1;
    private int accumulator1;
    private int lfsr1;
    private int envelopeState1;
    private int envelopeLevel1;
    private int rateCounter1;
    private int exponentialCounter1;

    private int frequency2;
    private int pulseWidth2;
    private int control2;
    private int attackDecay2;
    private int sustainRelease2;
    private int accumulator2;
    private int lfsr2;
    private int envelopeState2;
    private int envelopeLevel2;
    private int rateCounter2;
    private int exponentialCounter2;

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
            case 0x1C -> envelopeLevel2;
            default -> 0x00;
        };
    }

    @Override
    public synchronized void reset() {
        frequency0 = 0;
        pulseWidth0 = 0;
        control0 = 0;
        attackDecay0 = 0;
        sustainRelease0 = 0;
        accumulator0 = 0;
        lfsr0 = LFSR_SEED;
        envelopeState0 = RELEASE;
        envelopeLevel0 = 0;
        rateCounter0 = 0;
        exponentialCounter0 = 0;

        frequency1 = 0;
        pulseWidth1 = 0;
        control1 = 0;
        attackDecay1 = 0;
        sustainRelease1 = 0;
        accumulator1 = 0;
        lfsr1 = LFSR_SEED;
        envelopeState1 = RELEASE;
        envelopeLevel1 = 0;
        rateCounter1 = 0;
        exponentialCounter1 = 0;

        frequency2 = 0;
        pulseWidth2 = 0;
        control2 = 0;
        attackDecay2 = 0;
        sustainRelease2 = 0;
        accumulator2 = 0;
        lfsr2 = LFSR_SEED;
        envelopeState2 = RELEASE;
        envelopeLevel2 = 0;
        rateCounter2 = 0;
        exponentialCounter2 = 0;

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
        long mix = (long) (waveform0 - 0x800) * envelopeLevel0
                + (long) (waveform1 - 0x800) * envelopeLevel1
                + (long) (waveform2 - 0x800) * envelopeLevel2;
        int scaled = (int) (mix * (filterModeAndVolume & 0x0F) / (15L * 64));
        return dcBlocker.nextSample(scaled);
    }

    private void writeVoiceRegister(int voice, int register, int value) {
        switch (register) {
            case 0 -> setFrequency(voice, (frequency(voice) & 0xFF00) | value);
            case 1 -> setFrequency(voice, (frequency(voice) & 0x00FF) | (value << 8));
            case 2 -> setPulseWidth(voice, (pulseWidth(voice) & 0xF00) | value);
            case 3 -> setPulseWidth(voice, (pulseWidth(voice) & 0x0FF) | ((value & 0x0F) << 8));
            case 4 -> writeControl(voice, value);
            case 5 -> setAttackDecay(voice, value);
            case 6 -> setSustainRelease(voice, value);
            default -> throw new IllegalStateException("Unexpected SID voice register: " + register);
        }
    }

    private void writeControl(int voice, int value) {
        int previous = control(voice);
        boolean previousTest = (previous & TEST) != 0;
        boolean nextTest = (value & TEST) != 0;
        if (!previousTest && nextTest) {
            setAccumulator(voice, 0);
            setLfsr(voice, 0);
        } else if (previousTest && !nextTest) {
            setLfsr(voice, LFSR_SEED);
        }

        boolean previousGate = (previous & GATE) != 0;
        boolean nextGate = (value & GATE) != 0;
        if (!previousGate && nextGate) {
            setEnvelopeState(voice, ATTACK);
            setExponentialCounter(voice, 0);
        } else if (previousGate && !nextGate) {
            setEnvelopeState(voice, RELEASE);
            setExponentialCounter(voice, 0);
        }

        // SYNC and RING are retained in the control byte but are inert stubs.
        setControl(voice, value & (GATE | SYNC | RING | TEST | WAVEFORM_MASK));
    }

    private void stepCycle() {
        stepVoice(0);
        stepVoice(1);
        stepVoice(2);
    }

    private void stepVoice(int voice) {
        int currentControl = control(voice);
        int previousAccumulator = accumulator(voice);
        int nextAccumulator = (currentControl & TEST) != 0
                ? 0
                : (previousAccumulator + frequency(voice)) & ACCUMULATOR_MASK;
        setAccumulator(voice, nextAccumulator);
        if ((previousAccumulator & ACCUMULATOR_BIT_19) == 0
                && (nextAccumulator & ACCUMULATOR_BIT_19) != 0) {
            int currentLfsr = lfsr(voice);
            int feedback = ((currentLfsr >>> 22) ^ (currentLfsr >>> 17)) & 0x01;
            setLfsr(voice, ((currentLfsr << 1) | feedback) & LFSR_MASK);
        }
        tickEnvelope(voice);
    }

    private void tickEnvelope(int voice) {
        int counter = rateCounter(voice) + 1;
        int period = ratePeriod(activeRate(voice));
        if (counter < period) {
            setRateCounter(voice, counter);
            return;
        }
        setRateCounter(voice, 0);

        int state = envelopeState(voice);
        int level = envelopeLevel(voice);
        if (state == ATTACK) {
            if (level < 0xFF) {
                level++;
                setEnvelopeLevel(voice, level);
            }
            if (level == 0xFF) {
                setEnvelopeState(voice, DECAY_SUSTAIN);
                setExponentialCounter(voice, 0);
            }
            return;
        }

        if (state == DECAY_SUSTAIN) {
            int sustain = ((sustainRelease(voice) >>> 4) & 0x0F) * 0x11;
            if (level > sustain && exponentialStepDue(voice, level)) {
                setEnvelopeLevel(voice, level - 1);
            }
            return;
        }

        if (level > 0 && exponentialStepDue(voice, level)) {
            setEnvelopeLevel(voice, level - 1);
        }
    }

    private boolean exponentialStepDue(int voice, int level) {
        int divider = exponentialDivider(level);
        if (divider == 0) {
            return false;
        }
        int counter = exponentialCounter(voice) + 1;
        if (counter >= divider) {
            setExponentialCounter(voice, 0);
            return true;
        }
        setExponentialCounter(voice, counter);
        return false;
    }

    private int activeRate(int voice) {
        int state = envelopeState(voice);
        if (state == ATTACK) {
            return (attackDecay(voice) >>> 4) & 0x0F;
        }
        if (state == DECAY_SUSTAIN) {
            return attackDecay(voice) & 0x0F;
        }
        return sustainRelease(voice) & 0x0F;
    }

    private int waveformOutput(int voice) {
        int currentControl = control(voice);
        if ((currentControl & WAVEFORM_MASK) == 0) {
            return 0x800;
        }

        int currentAccumulator = accumulator(voice);
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
                    : ((currentAccumulator >>> 12) < pulseWidth(voice) ? 0x000 : 0xFFF);
            output &= pulseOutput;
        }
        if ((currentControl & NOISE) != 0) {
            output &= noiseOutput(lfsr(voice));
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

    private int frequency(int voice) {
        return switch (voice) {
            case 0 -> frequency0;
            case 1 -> frequency1;
            case 2 -> frequency2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setFrequency(int voice, int value) {
        switch (voice) {
            case 0 -> frequency0 = value;
            case 1 -> frequency1 = value;
            case 2 -> frequency2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private int pulseWidth(int voice) {
        return switch (voice) {
            case 0 -> pulseWidth0;
            case 1 -> pulseWidth1;
            case 2 -> pulseWidth2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setPulseWidth(int voice, int value) {
        switch (voice) {
            case 0 -> pulseWidth0 = value;
            case 1 -> pulseWidth1 = value;
            case 2 -> pulseWidth2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private int control(int voice) {
        return switch (voice) {
            case 0 -> control0;
            case 1 -> control1;
            case 2 -> control2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setControl(int voice, int value) {
        switch (voice) {
            case 0 -> control0 = value;
            case 1 -> control1 = value;
            case 2 -> control2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private int attackDecay(int voice) {
        return switch (voice) {
            case 0 -> attackDecay0;
            case 1 -> attackDecay1;
            case 2 -> attackDecay2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setAttackDecay(int voice, int value) {
        switch (voice) {
            case 0 -> attackDecay0 = value;
            case 1 -> attackDecay1 = value;
            case 2 -> attackDecay2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private int sustainRelease(int voice) {
        return switch (voice) {
            case 0 -> sustainRelease0;
            case 1 -> sustainRelease1;
            case 2 -> sustainRelease2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setSustainRelease(int voice, int value) {
        switch (voice) {
            case 0 -> sustainRelease0 = value;
            case 1 -> sustainRelease1 = value;
            case 2 -> sustainRelease2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private int accumulator(int voice) {
        return switch (voice) {
            case 0 -> accumulator0;
            case 1 -> accumulator1;
            case 2 -> accumulator2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setAccumulator(int voice, int value) {
        switch (voice) {
            case 0 -> accumulator0 = value;
            case 1 -> accumulator1 = value;
            case 2 -> accumulator2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private int lfsr(int voice) {
        return switch (voice) {
            case 0 -> lfsr0;
            case 1 -> lfsr1;
            case 2 -> lfsr2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setLfsr(int voice, int value) {
        switch (voice) {
            case 0 -> lfsr0 = value;
            case 1 -> lfsr1 = value;
            case 2 -> lfsr2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private int envelopeState(int voice) {
        return switch (voice) {
            case 0 -> envelopeState0;
            case 1 -> envelopeState1;
            case 2 -> envelopeState2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setEnvelopeState(int voice, int value) {
        switch (voice) {
            case 0 -> envelopeState0 = value;
            case 1 -> envelopeState1 = value;
            case 2 -> envelopeState2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private int envelopeLevel(int voice) {
        return switch (voice) {
            case 0 -> envelopeLevel0;
            case 1 -> envelopeLevel1;
            case 2 -> envelopeLevel2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setEnvelopeLevel(int voice, int value) {
        switch (voice) {
            case 0 -> envelopeLevel0 = value;
            case 1 -> envelopeLevel1 = value;
            case 2 -> envelopeLevel2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private int rateCounter(int voice) {
        return switch (voice) {
            case 0 -> rateCounter0;
            case 1 -> rateCounter1;
            case 2 -> rateCounter2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setRateCounter(int voice, int value) {
        switch (voice) {
            case 0 -> rateCounter0 = value;
            case 1 -> rateCounter1 = value;
            case 2 -> rateCounter2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private int exponentialCounter(int voice) {
        return switch (voice) {
            case 0 -> exponentialCounter0;
            case 1 -> exponentialCounter1;
            case 2 -> exponentialCounter2;
            default -> throw invalidVoice(voice);
        };
    }

    private void setExponentialCounter(int voice, int value) {
        switch (voice) {
            case 0 -> exponentialCounter0 = value;
            case 1 -> exponentialCounter1 = value;
            case 2 -> exponentialCounter2 = value;
            default -> throw invalidVoice(voice);
        }
    }

    private static IllegalArgumentException invalidVoice(int voice) {
        return new IllegalArgumentException("SID voice out of range: " + voice);
    }
}
