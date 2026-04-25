package dev.z8emu.machine.cpc.device;

public final class CpcCrtcDevice {
    private static final int REGISTER_COUNT = 18;
    private static final int DEFAULT_HORIZONTAL_DISPLAYED_CHARS = 40;
    private static final int DEFAULT_VERTICAL_DISPLAYED_CHARS = 25;
    private static final int DEFAULT_MAX_RASTER_ADDRESS = 7;
    private static final int DEFAULT_SCREEN_START_HIGH = 0x30;

    private final int[] registers = new int[REGISTER_COUNT];
    private int selectedRegister;

    public void reset() {
        java.util.Arrays.fill(registers, 0);
        selectedRegister = 0;
        registers[1] = DEFAULT_HORIZONTAL_DISPLAYED_CHARS;
        registers[6] = DEFAULT_VERTICAL_DISPLAYED_CHARS;
        registers[9] = DEFAULT_MAX_RASTER_ADDRESS;
        registers[12] = DEFAULT_SCREEN_START_HIGH;
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

    public int displayMemoryAddress(int rasterLine, int byteColumn) {
        int scanline = Math.floorMod(rasterLine, scanlinesPerCharacter());
        int characterRow = Math.max(0, rasterLine / scanlinesPerCharacter());
        int characterColumn = Math.max(0, byteColumn / 2);
        int ma = (startAddress() + (characterRow * horizontalDisplayedChars()) + characterColumn) & 0x3FFF;
        return displayAddress(ma, scanline, byteColumn);
    }

    private int displayAddress(int ma, int rasterLine, int byteColumn) {
        return (((ma & 0x3000) << 2)
                | ((rasterLine & 0x07) << 11)
                | ((ma & 0x03FF) << 1)
                | (byteColumn & 0x01)) & 0xFFFF;
    }
}
