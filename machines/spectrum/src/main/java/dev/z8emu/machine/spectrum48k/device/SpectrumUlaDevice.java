package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.machine.spectrum.memory.SpectrumDisplayMemory;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.bus.io.IoSelector;
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
    private static final int DISPLAY_BYTES_PER_LINE = 32;
    private static final int DISPLAY_BYTE_COUNT = DISPLAY_HEIGHT * DISPLAY_BYTES_PER_LINE;
    private static final int FLOATING_BUS_DISPLAY_START_48K = 14_347;
    private static final int FLOATING_BUS_DISPLAY_START_128K = 14_368;
    private static final int VISIBLE_T_STATES_PER_LINE = FRAME_WIDTH / 2;
    private static final int MASKABLE_INTERRUPT_TSTATES = 32;
    private static final int INITIAL_EVENT_CAPACITY = 256;
    private static final IoSelector PORT_FE_SELECTOR = IoSelector.mask(0x00FF, 0x00FE);

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
    private int portFeDefaultValue = 0xFF;
    private long elapsedTStates;
    private long frameCounter;
    private int lastRefreshAddress;
    private int[] currentFrameEventTimes = new int[INITIAL_EVENT_CAPACITY];
    private int[] currentFrameEventColors = new int[INITIAL_EVENT_CAPACITY];
    private int currentFrameEventCount;
    private int[] completedFrameEventTimes = new int[INITIAL_EVENT_CAPACITY];
    private int[] completedFrameEventColors = new int[INITIAL_EVENT_CAPACITY];
    private int completedFrameEventCount;
    private byte[] currentFramePixelBytes = new byte[DISPLAY_BYTE_COUNT];
    private byte[] currentFrameAttributeBytes = new byte[DISPLAY_BYTE_COUNT];
    private byte[] completedFramePixelBytes = new byte[DISPLAY_BYTE_COUNT];
    private byte[] completedFrameAttributeBytes = new byte[DISPLAY_BYTE_COUNT];
    private final FrameBuffer completedFrameBuffer = new FrameBuffer(FRAME_WIDTH, FRAME_HEIGHT);
    private final FrameBuffer immediateFrameBuffer = new FrameBuffer(FRAME_WIDTH, FRAME_HEIGHT);
    private int nextDisplayByteIndex;
    private boolean completedFrameAvailable;
    private boolean completedFrameFlashPhase;
    private final int frameTStates;
    private final int scanlinesPerFrame;
    private final int tStatesPerScanline;
    private final int visibleStartScanline;
    private final int visibleStartTState;
    private final int floatingBusDisplayStartTState;

    public SpectrumUlaDevice() {
        this(T_STATES_PER_FRAME, SCANLINES_PER_FRAME);
    }

    public SpectrumUlaDevice(int frameTStates, int scanlinesPerFrame) {
        if (frameTStates <= 0) {
            throw new IllegalArgumentException("frameTStates must be positive");
        }
        if (scanlinesPerFrame <= 0) {
            throw new IllegalArgumentException("scanlinesPerFrame must be positive");
        }

        this.frameTStates = frameTStates;
        this.scanlinesPerFrame = scanlinesPerFrame;
        this.tStatesPerScanline = frameTStates / scanlinesPerFrame;
        this.visibleStartScanline = (scanlinesPerFrame - FRAME_HEIGHT) / 2;
        this.visibleStartTState = (tStatesPerScanline - VISIBLE_T_STATES_PER_LINE) / 2;
        this.floatingBusDisplayStartTState = frameTStates == 70_908
                ? FLOATING_BUS_DISPLAY_START_128K
                : FLOATING_BUS_DISPLAY_START_48K;
    }

    public static IoSelector portSelector() {
        return PORT_FE_SELECTOR;
    }

    public int readPortFe(int port, KeyboardMatrixDevice keyboard, TapeDevice tape) {
        int value = portFeDefaultValue & keyboard.readSelectedRows(port);
        return tape.applyEarBitToPortRead(value);
    }

    public int readPortFe(IoAccess access, KeyboardMatrixDevice keyboard, TapeDevice tape) {
        tape.syncToTState(access.effectiveTState());
        return readPortFe(access.address(), keyboard, tape);
    }

    public void writePortFe(int value, long eventTState, BeeperDevice beeper) {
        writePortFe(value, eventTState, beeper, null);
    }

    public void writePortFe(int value, long eventTState, BeeperDevice beeper, SpectrumDisplayMemory memory) {
        syncToTState(eventTState, memory);
        borderColor = value & 0x07;
        portFeDefaultValue = (value & 0x10) != 0 ? 0xFF : 0xBF;
        appendBorderEvent((int) (elapsedTStates % frameTStates), borderColor);
        beeper.writeFromPortFe(value);
    }

    public void writePortFe(IoAccess access, int value, BeeperDevice beeper, SpectrumDisplayMemory memory) {
        writePortFe(value, access.effectiveTState(), beeper, memory);
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
        return completedFrameAvailable ? completedFrameBuffer : immediateFrameBuffer;
    }

    public boolean maskableInterruptLineActive(long currentTState) {
        int frameOffset = Math.floorMod((int) (currentTState % frameTStates), frameTStates);
        return frameOffset < MASKABLE_INTERRUPT_TSTATES;
    }

    public FrameBuffer renderFrame(SpectrumDisplayMemory memory) {
        if (completedFrameAvailable) {
            renderCompletedFrame();
            return completedFrameBuffer;
        }

        renderImmediateFrame(memory);
        return immediateFrameBuffer;
    }

    private void renderImmediateFrame(SpectrumDisplayMemory memory) {
        paintBorderBackground(
                immediateFrameBuffer,
                currentFrameEventTimes,
                currentFrameEventColors,
                currentFrameEventCount
        );
        renderDisplayFromMemory(memory, immediateFrameBuffer, flashPhase());
    }

    private void renderDisplayFromMemory(SpectrumDisplayMemory memory, FrameBuffer target, boolean flashPhase) {
        for (int y = 0; y < DISPLAY_HEIGHT; y++) {
            for (int columnByte = 0; columnByte < DISPLAY_BYTES_PER_LINE; columnByte++) {
                int index = (y * DISPLAY_BYTES_PER_LINE) + columnByte;
                renderDisplayByte(
                        memory.readDisplayMemory(pixelAddress(y, columnByte)),
                        memory.readDisplayMemory(attributeAddress(y, columnByte)),
                        target,
                        index,
                        flashPhase
                );
            }
        }
    }

    public int readFloatingBus(SpectrumDisplayMemory memory, long currentTState) {
        int frameOffset = Math.floorMod((int) (currentTState % frameTStates), frameTStates);
        int screenOffset = frameOffset - floatingBusDisplayStartTState;
        if (screenOffset < 0) {
            return 0xFF;
        }

        int scanline = screenOffset / tStatesPerScanline;
        if (scanline < 0 || scanline >= DISPLAY_HEIGHT) {
            return 0xFF;
        }

        int lineOffset = screenOffset % tStatesPerScanline;
        if (lineOffset < 0 || lineOffset >= 128) {
            return 0xFF;
        }

        int fetchGroup = lineOffset >>> 3;
        int fetchSlot = lineOffset & 0x07;
        int columnByte = fetchGroup * 2;
        int pixelRowBase = 0x4000
                | ((scanline & 0xC0) << 5)
                | ((scanline & 0x07) << 8)
                | ((scanline & 0x38) << 2);
        int attributeRowBase = 0x5800 + ((scanline >>> 3) * 32);

        return switch (fetchSlot) {
            case 0 -> memory.readDisplayMemory(pixelRowBase + columnByte);
            case 1 -> memory.readDisplayMemory(attributeRowBase + columnByte);
            case 2 -> memory.readDisplayMemory(pixelRowBase + columnByte + 1);
            case 3 -> memory.readDisplayMemory(attributeRowBase + columnByte + 1);
            default -> 0xFF;
        };
    }

    @Override
    public void reset() {
        borderColor = 0;
        portFeDefaultValue = 0xFF;
        elapsedTStates = 0;
        frameCounter = 0;
        lastRefreshAddress = 0;
        currentFrameEventCount = 0;
        completedFrameEventCount = 0;
        nextDisplayByteIndex = 0;
        completedFrameAvailable = false;
        completedFrameFlashPhase = false;
        java.util.Arrays.fill(currentFramePixelBytes, (byte) 0);
        java.util.Arrays.fill(currentFrameAttributeBytes, (byte) 0);
        java.util.Arrays.fill(completedFramePixelBytes, (byte) 0);
        java.util.Arrays.fill(completedFrameAttributeBytes, (byte) 0);
        completedFrameBuffer.clear(paletteColor(borderColor, false));
        immediateFrameBuffer.clear(paletteColor(borderColor, false));
        appendBorderEvent(0, borderColor);
    }

    @Override
    public void onTStatesElapsed(int tStates) {
        syncToTState(elapsedTStates + tStates);
    }

    public void onTStatesElapsed(int tStates, SpectrumDisplayMemory memory) {
        syncToTState(elapsedTStates + tStates, memory);
    }

    public void syncToTState(long targetTState) {
        syncToTState(targetTState, null);
    }

    public void syncToTState(long targetTState, SpectrumDisplayMemory memory) {
        if (targetTState <= elapsedTStates) {
            return;
        }

        while (elapsedTStates < targetTState) {
            int frameOffset = (int) (elapsedTStates % frameTStates);
            int remainingInFrame = frameTStates - frameOffset;
            int chunk = (int) Math.min(targetTState - elapsedTStates, remainingInFrame);
            captureDisplayUntil(elapsedTStates + chunk, memory);
            elapsedTStates += chunk;

            if (((int) (elapsedTStates % frameTStates)) == 0) {
                completeCurrentFrame();
                frameCounter++;
            }
        }
    }

    private boolean flashPhase() {
        return ((frameCounter / 16) & 0x01) != 0;
    }

    private int paletteColor(int color, boolean bright) {
        int normalized = color & 0x07;
        return bright ? BRIGHT_PALETTE[normalized] : NORMAL_PALETTE[normalized];
    }

    private void paintBorderBackground(FrameBuffer target, int[] eventTimes, int[] eventColors, int eventCount) {
        int eventIndex = 0;
        int currentArgb = paletteColor(eventCount == 0 ? borderColor : eventColors[0], false);
        int[] pixels = target.pixels();

        for (int y = 0; y < FRAME_HEIGHT; y++) {
            int scanline = visibleStartScanline + y;
            int rowBase = y * FRAME_WIDTH;
            for (int x = 0; x < FRAME_WIDTH; x++) {
                if (x >= BORDER_LEFT
                        && x < BORDER_LEFT + DISPLAY_WIDTH
                        && y >= BORDER_TOP
                        && y < BORDER_TOP + DISPLAY_HEIGHT) {
                    continue;
                }
                int frameTState = (scanline * tStatesPerScanline)
                        + visibleStartTState
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
        byte[] pixelBytes = completedFramePixelBytes;
        completedFramePixelBytes = currentFramePixelBytes;
        currentFramePixelBytes = pixelBytes;
        byte[] attributeBytes = completedFrameAttributeBytes;
        completedFrameAttributeBytes = currentFrameAttributeBytes;
        currentFrameAttributeBytes = attributeBytes;
        java.util.Arrays.fill(currentFramePixelBytes, (byte) 0);
        java.util.Arrays.fill(currentFrameAttributeBytes, (byte) 0);
        nextDisplayByteIndex = 0;
        completedFrameAvailable = true;
        completedFrameFlashPhase = flashPhase();
        appendBorderEvent(0, borderColor);
    }

    private void captureDisplayUntil(long targetTState, SpectrumDisplayMemory memory) {
        if (memory == null) {
            return;
        }

        long frameStartTState = elapsedTStates - Math.floorMod(elapsedTStates, frameTStates);
        int targetFrameOffset = (int) Math.min(frameTStates, targetTState - frameStartTState);

        while (nextDisplayByteIndex < DISPLAY_BYTE_COUNT
                && displayFetchFrameOffset(nextDisplayByteIndex) <= targetFrameOffset) {
            int y = nextDisplayByteIndex / DISPLAY_BYTES_PER_LINE;
            int columnByte = nextDisplayByteIndex % DISPLAY_BYTES_PER_LINE;
            currentFramePixelBytes[nextDisplayByteIndex] = (byte) memory.readDisplayMemory(pixelAddress(y, columnByte));
            currentFrameAttributeBytes[nextDisplayByteIndex] = (byte) memory.readDisplayMemory(attributeAddress(y, columnByte));
            nextDisplayByteIndex++;
        }
    }

    private int displayFetchFrameOffset(int displayByteIndex) {
        int y = displayByteIndex / DISPLAY_BYTES_PER_LINE;
        int columnByte = displayByteIndex % DISPLAY_BYTES_PER_LINE;
        int fetchGroup = columnByte >>> 1;
        int fetchSlot = (columnByte & 0x01) == 0 ? 0 : 2;
        return floatingBusDisplayStartTState
                + (y * tStatesPerScanline)
                + (fetchGroup * 8)
                + fetchSlot;
    }

    private int pixelAddress(int y, int columnByte) {
        int pixelRowBase = 0x4000
                | ((y & 0xC0) << 5)
                | ((y & 0x07) << 8)
                | ((y & 0x38) << 2);
        return pixelRowBase + columnByte;
    }

    private int attributeAddress(int y, int columnByte) {
        int attributeRowBase = 0x5800 + ((y >>> 3) * DISPLAY_BYTES_PER_LINE);
        return attributeRowBase + columnByte;
    }

    private void renderCompletedFrame() {
        paintBorderBackground(
                completedFrameBuffer,
                completedFrameEventTimes,
                completedFrameEventColors,
                completedFrameEventCount
        );
        for (int index = 0; index < DISPLAY_BYTE_COUNT; index++) {
            renderDisplayByte(
                    completedFramePixelBytes[index] & 0xFF,
                    completedFrameAttributeBytes[index] & 0xFF,
                    completedFrameBuffer,
                    index,
                    completedFrameFlashPhase
            );
        }
    }

    private void renderDisplayByte(
            int pixelByte,
            int attribute,
            FrameBuffer target,
            int displayByteIndex,
            boolean flashPhase) {
        int y = displayByteIndex / DISPLAY_BYTES_PER_LINE;
        int columnByte = displayByteIndex % DISPLAY_BYTES_PER_LINE;

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
        int targetY = BORDER_TOP + y;

        for (int bit = 0; bit < 8; bit++) {
            boolean pixelSet = ((pixelByte << bit) & 0x80) != 0;
            target.setPixel(targetX + bit, targetY, pixelSet ? inkArgb : paperArgb);
        }
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
