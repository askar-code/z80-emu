package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.platform.audio.ClockedPcmMonoSource;
import dev.z8emu.platform.audio.DcBlocker;

public final class BeeperDevice extends ClockedPcmMonoSource {
    public static final int SAMPLE_RATE = 44_100;

    private static final short LEVEL_EAR_1_MIC_1 = 12_000;
    private static final short LEVEL_EAR_1_MIC_0 = 11_000;
    private static final short LEVEL_EAR_0_MIC_1 = -8_000;
    private static final short LEVEL_EAR_0_MIC_0 = -12_000;
    private static final short TAPE_INPUT_LEVEL = 5_000;

    private final DcBlocker dcBlocker = new DcBlocker(DcBlocker.DEFAULT_ALPHA, LEVEL_EAR_0_MIC_0 - TAPE_INPUT_LEVEL);
    private boolean micHigh;
    private boolean earHigh;
    private boolean tapeInputHigh;
    private int lastPortFeValue = 0x00;

    public BeeperDevice(long cpuClockHz) {
        super(cpuClockHz, SAMPLE_RATE);
    }

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

    @Override
    public synchronized void reset() {
        micHigh = false;
        earHigh = false;
        tapeInputHigh = false;
        lastPortFeValue = 0x00;
        resetPcmAudio();
        dcBlocker.reset(mixedOutputLevel());
    }

    @Override
    protected short nextPcmSample() {
        return dcBlocker.nextSample(mixedOutputLevel());
    }

    private int mixedOutputLevel() {
        boolean earBit = (lastPortFeValue & 0x10) != 0;
        boolean micBit = (lastPortFeValue & 0x08) != 0;

        int speakerLevel;
        if (earBit) {
            speakerLevel = micBit ? LEVEL_EAR_1_MIC_1 : LEVEL_EAR_1_MIC_0;
        } else {
            speakerLevel = micBit ? LEVEL_EAR_0_MIC_1 : LEVEL_EAR_0_MIC_0;
        }

        int tapeLevel = tapeInputHigh ? TAPE_INPUT_LEVEL : -TAPE_INPUT_LEVEL;
        return speakerLevel + tapeLevel;
    }
}
