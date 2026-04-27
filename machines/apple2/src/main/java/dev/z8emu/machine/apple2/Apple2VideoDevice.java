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

    // DHGR color comes from a rolling composite signal, not a fixed 16-color
    // RGB lookup. These offsets keep the 560 half-pixel stream aligned with the
    // current 280-pixel framebuffer.
    private static final int DOUBLE_HIRES_NTSC_INITIAL_PHASE = 3;
    private static final int DOUBLE_HIRES_NTSC_SIGNAL_OFFSET = 2;
    private static final int DOUBLE_HIRES_NTSC_SEQUENCE_MASK = 0x0FFF;
    private static final double DOUBLE_HIRES_NTSC_RED_I = 1.55;
    private static final double DOUBLE_HIRES_NTSC_RED_Q = 0.25;
    private static final double DOUBLE_HIRES_NTSC_GREEN_I = -0.04;
    private static final double DOUBLE_HIRES_NTSC_GREEN_Q = -0.76;
    private static final double DOUBLE_HIRES_NTSC_BLUE_I = -2.55;
    private static final double DOUBLE_HIRES_NTSC_BLUE_Q = 1.60;
    private static final NtscSample[][] DOUBLE_HIRES_NTSC_TABLE = buildDoubleHiResNtscTable();

    private static final int FLASH_PHASE_FRAMES = 16;
    private static final int MIXED_TEXT_ROWS = 4;
    private static final int BACKGROUND_ARGB = 0xFF101010;
    private static final int FOREGROUND_ARGB = 0xFF66FF66;
    private static final int HIRES_BLACK_ARGB = 0xFF000000;
    private static final int HIRES_WHITE_ARGB = 0xFFFFFFFF;
    private static final int HIRES_VIOLET_ARGB = 0xFFDD22FF;
    private static final int HIRES_GREEN_ARGB = 0xFF00CC00;
    private static final int HIRES_BLUE_ARGB = 0xFF2222FF;
    private static final int HIRES_ORANGE_ARGB = 0xFFFF6600;
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
        return renderFrame(memory, null, softSwitches, currentTState, frameTStates);
    }

    public FrameBuffer renderFrame(
            Apple2Memory memory,
            Apple2AuxMemory auxMemory,
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
            if (doubleHiRes(auxMemory, softSwitches)) {
                drawDoubleHiRes(frame, memory, auxMemory, hiresPageBase(softSwitches), graphicsHeight);
            } else {
                drawHiRes(frame, memory, hiresPageBase(softSwitches), graphicsHeight);
            }
        } else {
            drawLoRes(frame, memory, textPageBase, graphicsHeight);
        }
        if (softSwitches.mixedMode()) {
            drawTextRows(frame, memory, textPageBase, TEXT_ROWS - MIXED_TEXT_ROWS, TEXT_ROWS, flashInverse);
        }
        return frame;
    }

    private static boolean doubleHiRes(Apple2AuxMemory auxMemory, Apple2SoftSwitches softSwitches) {
        return auxMemory != null
                && auxMemory.installed()
                && softSwitches.hires()
                && auxMemory.eightyColumn();
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
        boolean[] lineBits = new boolean[FRAME_WIDTH];
        boolean[] shiftedPixels = new boolean[FRAME_WIDTH];
        for (int y = 0; y < graphicsHeight; y++) {
            for (int byteColumn = 0; byteColumn < HIRES_BYTE_COLUMNS; byteColumn++) {
                int screenByte = memory.read(Apple2Memory.hiresPageAddress(pageBase, y, byteColumn));
                boolean shifted = (screenByte & 0x80) != 0;
                for (int bit = 0; bit < 7; bit++) {
                    int x = (byteColumn * CELL_WIDTH) + bit;
                    lineBits[x] = ((screenByte >>> bit) & 0x01) != 0;
                    shiftedPixels[x] = shifted;
                }
            }
            for (int x = 0; x < FRAME_WIDTH; x++) {
                frame.setPixel(x, y, hiResArtifactColor(lineBits, shiftedPixels, x));
            }
        }
    }

    private static void drawDoubleHiRes(
            FrameBuffer frame,
            Apple2Memory memory,
            Apple2AuxMemory auxMemory,
            int pageBase,
            int graphicsHeight
    ) {
        boolean[] lineBits = new boolean[FRAME_WIDTH * 2 + 6];
        NtscSample[] ntscSamples = new NtscSample[FRAME_WIDTH * 2 + 2];
        for (int y = 0; y < graphicsHeight; y++) {
            for (int byteColumn = 0; byteColumn < HIRES_BYTE_COLUMNS; byteColumn++) {
                int address = Apple2Memory.hiresPageAddress(pageBase, y, byteColumn);
                int auxByte = auxMemory.read(address);
                int mainByte = memory.read(address);
                int x = byteColumn * CELL_WIDTH * 2;
                for (int bit = 0; bit < 7; bit++) {
                    lineBits[x + bit] = ((auxByte >>> bit) & 0x01) != 0;
                    lineBits[x + CELL_WIDTH + bit] = ((mainByte >>> bit) & 0x01) != 0;
                }
            }
            for (int x = FRAME_WIDTH * 2; x < lineBits.length; x++) {
                lineBits[x] = false;
            }
            int signalBits = 0;
            int colorPhase = DOUBLE_HIRES_NTSC_INITIAL_PHASE;
            for (int halfPixel = 0; halfPixel < ntscSamples.length; halfPixel++) {
                boolean signal = lineBits[halfPixel + DOUBLE_HIRES_NTSC_SIGNAL_OFFSET];
                signalBits = ((signalBits << 1) | (signal ? 1 : 0)) & DOUBLE_HIRES_NTSC_SEQUENCE_MASK;
                ntscSamples[halfPixel] = DOUBLE_HIRES_NTSC_TABLE[colorPhase][signalBits];
                colorPhase = (colorPhase + 1) & 0x03;
            }
            for (int x = 0; x < FRAME_WIDTH; x++) {
                frame.setPixel(x, y, sampleDoubleHiResNtscPixel(ntscSamples, x * 2));
            }
        }
    }

    private static int hiResArtifactColor(boolean[] lineBits, boolean[] shiftedPixels, int x) {
        if (!lineBits[x]) {
            return HIRES_BLACK_ARGB;
        }
        if ((x > 0 && lineBits[x - 1]) || (x + 1 < lineBits.length && lineBits[x + 1])) {
            return HIRES_WHITE_ARGB;
        }
        boolean oddPixel = (x & 0x01) != 0;
        if (shiftedPixels[x]) {
            return oddPixel ? HIRES_ORANGE_ARGB : HIRES_BLUE_ARGB;
        }
        return oddPixel ? HIRES_GREEN_ARGB : HIRES_VIOLET_ARGB;
    }

    private static NtscSample[][] buildDoubleHiResNtscTable() {
        NtscSample[][] table = new NtscSample[4][4096];
        NtscFilter signalFilter = new NtscFilter(7.614490548, -0.2718798058, 0.7465656072, false);
        NtscFilter chromaFilter = new NtscFilter(7.438011255, -0.7318893645, 1.2336442711, true);
        NtscFilter lumaFilter = new NtscFilter(13.71331570, -0.3961075449, 1.1044202472, false);
        NtscFilter colorTvLumaFilter = new NtscFilter(13.71331570, -0.3961075449, 1.1044202472, false);
        for (int phase = 0; phase < table.length; phase++) {
            double phi = (phase * Math.PI / 2.0) + (Math.PI / 4.0);
            for (int sequence = 0; sequence < table[phase].length; sequence++) {
                int bits = sequence;
                double luma = 0.0;
                double chromaI = 0.0;
                double chromaQ = 0.0;
                for (int bit = 0; bit < 12; bit++) {
                    double signal = (bits & 0x0800) == 0 ? 0.0 : 1.0;
                    bits = (bits << 1) & 0xFFFF;
                    for (int sample = 0; sample < 2; sample++) {
                        double filteredSignal = signalFilter.filter(signal);
                        double chroma = chromaFilter.filter(filteredSignal);
                        lumaFilter.filter(filteredSignal);
                        luma = colorTvLumaFilter.filter(filteredSignal - chroma);
                        chroma *= 2.0;
                        chromaI += ((chroma * Math.cos(phi)) - chromaI) / 8.0;
                        chromaQ += ((chroma * Math.sin(phi)) - chromaQ) / 8.0;
                        phi += Math.PI / 4.0;
                    }
                }
                table[phase][sequence] = new NtscSample(sequence, luma, chromaI, chromaQ);
            }
        }
        return table;
    }

    private static int sampleDoubleHiResNtscPixel(NtscSample[] samples, int halfPixel) {
        // The 280-pixel framebuffer samples a 560 half-pixel DHGR signal. A
        // slight late-sample luma bias keeps sharp ornament edges readable,
        // while chroma is sampled across a wider YIQ aperture before RGB clamp.
        NtscSample first = samples[halfPixel];
        NtscSample second = samples[halfPixel + 1];
        NtscSample previous = halfPixel == 0 ? first : samples[halfPixel - 1];
        NtscSample next = samples[halfPixel + 2];
        double luma = ((2.0 * first.luma) + (3.0 * second.luma)) / 5.0;
        double chromaI = (previous.chromaI + first.chromaI + second.chromaI + next.chromaI) / 4.0;
        double chromaQ = (previous.chromaQ + first.chromaQ + second.chromaQ + next.chromaQ) / 4.0;
        return ntscRgb(second.sequence, luma, chromaI, chromaQ);
    }

    private static int ntscRgb(int sequence, double luma, double chromaI, double chromaQ) {
        double red = luma + (DOUBLE_HIRES_NTSC_RED_I * chromaI) + (DOUBLE_HIRES_NTSC_RED_Q * chromaQ);
        double green = luma + (DOUBLE_HIRES_NTSC_GREEN_I * chromaI) + (DOUBLE_HIRES_NTSC_GREEN_Q * chromaQ);
        double blue = luma + (DOUBLE_HIRES_NTSC_BLUE_I * chromaI) + (DOUBLE_HIRES_NTSC_BLUE_Q * chromaQ);
        int color = sequence & 0x0F;
        if (color == 0x0F) {
            red = 1.0;
            green = 1.0;
            blue = 1.0;
        } else if (sequence == 0x000) {
            // Keep a truly idle signal black, but let nonzero sequences that end
            // in a black nibble retain the rolling NTSC color bleed.
            red = 0.0;
            green = 0.0;
            blue = 0.0;
        } else if (color == 0x05) {
            red = 0x83 / 255.0;
            green = 0x83 / 255.0;
            blue = 0x83 / 255.0;
        } else if (color == 0x0A) {
            red = 0x78 / 255.0;
            green = 0x78 / 255.0;
            blue = 0x78 / 255.0;
        }
        return 0xFF000000 | (clampColor(red) << 16) | (clampColor(green) << 8) | clampColor(blue);
    }

    private static int clampColor(double value) {
        if (value <= 0.0) {
            return 0;
        }
        if (value >= 1.0) {
            return 255;
        }
        return (int) (value * 255.0);
    }

    private record NtscSample(int sequence, double luma, double chromaI, double chromaQ) {
    }

    private static final class NtscFilter {
        private final double gain;
        private final double feedback0;
        private final double feedback1;
        private final boolean chroma;
        private final double[] x = new double[3];
        private final double[] y = new double[3];

        private NtscFilter(double gain, double feedback0, double feedback1, boolean chroma) {
            this.gain = gain;
            this.feedback0 = feedback0;
            this.feedback1 = feedback1;
            this.chroma = chroma;
        }

        private double filter(double value) {
            x[0] = x[1];
            x[1] = x[2];
            x[2] = value / gain;
            y[0] = y[1];
            y[1] = y[2];
            double input = chroma ? -x[0] + x[2] : x[0] + x[2] + (2.0 * x[1]);
            y[2] = input + (feedback0 * y[0]) + (feedback1 * y[1]);
            return y[2];
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
