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
    private final int[] registers = new int[0x2F];
    private final FrameBuffer frame = new FrameBuffer(FRAME_WIDTH, FRAME_HEIGHT);
    private final boolean[] foregroundMask = new boolean[WINDOW_WIDTH * WINDOW_HEIGHT];
    private final int[] spriteMask = new int[WINDOW_WIDTH * WINDOW_HEIGHT];

    private int interruptLatch;
    private int spriteSpriteLatch;
    private int spriteDataLatch;
    private int rasterCompare;
    private int frameCycle;

    public C64VideoDevice(C64Memory memory) {
        this.memory = Objects.requireNonNull(memory, "memory");
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
            }
            case 0x12 -> {
                registers[registerIndex] = byteValue;
                rasterCompare = (rasterCompare & 0x100) | byteValue;
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
        frameCycle = 0;
    }

    @Override
    public void onTStatesElapsed(int tStates) {
        for (int cycle = 0; cycle < tStates; cycle++) {
            frameCycle++;
            if (frameCycle == FRAME_CYCLES) {
                frameCycle = 0;
            }
            if (frameCycle % CYCLES_PER_LINE == 0 && rasterLine() == rasterCompare) {
                interruptLatch |= 0x01;
            }
        }
    }

    public int rasterLine() {
        return frameCycle / CYCLES_PER_LINE;
    }

    public boolean interruptLineActive() {
        return (interruptLatch & registers[0x1A] & 0x0F) != 0;
    }

    public FrameBuffer renderFrame(int cia2PortA) {
        frame.clear(PALETTE[registers[0x20] & 0x0F]);
        Arrays.fill(foregroundMask, false);
        Arrays.fill(spriteMask, 0);
        if ((registers[0x11] & 0x10) == 0) {
            return frame;
        }

        int bankBase = ((~cia2PortA) & 0x03) * 0x4000;
        int matrixBase = ((registers[0x18] >> 4) & 0x0F) * 0x400;
        int charsetBase = ((registers[0x18] >> 1) & 0x07) * 0x800;
        renderText(bankBase, matrixBase, charsetBase);
        renderSprites(bankBase, matrixBase);
        return frame;
    }

    public static int paletteArgb(int index) {
        return PALETTE[index & 0x0F];
    }

    private void renderText(int bankBase, int matrixBase, int charsetBase) {
        int background = PALETTE[registers[0x21] & 0x0F];
        boolean multicolorMode = (registers[0x16] & 0x10) != 0;
        for (int row = 0; row < TEXT_ROWS; row++) {
            for (int column = 0; column < TEXT_COLUMNS; column++) {
                int cellOffset = row * TEXT_COLUMNS + column;
                int screenCode = vicRead(matrixBase + cellOffset, bankBase);
                int colorRam = memory.readColorRam(cellOffset);
                int targetX = BORDER_LEFT + column * CELL_SIZE;
                int targetY = BORDER_TOP + row * CELL_SIZE;
                for (int y = 0; y < CELL_SIZE; y++) {
                    int glyphBits = vicRead(charsetBase + screenCode * CELL_SIZE + y, bankBase);
                    int maskOffset = (row * CELL_SIZE + y) * WINDOW_WIDTH + column * CELL_SIZE;
                    if (multicolorMode && (colorRam & 0x08) != 0) {
                        renderMulticolorTextRow(glyphBits, colorRam, targetX, targetY + y, maskOffset);
                    } else {
                        int foregroundIndex = multicolorMode ? colorRam & 0x07 : colorRam;
                        int foreground = PALETTE[foregroundIndex];
                        for (int x = 0; x < CELL_SIZE; x++) {
                            boolean foregroundPixel = ((glyphBits >>> (7 - x)) & 1) != 0;
                            frame.setPixel(targetX + x, targetY + y,
                                    foregroundPixel ? foreground : background);
                            foregroundMask[maskOffset + x] = foregroundPixel;
                        }
                    }
                }
            }
        }
    }

    private void renderMulticolorTextRow(int glyphBits, int colorRam, int targetX, int targetY,
            int maskOffset) {
        for (int pair = 0; pair < 4; pair++) {
            int pairValue = (glyphBits >>> (6 - pair * 2)) & 0x03;
            int colorIndex = switch (pairValue) {
                case 0 -> registers[0x21];
                case 1 -> registers[0x22];
                case 2 -> registers[0x23];
                default -> colorRam & 0x07;
            };
            boolean foregroundPixel = pairValue >= 2;
            int pixelX = pair * 2;
            frame.setPixel(targetX + pixelX, targetY, PALETTE[colorIndex & 0x0F]);
            frame.setPixel(targetX + pixelX + 1, targetY, PALETTE[colorIndex & 0x0F]);
            foregroundMask[maskOffset + pixelX] = foregroundPixel;
            foregroundMask[maskOffset + pixelX + 1] = foregroundPixel;
        }
    }

    private void renderSprites(int bankBase, int matrixBase) {
        for (int sprite = SPRITE_COUNT - 1; sprite >= 0; sprite--) {
            if ((registers[0x15] & (1 << sprite)) == 0) {
                continue;
            }
            renderSprite(sprite, bankBase, matrixBase);
        }
    }

    private void renderSprite(int sprite, int bankBase, int matrixBase) {
        int spriteX = registers[sprite * 2]
                | (((registers[0x10] >>> sprite) & 0x01) << 8);
        int spriteY = registers[sprite * 2 + 1];
        int windowX = spriteX - 24;
        int windowY = spriteY - 50;
        int pointer = vicRead(matrixBase + 0x3F8 + sprite, bankBase);
        int dataBase = pointer * 64;
        boolean multicolor = (registers[0x1C] & (1 << sprite)) != 0;
        int scaleX = (registers[0x1D] & (1 << sprite)) != 0 ? 2 : 1;
        int scaleY = (registers[0x17] & (1 << sprite)) != 0 ? 2 : 1;
        for (int sourceY = 0; sourceY < SPRITE_HEIGHT; sourceY++) {
            int rowAddress = dataBase + sourceY * 3;
            int rowBits = (vicRead(rowAddress, bankBase) << 16)
                    | (vicRead(rowAddress + 1, bankBase) << 8)
                    | vicRead(rowAddress + 2, bankBase);
            for (int repeatY = 0; repeatY < scaleY; repeatY++) {
                int y = windowY + sourceY * scaleY + repeatY;
                if (y < 0 || y >= WINDOW_HEIGHT) {
                    continue;
                }
                if (multicolor) {
                    renderMulticolorSpriteRow(sprite, rowBits, windowX, y, scaleX);
                } else {
                    renderHiresSpriteRow(sprite, rowBits, windowX, y, scaleX);
                }
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
