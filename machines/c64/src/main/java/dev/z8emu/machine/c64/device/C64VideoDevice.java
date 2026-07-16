package dev.z8emu.machine.c64.device;

import dev.z8emu.machine.c64.C64Memory;
import dev.z8emu.platform.device.TimedDevice;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Arrays;
import java.util.Objects;

public final class C64VideoDevice implements TimedDevice {
    public static final int TEXT_COLUMNS = 40;
    public static final int TEXT_ROWS = 25;
    public static final int CELL_SIZE = 8;
    public static final int FRAME_WIDTH = 384;
    public static final int FRAME_HEIGHT = 272;
    public static final int BORDER_LEFT = (FRAME_WIDTH - TEXT_COLUMNS * CELL_SIZE) / 2;
    public static final int BORDER_TOP = (FRAME_HEIGHT - TEXT_ROWS * CELL_SIZE) / 2;
    public static final int CYCLES_PER_LINE = 63;
    public static final int LINES_PER_FRAME = 312;

    private static final int FRAME_CYCLES = CYCLES_PER_LINE * LINES_PER_FRAME;
    private static final int WINDOW_WIDTH = TEXT_COLUMNS * CELL_SIZE;
    private static final int WINDOW_HEIGHT = TEXT_ROWS * CELL_SIZE;
    private static final int SPRITE_COUNT = 8;
    private static final int SPRITE_WIDTH = 24;
    private static final int SPRITE_HEIGHT = 21;
    private static final int[] PALETTE = {
            0xFF000000, // black
            0xFFFFFFFF, // white
            0xFF813338, // red
            0xFF75CEC8, // cyan
            0xFF8E3C97, // purple
            0xFF56AC4D, // green
            0xFF2E2C9B, // blue
            0xFFEDF171, // yellow
            0xFF8E5029, // orange
            0xFF553800, // brown
            0xFFC46C71, // light red
            0xFF4A4A4A, // dark grey
            0xFF7B7B7B, // grey
            0xFFA9FF9F, // light green
            0xFF706DEB, // light blue
            0xFFB2B2B2, // light grey
    };

    private final C64Memory memory;
    private final C64CiaDevice cia2;
    private final int[] registers = new int[0x2F];
    private final FrameBuffer frame = new FrameBuffer(FRAME_WIDTH, FRAME_HEIGHT);
    private final boolean[] foregroundMask = new boolean[WINDOW_WIDTH * WINDOW_HEIGHT];
    private final int[] spriteMask = new int[WINDOW_WIDTH * WINDOW_HEIGHT];

    private int interruptLatch;
    private int spriteSpriteLatch;
    private int spriteDataLatch;
    private int rasterCompare;
    private int rasterCompareCycle;
    private int frameCycle;
    private int lineCursor;
    private boolean frameCompleted;

    public C64VideoDevice(C64Memory memory) {
        this(memory, null);
    }

    public C64VideoDevice(C64Memory memory, C64CiaDevice cia2) {
        this.memory = Objects.requireNonNull(memory, "memory");
        this.cia2 = cia2;
    }

    public int readRegister(int offset) {
        int registerIndex = offset & 0x3F;
        if (registerIndex >= registers.length) {
            return 0xFF;
        }
        return switch (registerIndex) {
            case 0x11 -> (registers[registerIndex] & 0x7F) | ((rasterLine() & 0x100) >> 1);
            case 0x12 -> rasterLine() & 0xFF;
            case 0x13, 0x14 -> 0x00; // Light pen input is not connected in Phase 2.
            case 0x16 -> registers[registerIndex] | 0xC0;
            case 0x18 -> registers[registerIndex] | 0x01;
            case 0x19 -> interruptLatch | 0x70 | (interruptLineActive() ? 0x80 : 0);
            case 0x1A -> registers[registerIndex] | 0xF0;
            case 0x1E -> readAndClearSpriteSpriteLatch();
            case 0x1F -> readAndClearSpriteDataLatch();
            default -> registerIndex >= 0x20
                    ? registers[registerIndex] | 0xF0
                    : registers[registerIndex];
        };
    }

    public void writeRegister(int offset, int value) {
        int registerIndex = offset & 0x3F;
        if (registerIndex >= registers.length) {
            return;
        }
        int byteValue = value & 0xFF;
        switch (registerIndex) {
            case 0x11 -> {
                registers[registerIndex] = byteValue & 0x7F;
                rasterCompare = (rasterCompare & 0xFF) | ((byteValue & 0x80) << 1);
                rasterCompareCycle = rasterCompare * CYCLES_PER_LINE;
            }
            case 0x12 -> {
                registers[registerIndex] = byteValue;
                rasterCompare = (rasterCompare & 0x100) | byteValue;
                rasterCompareCycle = rasterCompare * CYCLES_PER_LINE;
            }
            case 0x19 -> interruptLatch &= ~(byteValue & 0x0F);
            case 0x1E, 0x1F -> {
            }
            default -> registers[registerIndex] = byteValue;
        }
    }

    @Override
    public void reset() {
        Arrays.fill(registers, 0);
        interruptLatch = 0;
        spriteSpriteLatch = 0;
        spriteDataLatch = 0;
        rasterCompare = 0;
        rasterCompareCycle = rasterCompare * CYCLES_PER_LINE;
        frameCycle = 0;
        lineCursor = 0;
        frameCompleted = false;
    }

    @Override
    public void onTStatesElapsed(int tStates) {
        for (int cycle = 0; cycle < tStates; cycle++) {
            frameCycle++;
            if (frameCycle == FRAME_CYCLES) {
                frameCycle = 0;
                lineCursor = 0;
                frameCompleted = true;
            }
            if (frameCycle == rasterCompareCycle) {
                interruptLatch |= 0x01;
            }
        }
        int beamLine = frameCycle / CYCLES_PER_LINE;
        while (lineCursor < beamLine) {
            renderLine(lineCursor++);
        }
    }

    public int rasterLine() {
        return frameCycle / CYCLES_PER_LINE;
    }

    public boolean interruptLineActive() {
        return (interruptLatch & registers[0x1A] & 0x0F) != 0;
    }

    public FrameBuffer renderFrame(int cia2PortA) {
        if (frameCompleted) {
            return frame;
        }
        if (lineCursor == 0) {
            for (int rasterLine = 15; rasterLine <= 286; rasterLine++) {
                renderLine(rasterLine, cia2PortA);
            }
            return frame;
        }
        for (int rasterLine = lineCursor; rasterLine <= 286; rasterLine++) {
            renderLine(rasterLine);
        }
        return frame;
    }

    public static int paletteArgb(int index) {
        return PALETTE[index & 0x0F];
    }

    private void renderLine(int rasterLine) {
        renderLine(rasterLine, cia2 == null ? 0xFF : cia2.readRegister(0x00));
    }

    private void renderLine(int rasterLine, int cia2PortA) {
        if (rasterLine < 15 || rasterLine > 286) {
            return;
        }

        int frameY = rasterLine - 15;
        int frameStart = frameY * FRAME_WIDTH;
        int border = PALETTE[registers[0x20] & 0x0F];
        Arrays.fill(frame.pixels(), frameStart, frameStart + FRAME_WIDTH, border);
        if (rasterLine < 51 || rasterLine > 250 || (registers[0x11] & 0x10) == 0) {
            return;
        }

        int windowY = rasterLine - 51;
        int maskStart = windowY * WINDOW_WIDTH;
        Arrays.fill(foregroundMask, maskStart, maskStart + WINDOW_WIDTH, false);
        Arrays.fill(spriteMask, maskStart, maskStart + WINDOW_WIDTH, 0);

        boolean extendedColorMode = (registers[0x11] & 0x40) != 0;
        boolean bitmapMode = (registers[0x11] & 0x20) != 0;
        boolean multicolorMode = (registers[0x16] & 0x10) != 0;
        boolean invalidMode = extendedColorMode && (bitmapMode || multicolorMode);
        fillWindowRow(frameStart, invalidMode ? PALETTE[0] : PALETTE[registers[0x21] & 0x0F]);

        int bankBase = ((~cia2PortA) & 0x03) * 0x4000;
        renderGraphicsLine(windowY, bankBase, bitmapMode, multicolorMode,
                extendedColorMode && !invalidMode);
        renderSpriteLine(windowY, bankBase);
        if (invalidMode) {
            fillWindowRow(frameStart, PALETTE[0]);
        }
        renderWindowBorderLine(windowY, frameStart, border);
    }

    private void renderGraphicsLine(int windowY, int bankBase, boolean bitmapMode,
            boolean multicolorMode, boolean extendedColorMode) {
        int shiftY = (registers[0x11] & 0x07) - 3;
        int sourceY = windowY - shiftY;
        if (sourceY < 0 || sourceY >= WINDOW_HEIGHT) {
            return;
        }

        int row = sourceY / CELL_SIZE;
        int cellY = sourceY & (CELL_SIZE - 1);
        int matrixBase = ((registers[0x18] >> 4) & 0x0F) * 0x400;
        int shiftX = registers[0x16] & 0x07;
        int charsetBase = ((registers[0x18] >> 1) & 0x07) * 0x800;
        int bitmapBase = (registers[0x18] & 0x08) != 0 ? 0x2000 : 0x0000;
        for (int column = 0; column < TEXT_COLUMNS; column++) {
            int cellOffset = row * TEXT_COLUMNS + column;
            int matrixByte = vicRead(matrixBase + cellOffset, bankBase);
            int colorRam = memory.readColorRam(cellOffset);
            int targetX = column * CELL_SIZE + shiftX;
            if (bitmapMode) {
                int bitmapByte = vicRead(bitmapBase + cellOffset * CELL_SIZE + cellY, bankBase);
                if (multicolorMode) {
                    renderMulticolorRow(bitmapByte, registers[0x21], matrixByte >>> 4,
                            matrixByte, colorRam, targetX, windowY);
                } else {
                    int foreground = PALETTE[(matrixByte >>> 4) & 0x0F];
                    int background = PALETTE[matrixByte & 0x0F];
                    renderHiresRow(bitmapByte, foreground, background, targetX, windowY);
                }
                continue;
            }

            int glyphCode = extendedColorMode ? matrixByte & 0x3F : matrixByte;
            int backgroundRegister = extendedColorMode ? 0x21 + (matrixByte >>> 6) : 0x21;
            int background = PALETTE[registers[backgroundRegister] & 0x0F];
            int glyphBits = vicRead(charsetBase + glyphCode * CELL_SIZE + cellY, bankBase);
            if (multicolorMode && (colorRam & 0x08) != 0) {
                renderMulticolorRow(glyphBits, registers[0x21], registers[0x22],
                        registers[0x23], colorRam & 0x07, targetX, windowY);
            } else {
                int foregroundIndex = multicolorMode ? colorRam & 0x07 : colorRam;
                renderHiresRow(glyphBits, PALETTE[foregroundIndex], background, targetX, windowY);
            }
        }
    }

    private void renderMulticolorRow(
            int bits,
            int c0,
            int c1,
            int c2,
            int c3,
            int targetX,
            int targetY
    ) {
        for (int pair = 0; pair < 4; pair++) {
            int pairValue = (bits >>> (6 - pair * 2)) & 0x03;
            int colorIndex = switch (pairValue) {
                case 0 -> c0;
                case 1 -> c1;
                case 2 -> c2;
                default -> c3;
            };
            boolean foregroundPixel = pairValue >= 2;
            int pixelX = pair * 2;
            int color = PALETTE[colorIndex & 0x0F];
            drawGraphicsPixel(targetX + pixelX, targetY, color, foregroundPixel);
            drawGraphicsPixel(targetX + pixelX + 1, targetY, color, foregroundPixel);
        }
    }

    private void renderHiresRow(
            int bits,
            int foreground,
            int background,
            int targetX,
            int targetY
    ) {
        for (int x = 0; x < CELL_SIZE; x++) {
            boolean foregroundPixel = ((bits >>> (7 - x)) & 1) != 0;
            drawGraphicsPixel(targetX + x, targetY,
                    foregroundPixel ? foreground : background, foregroundPixel);
        }
    }

    private void drawGraphicsPixel(int x, int y, int color, boolean foregroundPixel) {
        if (x < 0 || x >= WINDOW_WIDTH || y < 0 || y >= WINDOW_HEIGHT) {
            return;
        }
        frame.setPixel(BORDER_LEFT + x, BORDER_TOP + y, color);
        foregroundMask[y * WINDOW_WIDTH + x] = foregroundPixel;
    }

    private void fillWindowRow(int frameStart, int color) {
        Arrays.fill(frame.pixels(), frameStart + BORDER_LEFT,
                frameStart + BORDER_LEFT + WINDOW_WIDTH, color);
    }

    private void renderWindowBorderLine(int windowY, int frameStart, int border) {
        if ((registers[0x16] & 0x08) == 0) {
            Arrays.fill(frame.pixels(), frameStart + BORDER_LEFT,
                    frameStart + BORDER_LEFT + 7, border);
            Arrays.fill(frame.pixels(), frameStart + BORDER_LEFT + 311,
                    frameStart + BORDER_LEFT + WINDOW_WIDTH, border);
        }
        if ((registers[0x11] & 0x08) == 0 && (windowY < 4 || windowY >= 196)) {
            fillWindowRow(frameStart, border);
        }
    }

    private void renderSpriteLine(int windowY, int bankBase) {
        int matrixBase = ((registers[0x18] >> 4) & 0x0F) * 0x400;
        for (int sprite = SPRITE_COUNT - 1; sprite >= 0; sprite--) {
            if ((registers[0x15] & (1 << sprite)) == 0) {
                continue;
            }
            int spriteY = registers[sprite * 2 + 1];
            int spriteWindowY = spriteY - 50;
            int scaleY = (registers[0x17] & (1 << sprite)) != 0 ? 2 : 1;
            if (windowY < spriteWindowY || windowY >= spriteWindowY + SPRITE_HEIGHT * scaleY) {
                continue;
            }
            int sourceY = (windowY - spriteWindowY) / scaleY;
            int pointer = vicRead(matrixBase + 0x3F8 + sprite, bankBase);
            int rowAddress = pointer * 64 + sourceY * 3;
            int rowBits = (vicRead(rowAddress, bankBase) << 16)
                    | (vicRead(rowAddress + 1, bankBase) << 8)
                    | vicRead(rowAddress + 2, bankBase);
            int spriteX = registers[sprite * 2]
                    | (((registers[0x10] >>> sprite) & 0x01) << 8);
            int spriteWindowX = spriteX - 24;
            int scaleX = (registers[0x1D] & (1 << sprite)) != 0 ? 2 : 1;
            if ((registers[0x1C] & (1 << sprite)) != 0) {
                renderMulticolorSpriteRow(sprite, rowBits, spriteWindowX, windowY, scaleX);
            } else {
                renderHiresSpriteRow(sprite, rowBits, spriteWindowX, windowY, scaleX);
            }
        }
    }

    private void renderHiresSpriteRow(int sprite, int rowBits, int windowX, int y, int scaleX) {
        int color = PALETTE[registers[0x27 + sprite] & 0x0F];
        for (int sourceX = 0; sourceX < SPRITE_WIDTH; sourceX++) {
            if (((rowBits >>> (23 - sourceX)) & 0x01) == 0) {
                continue;
            }
            for (int repeatX = 0; repeatX < scaleX; repeatX++) {
                drawSpritePixel(sprite, windowX + sourceX * scaleX + repeatX, y, color);
            }
        }
    }

    private void renderMulticolorSpriteRow(int sprite, int rowBits, int windowX, int y, int scaleX) {
        for (int pair = 0; pair < SPRITE_WIDTH / 2; pair++) {
            int pairValue = (rowBits >>> (22 - pair * 2)) & 0x03;
            if (pairValue == 0) {
                continue;
            }
            int colorIndex = switch (pairValue) {
                case 1 -> registers[0x25];
                case 2 -> registers[0x27 + sprite];
                default -> registers[0x26];
            };
            int pairWidth = 2 * scaleX;
            for (int repeatX = 0; repeatX < pairWidth; repeatX++) {
                drawSpritePixel(sprite, windowX + pair * pairWidth + repeatX, y,
                        PALETTE[colorIndex & 0x0F]);
            }
        }
    }

    private void drawSpritePixel(int sprite, int x, int y, int color) {
        if (x < 0 || x >= WINDOW_WIDTH) {
            return;
        }
        int maskIndex = y * WINDOW_WIDTH + x;
        int spriteBit = 1 << sprite;
        if (spriteMask[maskIndex] != 0) {
            spriteSpriteLatch |= spriteMask[maskIndex] | spriteBit;
        }
        spriteMask[maskIndex] |= spriteBit;
        if (foregroundMask[maskIndex]) {
            spriteDataLatch |= spriteBit;
        }
        if ((registers[0x1B] & spriteBit) == 0 || !foregroundMask[maskIndex]) {
            frame.setPixel(BORDER_LEFT + x, BORDER_TOP + y, color);
        }
    }

    private int readAndClearSpriteSpriteLatch() {
        int value = spriteSpriteLatch;
        spriteSpriteLatch = 0;
        return value;
    }

    private int readAndClearSpriteDataLatch() {
        int value = spriteDataLatch;
        spriteDataLatch = 0;
        return value;
    }

    private int vicRead(int address, int bankBase) {
        int fullAddress = (bankBase + (address & 0x3FFF)) & 0xFFFF;
        int bankIndependentAddress = fullAddress & 0x7FFF;
        if (bankIndependentAddress >= 0x1000 && bankIndependentAddress <= 0x1FFF) {
            return memory.readCharRom(fullAddress & 0x0FFF);
        }
        return memory.readRam(fullAddress);
    }
}
