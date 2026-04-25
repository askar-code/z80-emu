package dev.z8emu.machine.radio86rk.device;

import dev.z8emu.platform.audio.ClockedPcmMonoSource;
import dev.z8emu.platform.audio.DcBlocker;

public final class Radio86AudioDevice extends ClockedPcmMonoSource {
    public static final int SAMPLE_RATE = 44_100;

    private static final boolean MONITOR_TAPE_OUTPUT = Boolean.getBoolean("z8emu.radioTapeMonitorAudio");
    private static final int SPEAKER_LEVEL_HIGH = 9_000;
    private static final int TAPE_OUTPUT_LEVEL = 3_000;

    private final DcBlocker dcBlocker = new DcBlocker(DcBlocker.DEFAULT_ALPHA, 0);

    private boolean speakerHigh;
    private boolean tapeOutputHigh;

    public Radio86AudioDevice(long cpuClockHz) {
        super(cpuClockHz, SAMPLE_RATE);
    }

    public synchronized void setSpeakerHigh(boolean speakerHigh) {
        this.speakerHigh = speakerHigh;
    }

    public synchronized void setTapeOutputHigh(boolean tapeOutputHigh) {
        this.tapeOutputHigh = tapeOutputHigh;
    }

    @Override
    public synchronized void reset() {
        speakerHigh = false;
        tapeOutputHigh = false;
        resetPcmAudio();
        dcBlocker.reset(0);
    }

    @Override
    protected short nextPcmSample() {
        return dcBlocker.nextSample(mixedInputLevel());
    }

    private int mixedInputLevel() {
        int mixed = speakerHigh ? SPEAKER_LEVEL_HIGH : 0;
        if (MONITOR_TAPE_OUTPUT && tapeOutputHigh) {
            mixed += TAPE_OUTPUT_LEVEL;
        }
        return mixed;
    }
}
