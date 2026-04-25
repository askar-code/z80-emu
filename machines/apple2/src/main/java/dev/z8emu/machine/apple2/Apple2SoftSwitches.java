package dev.z8emu.machine.apple2;

public final class Apple2SoftSwitches {
    private static final int GRAPHICS_MODE = 0xC050;
    private static final int TEXT_MODE = 0xC051;
    private static final int FULL_SCREEN = 0xC052;
    private static final int MIXED_MODE = 0xC053;
    private static final int PAGE_1 = 0xC054;
    private static final int PAGE_2 = 0xC055;
    private static final int LORES = 0xC056;
    private static final int HIRES = 0xC057;

    private boolean textMode;
    private boolean mixedMode;
    private boolean page2;
    private boolean hires;

    public void reset() {
        textMode = true;
        mixedMode = false;
        page2 = false;
        hires = false;
    }

    public int read(int address) {
        touch(address);
        return 0x00;
    }

    public void write(int address, int value) {
        touch(address);
    }

    public boolean textMode() {
        return textMode;
    }

    public boolean mixedMode() {
        return mixedMode;
    }

    public boolean page2() {
        return page2;
    }

    public boolean hires() {
        return hires;
    }

    private void touch(int address) {
        switch (address & 0xFFFF) {
            case GRAPHICS_MODE -> textMode = false;
            case TEXT_MODE -> textMode = true;
            case FULL_SCREEN -> mixedMode = false;
            case MIXED_MODE -> mixedMode = true;
            case PAGE_1 -> page2 = false;
            case PAGE_2 -> page2 = true;
            case LORES -> hires = false;
            case HIRES -> hires = true;
            default -> {
            }
        }
    }
}
