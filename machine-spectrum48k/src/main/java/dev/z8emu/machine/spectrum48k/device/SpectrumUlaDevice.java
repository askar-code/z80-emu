package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.device.TimedDevice;
import dev.z8emu.platform.video.FrameBuffer;

public final class SpectrumUlaDevice implements TimedDevice {
    public static final int T_STATES_PER_FRAME = 69_888;
    public static final int DISPLAY_WIDTH = 256;
    public static final int DISPLAY_HEIGHT = 192;
    public static final int BORDER_LEFT = 48;
    public static final int BORDER_RIGHT = 48;
    public static final int BORDER_TOP = 48;
    public static final int BORDER_BOTTOM = 56;
    public static final int FRAME_WIDTH = BORDER_LEFT + DISPLAY_WIDTH + BORDER_RIGHT;
    public static final int FRAME_HEIGHT = BORDER_TOP + DISPLAY_HEIGHT + BORDER_BOTTOM;

    private static final int[] NORMAL_PALETTE = {
            0xFF000000,
            0xFF0000CD,
            0xFFCD0000,
            0xFFCD00CD,
            0xFF00CD00,
            0xFF00CDCD,
            0xFFCDCD00,
            0xFFCDCDCD,
    };

    private static final int[] BRIGHT_PALETTE = {
            0xFF000000,
            0xFF0000FF,
            0xFFFF0000,
            0xFFFF00FF,
            0xFF00FF00,
            0xFF00FFFF,
            0xFFFFFF00,
            0xFFFFFFFF,
    };

    private int borderColor;
    private int frameRemainder;
    private long frameCounter;
    private int lastRefreshAddress;
    private boolean pendingMaskableInterrupt;
    private final FrameBuffer frameBuffer = new FrameBuffer(FRAME_WIDTH, FRAME_HEIGHT);

    public int readPortFe(int port, KeyboardMatrixDevice keyboard, TapeDevice tape) {
        int value = keyboard.readSelectedRows(port);
        return tape.applyEarBitToPortRead(value);
    }

    public void writePortFe(int value, BeeperDevice beeper) {
        borderColor = value & 0x07;
        beeper.writeFromPortFe(value);
    }

    public int borderColor() {
        return borderColor;
    }

    public long frameCounter() {
        return frameCounter;
    }

    public void onRefreshAddress(int address) {
        lastRefreshAddress = address & 0xFFFF;
    }

    public int lastRefreshAddress() {
        return lastRefreshAddress;
    }

    public FrameBuffer frameBuffer() {
        return frameBuffer;
    }

    public boolean consumeMaskableInterrupt() {
        boolean pending = pendingMaskableInterrupt;
        pendingMaskableInterrupt = false;
        return pending;
    }

    public FrameBuffer renderFrame(Spectrum48kMemoryMap memory) {
        int borderArgb = paletteColor(borderColor, false);
        frameBuffer.clear(borderArgb);

        boolean flashPhase = ((frameCounter / 16) & 0x01) != 0;

        for (int y = 0; y < DISPLAY_HEIGHT; y++) {
            int pixelRowBase = 0x4000
                    | ((y & 0xC0) << 5)
                    | ((y & 0x07) << 8)
                    | ((y & 0x38) << 2);
            int attributeRowBase = 0x5800 + ((y >>> 3) * 32);
            int targetY = BORDER_TOP + y;

            for (int columnByte = 0; columnByte < 32; columnByte++) {
                int pixelByte = memory.read(pixelRowBase + columnByte);
                int attribute = memory.read(attributeRowBase + columnByte);

                int ink = attribute & 0x07;
                int paper = (attribute >>> 3) & 0x07;
                boolean bright = (attribute & 0x40) != 0;
                boolean flash = (attribute & 0x80) != 0;

                if (flash && flashPhase) {
                    int tmp = ink;
                    ink = paper;
                    paper = tmp;
                }

                int inkArgb = paletteColor(ink, bright);
                int paperArgb = paletteColor(paper, bright);
                int targetX = BORDER_LEFT + (columnByte * 8);

                for (int bit = 0; bit < 8; bit++) {
                    boolean pixelSet = ((pixelByte << bit) & 0x80) != 0;
                    frameBuffer.setPixel(targetX + bit, targetY, pixelSet ? inkArgb : paperArgb);
                }
            }
        }

        return frameBuffer;
    }

    @Override
    public void reset() {
        borderColor = 0;
        frameRemainder = 0;
        frameCounter = 0;
        lastRefreshAddress = 0;
        pendingMaskableInterrupt = false;
    }

    @Override
    public void onTStatesElapsed(int tStates) {
        frameRemainder += tStates;
        while (frameRemainder >= T_STATES_PER_FRAME) {
            frameRemainder -= T_STATES_PER_FRAME;
            frameCounter++;
            pendingMaskableInterrupt = true;
        }
    }

    private int paletteColor(int color, boolean bright) {
        int normalized = color & 0x07;
        return bright ? BRIGHT_PALETTE[normalized] : NORMAL_PALETTE[normalized];
    }
}
