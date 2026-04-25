package dev.z8emu.machine.cpc.device;

import dev.z8emu.machine.cpc.memory.CpcMemory;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Arrays;

public final class CpcGateArrayDevice {
    public static final int DISPLAY_WIDTH = 640;
    public static final int DISPLAY_HEIGHT = 200;
    public static final int BORDER_LEFT = 64;
    public static final int BORDER_RIGHT = 64;
    public static final int BORDER_TOP = 36;
    public static final int BORDER_BOTTOM = 36;
    public static final int FRAME_WIDTH = BORDER_LEFT + DISPLAY_WIDTH + BORDER_RIGHT;
    public static final int FRAME_HEIGHT = BORDER_TOP + DISPLAY_HEIGHT + BORDER_BOTTOM;
    public static final int DISPLAY_BYTES_PER_LINE = 80;

    private static final int T_STATES_PER_HSYNC = 256;
    private static final int INTERRUPT_HSYNC_PERIOD = 52;
    private static final int FRAME_TSTATES = T_STATES_PER_HSYNC * INTERRUPT_HSYNC_PERIOD * 6;
    private static final int DISPLAY_START_TSTATES = 112;
    private static final int DISPLAY_BYTE_TSTATES = 2;
    private static final int LOWER_RASTER_SPLIT_REFERENCE_DISPLAY_LINE = DISPLAY_HEIGHT - 7;
    private static final int INTERRUPT_COUNTER_MASK = 0x3F;
    private static final int INTERRUPT_ACK_CLEAR_MASK = 0x1F;
    private static final int BORDER_PEN_INDEX = 16;
    private static final int DEFAULT_BLACK_HARDWARE_COLOR = 20;
    private static final int INITIAL_EVENT_CAPACITY = 256;
    private static final int[] HARDWARE_PALETTE = {
            rgb(0x80, 0x80, 0x80), rgb(0x80, 0x80, 0x80), rgb(0x00, 0xFF, 0x80), rgb(0xFF, 0xFF, 0x80),
            rgb(0x00, 0x00, 0x80), rgb(0x80, 0x00, 0x80), rgb(0x00, 0x80, 0x80), rgb(0xFF, 0x80, 0x80),
            rgb(0x80, 0x00, 0x80), rgb(0xFF, 0xFF, 0x80), rgb(0xFF, 0xFF, 0x00), rgb(0xFF, 0xFF, 0xFF),
            rgb(0xFF, 0x00, 0x00), rgb(0xFF, 0x00, 0xFF), rgb(0xFF, 0x80, 0x00), rgb(0xFF, 0x80, 0xFF),
            rgb(0x00, 0x00, 0x80), rgb(0x00, 0xFF, 0x80), rgb(0x00, 0xFF, 0x00), rgb(0x00, 0xFF, 0xFF),
            rgb(0x00, 0x00, 0x00), rgb(0x00, 0x00, 0xFF), rgb(0x00, 0x80, 0x00), rgb(0x00, 0x80, 0xFF),
            rgb(0x80, 0x00, 0x80), rgb(0x80, 0xFF, 0x80), rgb(0x80, 0xFF, 0x00), rgb(0x80, 0xFF, 0xFF),
            rgb(0x80, 0x00, 0x00), rgb(0x80, 0x00, 0xFF), rgb(0x80, 0x80, 0x00), rgb(0x80, 0x80, 0xFF)
    };

    private final int[] hardwareInkByPen = new int[17];
    private final FrameBuffer frameBuffer = new FrameBuffer(FRAME_WIDTH, FRAME_HEIGHT);
    private int[] currentFrameEventTimes = new int[INITIAL_EVENT_CAPACITY];
    private int[] currentFrameEventModes = new int[INITIAL_EVENT_CAPACITY];
    private int[][] currentFrameEventInks = newEventInkState(INITIAL_EVENT_CAPACITY);
    private int currentFrameEventCount;
    private int[] completedFrameEventTimes = new int[INITIAL_EVENT_CAPACITY];
    private int[] completedFrameEventModes = new int[INITIAL_EVENT_CAPACITY];
    private int[][] completedFrameEventInks = newEventInkState(INITIAL_EVENT_CAPACITY);
    private int completedFrameEventCount;

    private int selectedPen;
    private int screenMode;
    private int interruptCounter;
    private int hsyncTStateRemainder;
    private boolean interruptRequestActive;
    private long elapsedTStates;
    private boolean completedFrameStateAvailable;

    public void reset() {
        Arrays.fill(hardwareInkByPen, DEFAULT_BLACK_HARDWARE_COLOR);
        selectedPen = 0;
        screenMode = 1;
        interruptCounter = 0;
        hsyncTStateRemainder = 0;
        interruptRequestActive = false;
        elapsedTStates = 0;
        completedFrameStateAvailable = false;
        currentFrameEventCount = 0;
        completedFrameEventCount = 0;
        appendCurrentFrameEvent(0);
        frameBuffer.clear(argbForHardwareColor(DEFAULT_BLACK_HARDWARE_COLOR));
    }

    public void writeRegister(int value, CpcMemory memory) {
        writeRegister(value, memory, elapsedTStates);
    }

    public void writeRegister(int value, CpcMemory memory, long eventTState) {
        syncRasterState(eventTState);
        int normalized = value & 0xFF;
        int function = normalized & 0xC0;
        if (function == 0x00) {
            selectedPen = (normalized & 0x10) != 0 ? BORDER_PEN_INDEX : normalized & 0x0F;
            return;
        }
        if (function == 0x40) {
            hardwareInkByPen[selectedPen] = normalized & 0x1F;
            appendCurrentFrameEvent(frameOffset());
            return;
        }
        if (function == 0x80) {
            int oldMode = screenMode;
            screenMode = normalized & 0x03;
            if (screenMode != oldMode) {
                appendCurrentFrameEvent(frameOffset());
            }
            if ((normalized & 0x10) != 0) {
                resetInterruptCounter();
            }
            memory.writeGateArrayControl(normalized);
            return;
        }
        memory.writeGateArrayControl(normalized);
    }

    public void onTStatesElapsed(int tStates) {
        onTStatesElapsed(tStates, elapsedTStates + tStates);
    }

    public void onTStatesElapsed(int tStates, long currentTState) {
        if (tStates <= 0) {
            return;
        }

        int elapsed = hsyncTStateRemainder + tStates;
        int hsyncs = elapsed / T_STATES_PER_HSYNC;
        hsyncTStateRemainder = elapsed % T_STATES_PER_HSYNC;
        for (int i = 0; i < hsyncs; i++) {
            onHsync();
        }
        syncRasterState(currentTState);
    }

    public boolean maskableInterruptLineActive() {
        return interruptRequestActive;
    }

    public void acknowledgeInterrupt() {
        interruptRequestActive = false;
        interruptCounter &= INTERRUPT_ACK_CLEAR_MASK;
    }

    public FrameBuffer renderFrame(CpcMemory memory, CpcCrtcDevice crtc) {
        if (completedFrameStateAvailable) {
            renderFrameWithLineState(memory, crtc);
            return frameBuffer;
        }

        renderImmediateFrame(memory, crtc);
        return frameBuffer;
    }

    private void renderImmediateFrame(CpcMemory memory, CpcCrtcDevice crtc) {
        frameBuffer.clear(paletteArgb(BORDER_PEN_INDEX));

        int mode = normalizedScreenMode();
        int visibleLines = Math.min(DISPLAY_HEIGHT, crtc.visibleRasterLines());
        int visibleBytes = Math.min(DISPLAY_BYTES_PER_LINE, crtc.horizontalDisplayedBytes());
        for (int y = 0; y < visibleLines; y++) {
            for (int byteColumn = 0; byteColumn < visibleBytes; byteColumn++) {
                int address = crtc.displayMemoryAddress(y, byteColumn);
                int value = memory.readDisplayMemory(address);
                renderDisplayByte(mode, value, byteColumn, y, hardwareInkByPen);
            }
        }
    }

    private void renderFrameWithLineState(CpcMemory memory, CpcCrtcDevice crtc) {
        paintFrameBorder(completedFrameEventTimes, completedFrameEventModes, completedFrameEventInks, completedFrameEventCount);

        int visibleLines = Math.min(DISPLAY_HEIGHT, crtc.visibleRasterLines());
        int visibleBytes = Math.min(DISPLAY_BYTES_PER_LINE, crtc.horizontalDisplayedBytes());
        int displayEventTop = displayEventTop();
        for (int y = 0; y < visibleLines; y++) {
            int frameLine = displayEventTop + y;
            int eventIndex = eventIndexAt(
                    completedFrameEventTimes,
                    completedFrameEventCount,
                    displayByteFrameOffset(frameLine, 0)
            );
            for (int byteColumn = 0; byteColumn < visibleBytes; byteColumn++) {
                int frameOffset = displayByteFrameOffset(frameLine, byteColumn);
                while (eventIndex + 1 < completedFrameEventCount
                        && completedFrameEventTimes[eventIndex + 1] <= frameOffset) {
                    eventIndex++;
                }
                int address = crtc.displayMemoryAddress(y, byteColumn);
                int value = memory.readDisplayMemory(address);
                int mode = completedFrameEventModes[eventIndex];
                int[] lineInks = completedFrameEventInks[eventIndex];
                renderDisplayByte(mode, value, byteColumn, y, lineInks);
            }
        }
    }

    public int screenMode() {
        return screenMode;
    }

    public int selectedPen() {
        return selectedPen;
    }

    public int hardwareInkForPen(int pen) {
        if (pen < 0 || pen >= hardwareInkByPen.length) {
            throw new IllegalArgumentException("Pen index out of range: " + pen);
        }
        return hardwareInkByPen[pen];
    }

    public int paletteArgb(int pen) {
        return argbForHardwareColor(hardwareInkForPen(pen));
    }

    public static int argbForHardwareColor(int hardwareColor) {
        return HARDWARE_PALETTE[hardwareColor & 0x1F];
    }

    private void renderDisplayByte(int mode, int value, int byteColumn, int y, int[] hardwareInkByLinePen) {
        switch (mode) {
            case 0 -> renderMode0Byte(value, byteColumn, y, hardwareInkByLinePen);
            case 2 -> renderMode2Byte(value, byteColumn, y, hardwareInkByLinePen);
            default -> renderMode1Byte(value, byteColumn, y, hardwareInkByLinePen);
        }
    }

    private void renderMode0Byte(int value, int byteColumn, int y, int[] hardwareInkByLinePen) {
        paintScaledPixel(byteColumn * 8, y, decodeMode0Pixel0(value), 4, hardwareInkByLinePen);
        paintScaledPixel((byteColumn * 8) + 4, y, decodeMode0Pixel1(value), 4, hardwareInkByLinePen);
    }

    private void renderMode1Byte(int value, int byteColumn, int y, int[] hardwareInkByLinePen) {
        int x = byteColumn * 8;
        paintScaledPixel(x, y, decodeMode1Pixel(value, 7, 3), 2, hardwareInkByLinePen);
        paintScaledPixel(x + 2, y, decodeMode1Pixel(value, 6, 2), 2, hardwareInkByLinePen);
        paintScaledPixel(x + 4, y, decodeMode1Pixel(value, 5, 1), 2, hardwareInkByLinePen);
        paintScaledPixel(x + 6, y, decodeMode1Pixel(value, 4, 0), 2, hardwareInkByLinePen);
    }

    private void renderMode2Byte(int value, int byteColumn, int y, int[] hardwareInkByLinePen) {
        int x = byteColumn * 8;
        for (int bit = 7; bit >= 0; bit--) {
            int pen = (value >>> bit) & 0x01;
            setDisplayPixel(x + (7 - bit), y, pen, hardwareInkByLinePen);
        }
    }

    private void paintScaledPixel(int x, int y, int pen, int width, int[] hardwareInkByLinePen) {
        for (int dx = 0; dx < width; dx++) {
            setDisplayPixel(x + dx, y, pen, hardwareInkByLinePen);
        }
    }

    private void setDisplayPixel(int x, int y, int pen, int[] hardwareInkByLinePen) {
        frameBuffer.setPixel(BORDER_LEFT + x, BORDER_TOP + y, argbForHardwareColor(hardwareInkByLinePen[pen]));
    }

    private int normalizedScreenMode() {
        return screenMode == 3 ? 1 : screenMode;
    }

    private void onHsync() {
        interruptCounter = (interruptCounter + 1) & INTERRUPT_COUNTER_MASK;
        if (interruptCounter == INTERRUPT_HSYNC_PERIOD) {
            interruptCounter = 0;
            interruptRequestActive = true;
        }
    }

    private void resetInterruptCounter() {
        interruptCounter = 0;
        interruptRequestActive = false;
    }

    private void syncRasterState(long targetTState) {
        if (targetTState <= elapsedTStates) {
            return;
        }

        while (elapsedTStates < targetTState) {
            int frameOffset = (int) (elapsedTStates % FRAME_TSTATES);
            int remainingInFrame = FRAME_TSTATES - frameOffset;
            int chunk = (int) Math.min(targetTState - elapsedTStates, remainingInFrame);
            elapsedTStates += chunk;

            if ((elapsedTStates % FRAME_TSTATES) == 0) {
                completeCurrentRasterState();
            }
        }
    }

    private void completeCurrentRasterState() {
        int[] times = completedFrameEventTimes;
        completedFrameEventTimes = currentFrameEventTimes;
        currentFrameEventTimes = times;

        int[] modes = completedFrameEventModes;
        completedFrameEventModes = currentFrameEventModes;
        currentFrameEventModes = modes;

        int[][] inks = completedFrameEventInks;
        completedFrameEventInks = currentFrameEventInks;
        currentFrameEventInks = inks;

        completedFrameEventCount = currentFrameEventCount;
        currentFrameEventCount = 0;
        appendCurrentFrameEvent(0);
        completedFrameStateAvailable = true;
    }

    private void paintFrameBorder(int[] eventTimes, int[] eventModes, int[][] eventInks, int eventCount) {
        if (eventCount == 0) {
            frameBuffer.clear(paletteArgb(BORDER_PEN_INDEX));
            return;
        }

        int eventIndex = 0;
        for (int y = 0; y < FRAME_HEIGHT; y++) {
            int rowBase = y * FRAME_WIDTH;
            for (int x = 0; x < FRAME_WIDTH; x++) {
                if (x >= BORDER_LEFT
                        && x < BORDER_LEFT + DISPLAY_WIDTH
                        && y >= BORDER_TOP
                        && y < BORDER_TOP + DISPLAY_HEIGHT) {
                    continue;
                }
                int frameOffset = (y * T_STATES_PER_HSYNC) + DISPLAY_START_TSTATES + (x / 4);
                while (eventIndex + 1 < eventCount && eventTimes[eventIndex + 1] <= frameOffset) {
                    eventIndex++;
                }
                frameBuffer.pixels()[rowBase + x] = argbForHardwareColor(eventInks[eventIndex][BORDER_PEN_INDEX]);
            }
        }
    }

    private void appendCurrentFrameEvent(int frameOffset) {
        int normalizedFrameOffset = Math.floorMod(frameOffset, FRAME_TSTATES);
        if (currentFrameEventCount > 0) {
            int lastIndex = currentFrameEventCount - 1;
            if (currentFrameEventTimes[lastIndex] == normalizedFrameOffset) {
                storeCurrentFrameEvent(lastIndex, normalizedFrameOffset);
                return;
            }
            if (currentFrameEventModes[lastIndex] == normalizedScreenMode()
                    && Arrays.equals(currentFrameEventInks[lastIndex], hardwareInkByPen)) {
                return;
            }
        }
        ensureCurrentFrameEventCapacity(currentFrameEventCount + 1);
        storeCurrentFrameEvent(currentFrameEventCount, normalizedFrameOffset);
        currentFrameEventCount++;
    }

    private void storeCurrentFrameEvent(int index, int frameOffset) {
        currentFrameEventTimes[index] = frameOffset;
        currentFrameEventModes[index] = normalizedScreenMode();
        System.arraycopy(hardwareInkByPen, 0, currentFrameEventInks[index], 0, hardwareInkByPen.length);
    }

    private void ensureCurrentFrameEventCapacity(int requiredCapacity) {
        if (requiredCapacity <= currentFrameEventTimes.length) {
            return;
        }
        int newCapacity = Math.max(requiredCapacity, currentFrameEventTimes.length * 2);
        currentFrameEventTimes = Arrays.copyOf(currentFrameEventTimes, newCapacity);
        currentFrameEventModes = Arrays.copyOf(currentFrameEventModes, newCapacity);
        currentFrameEventInks = Arrays.copyOf(currentFrameEventInks, newCapacity);
        for (int i = 0; i < currentFrameEventInks.length; i++) {
            if (currentFrameEventInks[i] == null) {
                currentFrameEventInks[i] = new int[hardwareInkByPen.length];
            }
        }
    }

    private int frameOffset() {
        return (int) Math.floorMod(elapsedTStates, FRAME_TSTATES);
    }

    private static int displayByteFrameOffset(int frameLine, int byteColumn) {
        return (frameLine * T_STATES_PER_HSYNC) + DISPLAY_START_TSTATES + (byteColumn * DISPLAY_BYTE_TSTATES);
    }

    private int displayEventTop() {
        int eventTop = BORDER_TOP;
        for (int i = 0; i < completedFrameEventCount; i++) {
            int line = completedFrameEventTimes[i] / T_STATES_PER_HSYNC;
            if (line >= 200 && line < 280 && completedFrameEventModes[i] == 1) {
                // Approximation until CRTC display-enable timing is modeled.
                eventTop = line - LOWER_RASTER_SPLIT_REFERENCE_DISPLAY_LINE;
            }
        }
        return eventTop;
    }

    private static int eventIndexAt(int[] eventTimes, int eventCount, int frameOffset) {
        int eventIndex = 0;
        while (eventIndex + 1 < eventCount && eventTimes[eventIndex + 1] <= frameOffset) {
            eventIndex++;
        }
        return eventIndex;
    }

    private static int[][] newEventInkState(int capacity) {
        int[][] eventInks = new int[capacity][17];
        return eventInks;
    }

    private static int decodeMode0Pixel0(int value) {
        return ((value & 0x80) >>> 7)
                | ((value & 0x08) >>> 2)
                | ((value & 0x20) >>> 3)
                | ((value & 0x02) << 2);
    }

    private static int decodeMode0Pixel1(int value) {
        return ((value & 0x40) >>> 6)
                | ((value & 0x04) >>> 1)
                | ((value & 0x10) >>> 2)
                | ((value & 0x01) << 3);
    }

    private static int decodeMode1Pixel(int value, int lowBit, int highBit) {
        return ((value >>> lowBit) & 0x01) | (((value >>> highBit) & 0x01) << 1);
    }

    private static int rgb(int red, int green, int blue) {
        return 0xFF000000 | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }
}
