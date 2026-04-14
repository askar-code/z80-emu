package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.machine.spectrum.memory.SpectrumDisplayMemory;
import dev.z8emu.platform.device.TimedDevice;
import dev.z8emu.platform.video.FrameBuffer;

public final class SpectrumUlaDevice implements TimedDevice {
    public static final int T_STATES_PER_FRAME = 69_888;
    public static final int SCANLINES_PER_FRAME = 312;
    public static final int DISPLAY_WIDTH = 256;
    public static final int DISPLAY_HEIGHT = 192;
    public static final int BORDER_LEFT = 48;
    public static final int BORDER_RIGHT = 48;
    public static final int BORDER_TOP = 48;
    public static final int BORDER_BOTTOM = 56;
    public static final int FRAME_WIDTH = BORDER_LEFT + DISPLAY_WIDTH + BORDER_RIGHT;
    public static final int FRAME_HEIGHT = BORDER_TOP + DISPLAY_HEIGHT + BORDER_BOTTOM;
    private static final int T_STATES_PER_SCANLINE = T_STATES_PER_FRAME / SCANLINES_PER_FRAME;
    private static final int VISIBLE_START_SCANLINE = (SCANLINES_PER_FRAME - FRAME_HEIGHT) / 2;
    private static final int VISIBLE_T_STATES_PER_LINE = FRAME_WIDTH / 2;
    private static final int VISIBLE_START_T_STATE = (T_STATES_PER_SCANLINE - VISIBLE_T_STATES_PER_LINE) / 2;
    private static final int INITIAL_EVENT_CAPACITY = 256;

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
    private long elapsedTStates;
    private long frameCounter;
    private int lastRefreshAddress;
    private boolean pendingMaskableInterrupt;
    private int[] currentFrameEventTimes = new int[INITIAL_EVENT_CAPACITY];
    private int[] currentFrameEventColors = new int[INITIAL_EVENT_CAPACITY];
    private int currentFrameEventCount;
    private int[] completedFrameEventTimes = new int[INITIAL_EVENT_CAPACITY];
    private int[] completedFrameEventColors = new int[INITIAL_EVENT_CAPACITY];
    private int completedFrameEventCount;
    private final FrameBuffer frameBuffer = new FrameBuffer(FRAME_WIDTH, FRAME_HEIGHT);

    public int readPortFe(int port, KeyboardMatrixDevice keyboard, TapeDevice tape) {
        int value = keyboard.readSelectedRows(port);
        return tape.applyEarBitToPortRead(value);
    }

    public void writePortFe(int value, long eventTState, BeeperDevice beeper) {
        syncToTState(eventTState);
        borderColor = value & 0x07;
        appendBorderEvent((int) (elapsedTStates % T_STATES_PER_FRAME), borderColor);
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

    public FrameBuffer renderFrame(SpectrumDisplayMemory memory) {
        paintBorderBackground();

        boolean flashPhase = ((frameCounter / 16) & 0x01) != 0;

        for (int y = 0; y < DISPLAY_HEIGHT; y++) {
            int pixelRowBase = 0x4000
                    | ((y & 0xC0) << 5)
                    | ((y & 0x07) << 8)
                    | ((y & 0x38) << 2);
            int attributeRowBase = 0x5800 + ((y >>> 3) * 32);
            int targetY = BORDER_TOP + y;

            for (int columnByte = 0; columnByte < 32; columnByte++) {
                int pixelByte = memory.readDisplayMemory(pixelRowBase + columnByte);
                int attribute = memory.readDisplayMemory(attributeRowBase + columnByte);

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
        elapsedTStates = 0;
        frameCounter = 0;
        lastRefreshAddress = 0;
        pendingMaskableInterrupt = false;
        currentFrameEventCount = 0;
        completedFrameEventCount = 0;
        appendBorderEvent(0, borderColor);
    }

    @Override
    public void onTStatesElapsed(int tStates) {
        syncToTState(elapsedTStates + tStates);
    }

    public void syncToTState(long targetTState) {
        if (targetTState <= elapsedTStates) {
            return;
        }

        while (elapsedTStates < targetTState) {
            int frameOffset = (int) (elapsedTStates % T_STATES_PER_FRAME);
            int remainingInFrame = T_STATES_PER_FRAME - frameOffset;
            int chunk = (int) Math.min(targetTState - elapsedTStates, remainingInFrame);
            elapsedTStates += chunk;

            if (((int) (elapsedTStates % T_STATES_PER_FRAME)) == 0) {
                completeCurrentFrame();
                frameCounter++;
                pendingMaskableInterrupt = true;
            }
        }
    }

    private int paletteColor(int color, boolean bright) {
        int normalized = color & 0x07;
        return bright ? BRIGHT_PALETTE[normalized] : NORMAL_PALETTE[normalized];
    }

    private void paintBorderBackground() {
        int[] eventTimes = frameCounter == 0 ? currentFrameEventTimes : completedFrameEventTimes;
        int[] eventColors = frameCounter == 0 ? currentFrameEventColors : completedFrameEventColors;
        int eventCount = frameCounter == 0 ? currentFrameEventCount : completedFrameEventCount;

        int eventIndex = 0;
        int currentArgb = paletteColor(eventColors[0], false);
        int[] pixels = frameBuffer.pixels();

        for (int y = 0; y < FRAME_HEIGHT; y++) {
            int scanline = VISIBLE_START_SCANLINE + y;
            int rowBase = y * FRAME_WIDTH;
            for (int x = 0; x < FRAME_WIDTH; x++) {
                int frameTState = (scanline * T_STATES_PER_SCANLINE)
                        + VISIBLE_START_T_STATE
                        + (x >>> 1);
                while (eventIndex + 1 < eventCount && eventTimes[eventIndex + 1] <= frameTState) {
                    eventIndex++;
                    currentArgb = paletteColor(eventColors[eventIndex], false);
                }
                pixels[rowBase + x] = currentArgb;
            }
        }
    }

    private void completeCurrentFrame() {
        int[] times = completedFrameEventTimes;
        completedFrameEventTimes = currentFrameEventTimes;
        currentFrameEventTimes = times;

        int[] colors = completedFrameEventColors;
        completedFrameEventColors = currentFrameEventColors;
        currentFrameEventColors = colors;

        completedFrameEventCount = currentFrameEventCount;
        currentFrameEventCount = 0;
        appendBorderEvent(0, borderColor);
    }

    private void appendBorderEvent(int frameOffset, int color) {
        if (currentFrameEventCount > 0) {
            int lastIndex = currentFrameEventCount - 1;
            if (currentFrameEventTimes[lastIndex] == frameOffset) {
                currentFrameEventColors[lastIndex] = color;
                return;
            }
            if (currentFrameEventColors[lastIndex] == color) {
                return;
            }
        }
        ensureEventCapacity(currentFrameEventCount + 1);
        currentFrameEventTimes[currentFrameEventCount] = frameOffset;
        currentFrameEventColors[currentFrameEventCount] = color;
        currentFrameEventCount++;
    }

    private void ensureEventCapacity(int requiredCapacity) {
        if (requiredCapacity <= currentFrameEventTimes.length) {
            return;
        }
        int newCapacity = Math.max(requiredCapacity, currentFrameEventTimes.length * 2);
        currentFrameEventTimes = java.util.Arrays.copyOf(currentFrameEventTimes, newCapacity);
        currentFrameEventColors = java.util.Arrays.copyOf(currentFrameEventColors, newCapacity);
    }
}
