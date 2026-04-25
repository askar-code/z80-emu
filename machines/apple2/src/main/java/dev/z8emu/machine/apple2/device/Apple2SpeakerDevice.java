package dev.z8emu.machine.apple2.device;

import dev.z8emu.platform.audio.ClockedPcmMonoSource;
import dev.z8emu.platform.audio.DcBlocker;

public final class Apple2SpeakerDevice extends ClockedPcmMonoSource {
    public static final int SAMPLE_RATE = 44_100;

    private static final int LEVEL_HIGH = 9_000;
    private static final int LEVEL_LOW = -9_000;

    private final DcBlocker dcBlocker = new DcBlocker(DcBlocker.DEFAULT_ALPHA, LEVEL_LOW);

    private boolean high;

    public Apple2SpeakerDevice(long cpuClockHz) {
        super(cpuClockHz, SAMPLE_RATE);
    }

    public synchronized void toggle() {
        high = !high;
    }

    public synchronized boolean high() {
        return high;
    }

    @Override
    public synchronized void reset() {
        high = false;
        dcBlocker.reset(LEVEL_LOW);
        resetPcmAudio();
    }

    @Override
    protected short nextPcmSample() {
        int rawLevel = high ? LEVEL_HIGH : LEVEL_LOW;
        return dcBlocker.nextSample(rawLevel);
    }
}
