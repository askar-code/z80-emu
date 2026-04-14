package dev.z8emu.machine.radio86rk.device;

import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

public final class Radio86VideoDevice {
    private static final int ATTR_HIGHLIGHT = 0x01;
    private static final int ATTR_BLINK = 0x02;
    private static final int ATTR_GPA = 0x0C;
    private static final int ATTR_REVERSE = 0x10;
    private static final int ATTR_UNDERLINE = 0x20;
    private static final int ATTR_SUPPRESS = 0x40;

    private static final int CA_H = 0x01;
    private static final int CA_B = 0x02;
    private static final int CA_LTEN = 0x01;
    private static final int CA_VSP = 0x02;

    private static final int SCC_END_OF_ROW = 0xF0;
    private static final int SCC_END_OF_ROW_DMA = 0xF1;
    private static final int SCC_END_OF_SCREEN = 0xF2;
    private static final int SCC_END_OF_SCREEN_DMA = 0xF3;

    private static final int STATUS_FIFO_OVERRUN = 0x01;
    private static final int STATUS_DATA_UNDERRUN = 0x02;
    private static final int CURSOR_BLINKING_REVERSE_VIDEO = 0;
    private static final int CURSOR_BLINKING_UNDERLINE = 1;
    private static final int CURSOR_STEADY_REVERSE_VIDEO = 2;
    private static final int CURSOR_STEADY_UNDERLINE = 3;
    private static final int CURSOR_BLINK_HALF_PERIOD_FRAMES = 7;
    private static final int CHAR_BLINK_HALF_PERIOD_FRAMES = 32;

    private static final int[][] CHARACTER_ATTRIBUTE = {
            {2, 2, 4, 4, 2, 4, 4, 4, 2, 4, 4, 0, 2, 0, 0, 0},
            {8, 12, 8, 12, 1, 12, 8, 1, 1, 4, 1, 0, 2, 0, 0, 0},
            {4, 4, 2, 2, 4, 4, 4, 2, 2, 4, 4, 0, 2, 0, 0, 0}
    };

    public static final int T_STATES_PER_FRAME = 35_556;
    public static final int TOTAL_COLUMNS = 78;
    public static final int TOTAL_ROWS = 30;
    public static final int VISIBLE_COLUMNS = 64;
    public static final int VISIBLE_ROWS = 25;
    public static final int BORDER_COLUMNS = (TOTAL_COLUMNS - VISIBLE_COLUMNS) / 2;
    public static final int BORDER_TOP_ROWS = 3;
    public static final int CELL_WIDTH = 6;
    public static final int CELL_HEIGHT = 10;
    public static final int FRAME_WIDTH = VISIBLE_COLUMNS * CELL_WIDTH;
    public static final int FRAME_HEIGHT = VISIBLE_ROWS * CELL_HEIGHT;
    public static final int VISIBLE_OFFSET = (BORDER_TOP_ROWS * TOTAL_COLUMNS) + BORDER_COLUMNS;

    public static final int STATUS_INTERRUPT_ENABLE = 0x40;
    public static final int STATUS_INTERRUPT_REQUEST = 0x20;
    public static final int STATUS_VIDEO_ENABLE = 0x04;
    public static final int STATUS_IMPROPER_COMMAND = 0x08;

    private static final int COLOR_BACKGROUND = 0xFF000000;
    private static final int COLOR_FOREGROUND = 0xFFA0A0A0;

    private final Radio86DmaDevice dma;
    private final FrameBuffer frameBuffer = new FrameBuffer(FRAME_WIDTH, FRAME_HEIGHT);
    private final Queue<Integer> pendingReadData = new ArrayDeque<>();

    private int statusRegister;
    private int commandRegister;
    private int expectedParameterCount;
    private int parameterIndex;
    private int charactersPerRow = TOTAL_COLUMNS;
    private int characterRowsPerFrame = TOTAL_ROWS;
    private int scanLinesPerRow = CELL_HEIGHT;
    private boolean offsetLineCounter;
    private boolean visibleFieldAttribute;
    private int underlineLine;
    private int cursorFormat;
    private int cursorAddress;
    private int lightPenAddress;
    private int frameTStates;
    private long frameCounter;

    public Radio86VideoDevice(Radio86DmaDevice dma) {
        this.dma = dma;
        reset();
    }

    public void reset() {
        pendingReadData.clear();
        statusRegister = 0;
        commandRegister = 0;
        expectedParameterCount = 0;
        parameterIndex = 0;
        charactersPerRow = TOTAL_COLUMNS;
        characterRowsPerFrame = TOTAL_ROWS;
        scanLinesPerRow = CELL_HEIGHT;
        offsetLineCounter = false;
        visibleFieldAttribute = false;
        underlineLine = scanLinesPerRow - 1;
        cursorFormat = CURSOR_STEADY_UNDERLINE;
        cursorAddress = 0;
        lightPenAddress = 0;
        frameTStates = 0;
        frameCounter = 0;
        frameBuffer.clear(COLOR_BACKGROUND);
    }

    public int readRegister(int registerIndex) {
        if ((registerIndex & 0x01) == 0) {
            return pendingReadData.isEmpty() ? 0x00 : pendingReadData.poll();
        }

        int value = statusRegister;
        statusRegister &= STATUS_INTERRUPT_ENABLE | STATUS_VIDEO_ENABLE;
        return value & 0xFF;
    }

    public void writeRegister(int registerIndex, int value) {
        int byteValue = value & 0xFF;
        if ((registerIndex & 0x01) == 0) {
            writeParameter(byteValue);
        } else {
            writeCommand(byteValue);
        }
    }

    public int charactersPerRow() {
        return charactersPerRow;
    }

    public int characterRowsPerFrame() {
        return characterRowsPerFrame;
    }

    public int scanLinesPerRow() {
        return scanLinesPerRow;
    }

    public int cursorAddress() {
        return cursorAddress;
    }

    public int cursorFormat() {
        return cursorFormat;
    }

    public long frameCounter() {
        return frameCounter;
    }

    public boolean videoEnabled() {
        return (statusRegister & STATUS_VIDEO_ENABLE) != 0;
    }

    public boolean interruptsEnabled() {
        return (statusRegister & STATUS_INTERRUPT_ENABLE) != 0;
    }

    public int statusRegister() {
        return statusRegister & 0xFF;
    }

    public void onTStatesElapsed(int tStates) {
        if (tStates <= 0) {
            return;
        }

        frameTStates += tStates;
        while (frameTStates >= T_STATES_PER_FRAME) {
            frameTStates -= T_STATES_PER_FRAME;
            frameCounter++;
            if (videoEnabled() && interruptsEnabled()) {
                statusRegister |= STATUS_INTERRUPT_REQUEST;
            }
        }
    }

    public FrameBuffer renderFrame(Radio86Memory memory) {
        frameBuffer.clear(COLOR_BACKGROUND);
        if (!videoEnabled() || !dma.channelEnabled(Radio86DmaDevice.CHANNEL_VIDEO)) {
            return frameBuffer;
        }

        int screenBase = dma.channelBaseAddress(Radio86DmaDevice.CHANNEL_VIDEO);
        int screenLength = dma.channelTransferLength(Radio86DmaDevice.CHANNEL_VIDEO);
        int screenColumns = Math.max(1, charactersPerRow);
        int pointer = 0;
        int fieldAttr = 0;
        boolean endOfScreen = false;

        if (screenLength < screenColumns * TOTAL_ROWS) {
            statusRegister |= STATUS_DATA_UNDERRUN;
        }

        for (int row = 0; row < TOTAL_ROWS; row++) {
            boolean endOfRow = endOfScreen;
            for (int column = 0; column < screenColumns; column++) {
                Cell cell = decodeCell(memory, screenBase, screenLength, pointer, row, column, fieldAttr, endOfRow, endOfScreen);
                pointer = cell.nextPointer();
                fieldAttr = cell.fieldAttr();
                endOfRow = cell.endOfRow();
                endOfScreen = cell.endOfScreen();

                if (row < BORDER_TOP_ROWS || row >= BORDER_TOP_ROWS + VISIBLE_ROWS) {
                    continue;
                }
                if (column < BORDER_COLUMNS || column >= BORDER_COLUMNS + VISIBLE_COLUMNS) {
                    continue;
                }

                int targetX = (column - BORDER_COLUMNS) * CELL_WIDTH;
                int targetY = (row - BORDER_TOP_ROWS) * CELL_HEIGHT;
                renderCharacter(targetX, targetY, cell.charCode(), cell.attr());
                if (isCursorCell(row, column) && cursorVisibleThisFrame()) {
                    renderCursor(targetX, targetY);
                }
            }
        }
        return frameBuffer;
    }

    private void writeCommand(int command) {
        commandRegister = command & 0xFF;
        pendingReadData.clear();

        if ((commandRegister & 0xE0) == 0x00) {
            expectedParameterCount = 4;
            parameterIndex = 0;
            statusRegister &= ~(STATUS_VIDEO_ENABLE | STATUS_IMPROPER_COMMAND);
            return;
        }
        if ((commandRegister & 0xE0) == 0x20) {
            expectedParameterCount = 0;
            parameterIndex = 0;
            statusRegister |= STATUS_VIDEO_ENABLE | STATUS_INTERRUPT_ENABLE;
            return;
        }
        if ((commandRegister & 0xE0) == 0x40) {
            expectedParameterCount = 0;
            parameterIndex = 0;
            statusRegister &= ~STATUS_VIDEO_ENABLE;
            return;
        }
        if ((commandRegister & 0xE0) == 0x60) {
            expectedParameterCount = 0;
            parameterIndex = 0;
            pendingReadData.add(lightPenAddress & 0xFF);
            pendingReadData.add((lightPenAddress >>> 8) & 0xFF);
            return;
        }
        if ((commandRegister & 0xE0) == 0x80) {
            expectedParameterCount = 2;
            parameterIndex = 0;
            return;
        }
        if ((commandRegister & 0xE0) == 0xA0) {
            expectedParameterCount = 0;
            parameterIndex = 0;
            statusRegister |= STATUS_INTERRUPT_ENABLE;
            return;
        }
        if ((commandRegister & 0xE0) == 0xC0) {
            expectedParameterCount = 0;
            parameterIndex = 0;
            statusRegister &= ~STATUS_INTERRUPT_ENABLE;
            return;
        }
        if ((commandRegister & 0xE0) == 0xE0) {
            expectedParameterCount = 0;
            parameterIndex = 0;
            frameTStates = 0;
            return;
        }

        statusRegister |= STATUS_IMPROPER_COMMAND;
    }

    private void writeParameter(int value) {
        if (expectedParameterCount == 0) {
            statusRegister |= STATUS_IMPROPER_COMMAND;
            return;
        }

        if ((commandRegister & 0xE0) == 0x00) {
            applyResetParameter(parameterIndex, value);
        } else if ((commandRegister & 0xE0) == 0x80) {
            applyCursorParameter(parameterIndex, value);
        }

        parameterIndex++;
        if (parameterIndex >= expectedParameterCount) {
            expectedParameterCount = 0;
            parameterIndex = 0;
        }
    }

    private void applyResetParameter(int index, int value) {
        switch (index) {
            case 0 -> charactersPerRow = (value & 0x7F) + 1;
            case 1 -> characterRowsPerFrame = (value & 0x3F) + 1;
            case 2 -> {
                underlineLine = (value >>> 4) & 0x0F;
                scanLinesPerRow = (value & 0x0F) + 1;
            }
            case 3 -> {
                offsetLineCounter = (value & 0x80) != 0;
                visibleFieldAttribute = (value & 0x40) != 0;
                cursorFormat = (value >>> 4) & 0x03;
            }
            default -> {
            }
        }
    }

    private void applyCursorParameter(int index, int value) {
        if (index == 0) {
            cursorAddress = (cursorAddress & 0xFF00) | (value & 0xFF);
        } else if (index == 1) {
            cursorAddress = ((value & 0xFF) << 8) | (cursorAddress & 0x00FF);
        }
    }

    private void renderCharacter(int targetX, int targetY, int charCode, int attr) {
        for (int line = 0; line < CELL_HEIGHT; line++) {
            int rowBits = glyphRow(charCode, line, attr);
            for (int column = 0; column < CELL_WIDTH; column++) {
                if (((rowBits >>> (CELL_WIDTH - 1 - column)) & 0x01) != 0) {
                    frameBuffer.setPixel(targetX + column, targetY + line, (attr & ATTR_HIGHLIGHT) != 0 ? 0xFFFFFFFF : COLOR_FOREGROUND);
                }
            }
        }
    }

    private void renderCursor(int targetX, int targetY) {
        if (isUnderlineCursor()) {
            int cursorLine = Math.min(CELL_HEIGHT - 1, underlineLine + 1);
            for (int column = 0; column < CELL_WIDTH; column++) {
                frameBuffer.setPixel(targetX + column, targetY + cursorLine, COLOR_FOREGROUND);
            }
            return;
        }

        for (int line = 0; line < CELL_HEIGHT; line++) {
            for (int column = 0; column < CELL_WIDTH; column++) {
                int x = targetX + column;
                int y = targetY + line;
                int pixelIndex = (y * frameBuffer.width()) + x;
                frameBuffer.pixels()[pixelIndex] =
                        frameBuffer.pixels()[pixelIndex] == COLOR_FOREGROUND ? COLOR_BACKGROUND : COLOR_FOREGROUND;
            }
        }
    }

    private boolean isCursorCell(int row, int column) {
        int cursorColumn = cursorAddress & 0xFF;
        int cursorRow = (cursorAddress >>> 8) & 0xFF;
        return cursorRow == row && cursorColumn == column;
    }

    private boolean cursorVisibleThisFrame() {
        if (!isBlinkingCursor()) {
            return true;
        }
        return ((frameCounter / CURSOR_BLINK_HALF_PERIOD_FRAMES) & 0x01) == 0;
    }

    private boolean isBlinkingCursor() {
        return cursorFormat == CURSOR_BLINKING_REVERSE_VIDEO || cursorFormat == CURSOR_BLINKING_UNDERLINE;
    }

    private boolean isUnderlineCursor() {
        return cursorFormat == CURSOR_BLINKING_UNDERLINE || cursorFormat == CURSOR_STEADY_UNDERLINE;
    }

    private int glyphRow(int charCode, int rasterLine, int attr) {
        int adjustedRasterLine = offsetLineCounter ? Math.max(0, rasterLine - 1) : rasterLine;
        if (rasterLine == 0 || rasterLine == CELL_HEIGHT - 1) {
            return 0;
        }
        int rawRow = Radio86CharacterGenerator.row(charCode, adjustedRasterLine - 1);
        if (Radio86CharacterGenerator.activeLowEncoding()) {
            rawRow ^= 0xFF;
        }
        int rowBits = rawRow & 0x3F;
        if ((attr & ATTR_SUPPRESS) != 0) {
            return 0;
        }
        if ((attr & ATTR_UNDERLINE) != 0 && rasterLine == Math.min(CELL_HEIGHT - 1, underlineLine + 1)) {
            rowBits = 0x3F;
        }
        if ((attr & ATTR_BLINK) != 0 && charBlinkVisible()) {
            rowBits = 0;
        }
        if ((attr & ATTR_REVERSE) != 0) {
            rowBits ^= 0x3F;
        }
        return rowBits;
    }

    private boolean charBlinkVisible() {
        return ((frameCounter / CHAR_BLINK_HALF_PERIOD_FRAMES) & 0x01) == 0;
    }

    private Cell decodeCell(
            Radio86Memory memory,
            int screenBase,
            int screenLength,
            int pointer,
            int row,
            int column,
            int fieldAttr,
            boolean endOfRow,
            boolean endOfScreen
    ) {
        if (endOfScreen || endOfRow || pointer >= screenLength) {
            return new Cell(0x20, ATTR_SUPPRESS, pointer, fieldAttr, endOfRow, endOfScreen);
        }

        int data = memory.peekRam(screenBase + pointer);
        int nextPointer = pointer + 1;

        if ((data & 0xC0) == 0x80) {
            int nextFieldAttr = data & (ATTR_HIGHLIGHT | ATTR_BLINK | ATTR_GPA | ATTR_REVERSE | ATTR_UNDERLINE);
            if (visibleFieldAttribute) {
                return new Cell(0x20, nextFieldAttr | ATTR_SUPPRESS, nextPointer, nextFieldAttr, endOfRow, endOfScreen);
            }
            return new Cell(0x20, 0, nextPointer, nextFieldAttr, endOfRow, endOfScreen);
        }

        if (data >= SCC_END_OF_ROW) {
            return switch (data) {
                case SCC_END_OF_ROW, SCC_END_OF_ROW_DMA -> new Cell(0x20, ATTR_SUPPRESS, nextPointer, fieldAttr, true, endOfScreen);
                case SCC_END_OF_SCREEN, SCC_END_OF_SCREEN_DMA -> new Cell(0x20, ATTR_SUPPRESS, nextPointer, fieldAttr, true, true);
                default -> new Cell(0x20, 0, nextPointer, fieldAttr, endOfRow, endOfScreen);
            };
        }

        int attr = fieldAttr;
        if (data >= 0xC0) {
            int cccc = (data >>> 2) & 0x0F;
            int lineClass = rasterClassForAttributes();
            int ca = CHARACTER_ATTRIBUTE[lineClass][cccc];
            if ((data & CA_H) != 0) {
                attr |= ATTR_HIGHLIGHT;
            }
            if ((data & CA_B) != 0) {
                attr |= ATTR_BLINK;
            }
            if ((ca & CA_LTEN) != 0) {
                attr |= ATTR_UNDERLINE;
            }
            if ((ca & CA_VSP) != 0) {
                attr |= ATTR_BLINK;
            }
        }
        return new Cell(data & 0x7F, attr, nextPointer, fieldAttr, endOfRow, endOfScreen);
    }

    private int rasterClassForAttributes() {
        return 1;
    }

    private record Cell(int charCode, int attr, int nextPointer, int fieldAttr, boolean endOfRow, boolean endOfScreen) {
    }
}
