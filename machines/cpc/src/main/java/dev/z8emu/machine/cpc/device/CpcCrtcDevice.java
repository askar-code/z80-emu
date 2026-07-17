package dev.z8emu.machine.cpc.device;

public final class CpcCrtcDevice {
    private static final int REGISTER_COUNT = 18;
    private static final int DEFAULT_HORIZONTAL_TOTAL = 63;
    private static final int DEFAULT_HORIZONTAL_DISPLAYED_CHARS = 40;
    private static final int DEFAULT_HORIZONTAL_SYNC_POSITION = 46;
    private static final int DEFAULT_SYNC_WIDTHS = 0x8E;
    private static final int DEFAULT_VERTICAL_TOTAL = 38;
    private static final int DEFAULT_VERTICAL_TOTAL_ADJUST = 0;
    private static final int DEFAULT_VERTICAL_DISPLAYED_CHARS = 25;
    private static final int DEFAULT_VERTICAL_SYNC_POSITION = 30;
    private static final int DEFAULT_MAX_RASTER_ADDRESS = 7;
    private static final int DEFAULT_SCREEN_START_HIGH = 0x30;
    private static final int VSYNC_LENGTH_LINES = 16;

    private final int[] registers = new int[REGISTER_COUNT];
    private int selectedRegister;
    private int charRow;
    private int scanline;
    private boolean verticalAdjustActive;
    private int verticalAdjustLine;
    private boolean vsyncActive;
    private int vsyncLinesRemaining;
    private boolean vsyncStartedThisTick;
    private boolean displayWrappedToRowZeroThisTick;

    public void reset() {
        java.util.Arrays.fill(registers, 0);
        selectedRegister = 0;
        registers[0] = DEFAULT_HORIZONTAL_TOTAL;
        registers[1] = DEFAULT_HORIZONTAL_DISPLAYED_CHARS;
        registers[2] = DEFAULT_HORIZONTAL_SYNC_POSITION;
        registers[3] = DEFAULT_SYNC_WIDTHS;
        registers[4] = DEFAULT_VERTICAL_TOTAL;
        registers[5] = DEFAULT_VERTICAL_TOTAL_ADJUST;
        registers[6] = DEFAULT_VERTICAL_DISPLAYED_CHARS;
        registers[7] = DEFAULT_VERTICAL_SYNC_POSITION;
        registers[9] = DEFAULT_MAX_RASTER_ADDRESS;
        registers[12] = DEFAULT_SCREEN_START_HIGH;
        charRow = DEFAULT_VERTICAL_SYNC_POSITION;
        scanline = 0;
        verticalAdjustActive = false;
        verticalAdjustLine = 0;
        vsyncActive = true;
        vsyncLinesRemaining = VSYNC_LENGTH_LINES;
        vsyncStartedThisTick = false;
        displayWrappedToRowZeroThisTick = false;
    }

    public void selectRegister(int value) {
        selectedRegister = value & 0x1F;
    }

    public void writeSelectedRegister(int value) {
        if (selectedRegister >= REGISTER_COUNT) {
            return;
        }
        registers[selectedRegister] = value & 0xFF;
    }

    public int readSelectedRegister() {
        if (selectedRegister >= REGISTER_COUNT) {
            return 0xFF;
        }
        return registers[selectedRegister];
    }

    public int selectedRegister() {
        return selectedRegister;
    }

    public int horizontalDisplayedChars() {
        return Math.max(1, registers[1] & 0xFF);
    }

    public int horizontalDisplayedBytes() {
        return horizontalDisplayedChars() * 2;
    }

    public int verticalDisplayedChars() {
        return Math.max(1, registers[6] & 0x7F);
    }

    public int scanlinesPerCharacter() {
        return ((registers[9] & 0x1F) + 1);
    }

    public int visibleRasterLines() {
        return verticalDisplayedChars() * scanlinesPerCharacter();
    }

    public int startAddress() {
        return (((registers[12] & 0x3F) << 8) | registers[13]) & 0x3FFF;
    }

    public int screenStartByteAddress() {
        return displayAddress(startAddress(), 0, 0);
    }

    public void onHsync() {
        vsyncStartedThisTick = false;
        displayWrappedToRowZeroThisTick = false;

        if (vsyncActive) {
            vsyncLinesRemaining--;
            if (vsyncLinesRemaining == 0) {
                vsyncActive = false;
            }
        }

        if (verticalAdjustActive) {
            verticalAdjustLine++;
            if (verticalAdjustLine >= (registers[5] & 0x1F)) {
                wrapDisplayToRowZero();
            }
            return;
        }

        int rowHeight = (registers[9] & 0x1F) + 1;
        scanline++;
        if (scanline < rowHeight) {
            return;
        }

        scanline = 0;
        int maxCharRow = registers[4] & 0x7F;
        if (charRow >= maxCharRow) {
            if ((registers[5] & 0x1F) == 0) {
                wrapDisplayToRowZero();
            } else {
                verticalAdjustActive = true;
                verticalAdjustLine = 0;
            }
            return;
        }

        charRow++;
        startVsyncAtMatchingRow();
    }

    public boolean vsyncActive() {
        return vsyncActive;
    }

    public boolean vsyncJustStarted() {
        return vsyncActive && vsyncLinesRemaining == VSYNC_LENGTH_LINES - 1;
    }

    public boolean vsyncStartedThisTick() {
        return vsyncStartedThisTick;
    }

    public boolean displayWrappedToRowZeroThisTick() {
        return displayWrappedToRowZeroThisTick;
    }

    public int displayMemoryAddress(int rasterLine, int byteColumn) {
        return displayMemoryAddress(
                rasterLine,
                byteColumn,
                scanlinesPerCharacter(),
                horizontalDisplayedChars(),
                startAddress()
        );
    }

    int displayMemoryAddress(
            int rasterLine,
            int byteColumn,
            int scanlinesPerChar,
            int horizChars,
            int startAddr
    ) {
        int scanline = Math.floorMod(rasterLine, scanlinesPerChar);
        int characterRow = Math.max(0, rasterLine / scanlinesPerChar);
        int characterColumn = Math.max(0, byteColumn / 2);
        int ma = (startAddr + (characterRow * horizChars) + characterColumn) & 0x3FFF;
        return displayAddress(ma, scanline, byteColumn);
    }

    private int displayAddress(int ma, int rasterLine, int byteColumn) {
        return (((ma & 0x3000) << 2)
                | ((rasterLine & 0x07) << 11)
                | ((ma & 0x03FF) << 1)
                | (byteColumn & 0x01)) & 0xFFFF;
    }

    private void wrapDisplayToRowZero() {
        charRow = 0;
        scanline = 0;
        verticalAdjustActive = false;
        verticalAdjustLine = 0;
        displayWrappedToRowZeroThisTick = true;
        startVsyncAtMatchingRow();
    }

    private void startVsyncAtMatchingRow() {
        if (charRow != (registers[7] & 0x7F)) {
            return;
        }
        vsyncStartedThisTick = !vsyncActive;
        vsyncActive = true;
        vsyncLinesRemaining = VSYNC_LENGTH_LINES;
    }
}
