package dev.z8emu.machine.apple2;

public final class Apple2LanguageCard {
    private static final int SWITCH_START = 0xC080;
    private static final int SWITCH_END_EXCLUSIVE = 0xC090;
    private static final int BANKED_START = 0xD000;
    private static final int BANKED_END_EXCLUSIVE = 0xE000;
    private static final int COMMON_START = 0xE000;
    private static final int COMMON_END_EXCLUSIVE = 0x10000;

    private final byte[] bank1 = new byte[BANKED_END_EXCLUSIVE - BANKED_START];
    private final byte[] bank2 = new byte[BANKED_END_EXCLUSIVE - BANKED_START];
    private final byte[] common = new byte[COMMON_END_EXCLUSIVE - COMMON_START];
    private boolean readRam;
    private boolean writeEnabled;
    private boolean bank2Selected;
    private int lastWriteEnableSwitch = -1;

    public Apple2LanguageCard() {
        resetSwitches();
    }

    public void resetSwitches() {
        readRam = false;
        writeEnabled = false;
        bank2Selected = true;
        lastWriteEnableSwitch = -1;
    }

    public boolean handlesSwitch(int address) {
        int normalized = address & 0xFFFF;
        return normalized >= SWITCH_START && normalized < SWITCH_END_EXCLUSIVE;
    }

    public int readSwitch(int address) {
        applySwitch(address);
        return 0x00;
    }

    public void writeSwitch(int address) {
        applySwitch(address);
    }

    public boolean handlesHighMemory(int address) {
        int normalized = address & 0xFFFF;
        return normalized >= BANKED_START;
    }

    public boolean readsRam() {
        return readRam;
    }

    public boolean writesRam() {
        return writeEnabled;
    }

    public int readHighMemory(int address) {
        int normalized = address & 0xFFFF;
        if (normalized >= BANKED_START && normalized < BANKED_END_EXCLUSIVE) {
            return Byte.toUnsignedInt(selectedBank()[normalized - BANKED_START]);
        }
        if (normalized >= COMMON_START) {
            return Byte.toUnsignedInt(common[normalized - COMMON_START]);
        }
        throw new IllegalArgumentException("Apple II language-card address out of range: 0x%04X".formatted(normalized));
    }

    public void writeHighMemory(int address, int value) {
        int normalized = address & 0xFFFF;
        if (normalized >= BANKED_START && normalized < BANKED_END_EXCLUSIVE) {
            selectedBank()[normalized - BANKED_START] = (byte) value;
            return;
        }
        if (normalized >= COMMON_START) {
            common[normalized - COMMON_START] = (byte) value;
            return;
        }
        throw new IllegalArgumentException("Apple II language-card address out of range: 0x%04X".formatted(normalized));
    }

    private void applySwitch(int address) {
        int switchOffset = (address & 0x0F);
        bank2Selected = (switchOffset & 0x08) == 0;
        int mode = switchOffset & 0x03;
        switch (mode) {
            case 0 -> {
                readRam = true;
                disableWrite();
            }
            case 1 -> {
                readRam = false;
                updateWriteEnable(switchOffset);
            }
            case 2 -> {
                readRam = false;
                disableWrite();
            }
            case 3 -> {
                readRam = true;
                updateWriteEnable(switchOffset);
            }
            default -> throw new IllegalStateException("Unexpected language-card switch: " + mode);
        }
    }

    private void updateWriteEnable(int switchOffset) {
        writeEnabled = lastWriteEnableSwitch == switchOffset;
        lastWriteEnableSwitch = switchOffset;
    }

    private void disableWrite() {
        writeEnabled = false;
        lastWriteEnableSwitch = -1;
    }

    private byte[] selectedBank() {
        return bank2Selected ? bank2 : bank1;
    }
}
