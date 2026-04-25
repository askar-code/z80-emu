package dev.z8emu.machine.apple2;

import dev.z8emu.platform.video.FrameBuffer;

public final class Apple2VideoDevice {
    public static final int TEXT_COLUMNS = 40;
    public static final int TEXT_ROWS = 24;
    public static final int CELL_WIDTH = 7;
    public static final int CELL_HEIGHT = 8;
    public static final int FRAME_WIDTH = TEXT_COLUMNS * CELL_WIDTH;
    public static final int FRAME_HEIGHT = TEXT_ROWS * CELL_HEIGHT;
    public static final int LORES_COLUMNS = TEXT_COLUMNS;
    public static final int LORES_ROWS = 48;
    public static final int LORES_CELL_HEIGHT = 4;
    public static final int HIRES_ROWS = FRAME_HEIGHT;
    public static final int HIRES_BYTE_COLUMNS = 40;

    private static final int FLASH_PHASE_FRAMES = 16;
    private static final int MIXED_TEXT_ROWS = 4;
    private static final int BACKGROUND_ARGB = 0xFF101010;
    private static final int FOREGROUND_ARGB = 0xFF66FF66;
    private static final int HIRES_FOREGROUND_ARGB = FOREGROUND_ARGB;
    private static final int[] LORES_PALETTE = {
            0xFF000000,
            0xFFDD22DD,
            0xFF000099,
            0xFFDD22FF,
            0xFF007722,
            0xFF555555,
            0xFF2222FF,
            0xFF66AAFF,
            0xFF885500,
            0xFFFF6600,
            0xFFAAAAAA,
            0xFFFF99AA,
            0xFF00CC00,
            0xFFFFFF00,
            0xFF44FFCC,
            0xFFFFFFFF
    };

    private final int frameWidth;
    private final int frameHeight;

    public Apple2VideoDevice(int frameWidth, int frameHeight) {
        if (frameWidth != FRAME_WIDTH || frameHeight != FRAME_HEIGHT) {
            throw new IllegalArgumentException("Apple II frame must be 280x192");
        }
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    public FrameBuffer renderFrame(
            Apple2Memory memory,
            Apple2SoftSwitches softSwitches,
            long currentTState,
            int frameTStates
    ) {
        FrameBuffer frame = new FrameBuffer(frameWidth, frameHeight);
        frame.clear(BACKGROUND_ARGB);
        boolean flashInverse = flashInverse(currentTState, frameTStates);
        int textPageBase = textPageBase(softSwitches);
        if (softSwitches.textMode()) {
            drawTextRows(frame, memory, textPageBase, 0, TEXT_ROWS, flashInverse);
            return frame;
        }

        int graphicsHeight = softSwitches.mixedMode()
                ? FRAME_HEIGHT - (MIXED_TEXT_ROWS * CELL_HEIGHT)
                : FRAME_HEIGHT;
        if (softSwitches.hires()) {
            drawHiRes(frame, memory, hiresPageBase(softSwitches), graphicsHeight);
        } else {
            drawLoRes(frame, memory, textPageBase, graphicsHeight);
        }
        if (softSwitches.mixedMode()) {
            drawTextRows(frame, memory, textPageBase, TEXT_ROWS - MIXED_TEXT_ROWS, TEXT_ROWS, flashInverse);
        }
        return frame;
    }

    private static int textPageBase(Apple2SoftSwitches softSwitches) {
        return softSwitches.page2()
                ? Apple2Memory.TEXT_PAGE_2_START
                : Apple2Memory.TEXT_PAGE_1_START;
    }

    private static int hiresPageBase(Apple2SoftSwitches softSwitches) {
        return softSwitches.page2()
                ? Apple2Memory.HIRES_PAGE_2_START
                : Apple2Memory.HIRES_PAGE_1_START;
    }

    private static void drawTextRows(
            FrameBuffer frame,
            Apple2Memory memory,
            int textPageBase,
            int startRow,
            int endRow,
            boolean flashInverse
    ) {
        for (int row = startRow; row < endRow; row++) {
            for (int column = 0; column < TEXT_COLUMNS; column++) {
                int address = Apple2Memory.textPageAddress(textPageBase, row, column);
                drawCharacter(frame, column * CELL_WIDTH, row * CELL_HEIGHT, memory.read(address), flashInverse);
            }
        }
    }

    private static void drawLoRes(FrameBuffer frame, Apple2Memory memory, int pageBase, int graphicsHeight) {
        int loresRows = graphicsHeight / LORES_CELL_HEIGHT;
        for (int row = 0; row < loresRows; row++) {
            for (int column = 0; column < LORES_COLUMNS; column++) {
                int address = Apple2Memory.loresPageAddress(pageBase, row, column);
                int screenByte = memory.read(address);
                int colorIndex = (row & 0x01) == 0 ? screenByte & 0x0F : (screenByte >>> 4) & 0x0F;
                fillRect(
                        frame,
                        column * CELL_WIDTH,
                        row * LORES_CELL_HEIGHT,
                        CELL_WIDTH,
                        LORES_CELL_HEIGHT,
                        LORES_PALETTE[colorIndex]
                );
            }
        }
    }

    private static void drawHiRes(FrameBuffer frame, Apple2Memory memory, int pageBase, int graphicsHeight) {
        for (int y = 0; y < graphicsHeight; y++) {
            for (int byteColumn = 0; byteColumn < HIRES_BYTE_COLUMNS; byteColumn++) {
                int screenByte = memory.read(Apple2Memory.hiresPageAddress(pageBase, y, byteColumn));
                for (int bit = 0; bit < 7; bit++) {
                    boolean lit = ((screenByte >>> bit) & 0x01) != 0;
                    int x = (byteColumn * CELL_WIDTH) + bit;
                    frame.setPixel(x, y, lit ? HIRES_FOREGROUND_ARGB : BACKGROUND_ARGB);
                }
            }
        }
    }

    private static void fillRect(FrameBuffer frame, int x, int y, int width, int height, int argb) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                frame.setPixel(x + dx, y + dy, argb);
            }
        }
    }

    private static boolean flashInverse(long currentTState, int frameTStates) {
        if (frameTStates <= 0) {
            return true;
        }
        long frameNumber = Math.floorDiv(Math.max(0L, currentTState), frameTStates);
        return ((frameNumber / FLASH_PHASE_FRAMES) & 0x01) == 0;
    }

    private static void drawCharacter(FrameBuffer frame, int cellX, int cellY, int screenCode, boolean flashInverse) {
        char character = decodeScreenCode(screenCode);
        int[] glyph = glyph(character);
        boolean inverse = inverse(screenCode, flashInverse);
        for (int y = 0; y < 7; y++) {
            int bits = glyph[y];
            for (int x = 0; x < 5; x++) {
                boolean lit = ((bits >>> (4 - x)) & 0x01) != 0;
                if (inverse) {
                    lit = !lit;
                }
                frame.setPixel(cellX + 1 + x, cellY + y, lit ? FOREGROUND_ARGB : BACKGROUND_ARGB);
            }
        }
    }

    private static boolean inverse(int screenCode, boolean flashInverse) {
        return switch (screenCode & 0xC0) {
            case 0x00 -> true;
            case 0x40 -> flashInverse;
            default -> false;
        };
    }

    private static char decodeScreenCode(int screenCode) {
        int normalized = screenCode & 0x3F;
        if (normalized < 0x20) {
            normalized += 0x40;
        }
        return (char) normalized;
    }

    private static int[] glyph(char character) {
        return switch (Character.toUpperCase(character)) {
            case 'A' -> rows("01110", "10001", "10001", "11111", "10001", "10001", "10001");
            case 'B' -> rows("11110", "10001", "10001", "11110", "10001", "10001", "11110");
            case 'C' -> rows("01111", "10000", "10000", "10000", "10000", "10000", "01111");
            case 'D' -> rows("11110", "10001", "10001", "10001", "10001", "10001", "11110");
            case 'E' -> rows("11111", "10000", "10000", "11110", "10000", "10000", "11111");
            case 'F' -> rows("11111", "10000", "10000", "11110", "10000", "10000", "10000");
            case 'G' -> rows("01111", "10000", "10000", "10011", "10001", "10001", "01110");
            case 'H' -> rows("10001", "10001", "10001", "11111", "10001", "10001", "10001");
            case 'I' -> rows("11111", "00100", "00100", "00100", "00100", "00100", "11111");
            case 'J' -> rows("00111", "00010", "00010", "00010", "10010", "10010", "01100");
            case 'K' -> rows("10001", "10010", "10100", "11000", "10100", "10010", "10001");
            case 'L' -> rows("10000", "10000", "10000", "10000", "10000", "10000", "11111");
            case 'M' -> rows("10001", "11011", "10101", "10101", "10001", "10001", "10001");
            case 'N' -> rows("10001", "11001", "10101", "10011", "10001", "10001", "10001");
            case 'O' -> rows("01110", "10001", "10001", "10001", "10001", "10001", "01110");
            case 'P' -> rows("11110", "10001", "10001", "11110", "10000", "10000", "10000");
            case 'Q' -> rows("01110", "10001", "10001", "10001", "10101", "10010", "01101");
            case 'R' -> rows("11110", "10001", "10001", "11110", "10100", "10010", "10001");
            case 'S' -> rows("01111", "10000", "10000", "01110", "00001", "00001", "11110");
            case 'T' -> rows("11111", "00100", "00100", "00100", "00100", "00100", "00100");
            case 'U' -> rows("10001", "10001", "10001", "10001", "10001", "10001", "01110");
            case 'V' -> rows("10001", "10001", "10001", "10001", "10001", "01010", "00100");
            case 'W' -> rows("10001", "10001", "10001", "10101", "10101", "10101", "01010");
            case 'X' -> rows("10001", "10001", "01010", "00100", "01010", "10001", "10001");
            case 'Y' -> rows("10001", "10001", "01010", "00100", "00100", "00100", "00100");
            case 'Z' -> rows("11111", "00001", "00010", "00100", "01000", "10000", "11111");
            case '0' -> rows("01110", "10001", "10011", "10101", "11001", "10001", "01110");
            case '1' -> rows("00100", "01100", "00100", "00100", "00100", "00100", "01110");
            case '2' -> rows("01110", "10001", "00001", "00010", "00100", "01000", "11111");
            case '3' -> rows("11110", "00001", "00001", "01110", "00001", "00001", "11110");
            case '4' -> rows("00010", "00110", "01010", "10010", "11111", "00010", "00010");
            case '5' -> rows("11111", "10000", "10000", "11110", "00001", "00001", "11110");
            case '6' -> rows("01110", "10000", "10000", "11110", "10001", "10001", "01110");
            case '7' -> rows("11111", "00001", "00010", "00100", "01000", "01000", "01000");
            case '8' -> rows("01110", "10001", "10001", "01110", "10001", "10001", "01110");
            case '9' -> rows("01110", "10001", "10001", "01111", "00001", "00001", "01110");
            case '>' -> rows("10000", "01000", "00100", "00010", "00100", "01000", "10000");
            case '<' -> rows("00001", "00010", "00100", "01000", "00100", "00010", "00001");
            case '=' -> rows("00000", "11111", "00000", "11111", "00000", "00000", "00000");
            case '+' -> rows("00000", "00100", "00100", "11111", "00100", "00100", "00000");
            case '-' -> rows("00000", "00000", "00000", "11111", "00000", "00000", "00000");
            case '*' -> rows("00000", "10101", "01110", "11111", "01110", "10101", "00000");
            case '/' -> rows("00001", "00010", "00010", "00100", "01000", "01000", "10000");
            case '.' -> rows("00000", "00000", "00000", "00000", "00000", "01100", "01100");
            case ',' -> rows("00000", "00000", "00000", "00000", "01100", "00100", "01000");
            case ':' -> rows("00000", "01100", "01100", "00000", "01100", "01100", "00000");
            case ';' -> rows("00000", "01100", "01100", "00000", "01100", "00100", "01000");
            case '?' -> rows("01110", "10001", "00001", "00010", "00100", "00000", "00100");
            case '!' -> rows("00100", "00100", "00100", "00100", "00100", "00000", "00100");
            case '(' -> rows("00010", "00100", "01000", "01000", "01000", "00100", "00010");
            case ')' -> rows("01000", "00100", "00010", "00010", "00010", "00100", "01000");
            case '[' -> rows("11100", "10000", "10000", "10000", "10000", "10000", "11100");
            case ']' -> rows("00111", "00001", "00001", "00001", "00001", "00001", "00111");
            case '$' -> rows("00100", "01111", "10100", "01110", "00101", "11110", "00100");
            case '@' -> rows("01110", "10001", "10111", "10101", "10111", "10000", "01111");
            case '#', '"' -> rows("01010", "01010", "11111", "01010", "11111", "01010", "01010");
            case '\'', '`' -> rows("00100", "00100", "01000", "00000", "00000", "00000", "00000");
            case ' ' -> rows("00000", "00000", "00000", "00000", "00000", "00000", "00000");
            default -> rows("11111", "10001", "00010", "00100", "00000", "00100", "00000");
        };
    }

    private static int[] rows(String row0, String row1, String row2, String row3, String row4, String row5, String row6) {
        return new int[] {
                bits(row0),
                bits(row1),
                bits(row2),
                bits(row3),
                bits(row4),
                bits(row5),
                bits(row6)
        };
    }

    private static int bits(String row) {
        int value = 0;
        for (int i = 0; i < row.length(); i++) {
            value = (value << 1) | (row.charAt(i) == '1' ? 1 : 0);
        }
        return value;
    }
}
