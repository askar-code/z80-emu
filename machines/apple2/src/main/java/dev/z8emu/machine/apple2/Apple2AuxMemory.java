package dev.z8emu.machine.apple2;

import java.util.Arrays;

public final class Apple2AuxMemory {
    private static final int AUX_SIZE = Apple2Memory.ADDRESS_SPACE_SIZE;
    private static final int ZERO_PAGE_END_EXCLUSIVE = 0x0200;
    private static final int AUX_SWITCH_END_EXCLUSIVE = 0xC000;

    private final byte[] ram = new byte[AUX_SIZE];
    private final boolean installed;
    private boolean eightyStore;
    private boolean ramRead;
    private boolean ramWrite;
    private boolean intCxRom;
    private boolean altZeroPage;
    private boolean slotC3Rom;
    private boolean eightyColumn;
    private boolean altCharSet;

    public Apple2AuxMemory(boolean installed) {
        this.installed = installed;
        reset();
    }

    public void reset() {
        Arrays.fill(ram, (byte) 0);
        eightyStore = false;
        ramRead = false;
        ramWrite = false;
        intCxRom = false;
        altZeroPage = false;
        slotC3Rom = false;
        eightyColumn = false;
        altCharSet = false;
    }

    public boolean installed() {
        return installed;
    }

    public void writeSoftSwitch(int address) {
        if (!installed) {
            return;
        }
        switch (address & 0xFFFF) {
            case 0xC000 -> eightyStore = false;
            case 0xC001 -> eightyStore = true;
            case 0xC002 -> ramRead = false;
            case 0xC003 -> ramRead = true;
            case 0xC004 -> ramWrite = false;
            case 0xC005 -> ramWrite = true;
            case 0xC006 -> intCxRom = false;
            case 0xC007 -> intCxRom = true;
            case 0xC008 -> altZeroPage = false;
            case 0xC009 -> altZeroPage = true;
            case 0xC00A -> slotC3Rom = false;
            case 0xC00B -> slotC3Rom = true;
            case 0xC00C -> eightyColumn = false;
            case 0xC00D -> eightyColumn = true;
            case 0xC00E -> altCharSet = false;
            case 0xC00F -> altCharSet = true;
            default -> {
            }
        }
    }

    public int readStatus(int address, Apple2SoftSwitches videoSwitches) {
        if (!installed) {
            return 0x00;
        }
        return switch (address & 0xFFFF) {
            case 0xC013 -> statusBit(ramRead);
            case 0xC014 -> statusBit(ramWrite);
            case 0xC015 -> statusBit(intCxRom);
            case 0xC016 -> statusBit(altZeroPage);
            case 0xC017 -> statusBit(slotC3Rom);
            case 0xC018 -> statusBit(eightyStore);
            case 0xC01A -> statusBit(videoSwitches.textMode());
            case 0xC01B -> statusBit(videoSwitches.mixedMode());
            case 0xC01C -> statusBit(videoSwitches.page2());
            case 0xC01D -> statusBit(videoSwitches.hires());
            case 0xC01E -> statusBit(altCharSet);
            case 0xC01F -> statusBit(eightyColumn);
            default -> 0x00;
        };
    }

    public boolean readsAux(int address, Apple2SoftSwitches videoSwitches) {
        return installed && selectsAux(address, videoSwitches, ramRead);
    }

    public boolean writesAux(int address, Apple2SoftSwitches videoSwitches) {
        return installed && selectsAux(address, videoSwitches, ramWrite);
    }

    public int read(int address) {
        return Byte.toUnsignedInt(ram[address & 0xFFFF]);
    }

    public void write(int address, int value) {
        ram[address & 0xFFFF] = (byte) value;
    }

    public boolean eightyStore() {
        return eightyStore;
    }

    public boolean ramRead() {
        return ramRead;
    }

    public boolean ramWrite() {
        return ramWrite;
    }

    public boolean altZeroPage() {
        return altZeroPage;
    }

    public boolean intCxRom() {
        return intCxRom;
    }

    public boolean slotC3Rom() {
        return slotC3Rom;
    }

    public boolean eightyColumn() {
        return eightyColumn;
    }

    public boolean altCharSet() {
        return altCharSet;
    }

    private boolean selectsAux(int address, Apple2SoftSwitches videoSwitches, boolean operationSwitch) {
        int normalized = address & 0xFFFF;
        if (normalized < ZERO_PAGE_END_EXCLUSIVE) {
            return altZeroPage;
        }
        if (normalized >= AUX_SWITCH_END_EXCLUSIVE) {
            return false;
        }
        if (eightyStore && isTextPage(normalized)) {
            return videoSwitches.page2();
        }
        if (eightyStore && videoSwitches.hires() && isHiresPage1(normalized)) {
            return videoSwitches.page2();
        }
        return operationSwitch;
    }

    private static boolean isTextPage(int address) {
        return address >= Apple2Memory.TEXT_PAGE_1_START
                && address < Apple2Memory.TEXT_PAGE_1_END_EXCLUSIVE;
    }

    private static boolean isHiresPage1(int address) {
        return address >= Apple2Memory.HIRES_PAGE_1_START
                && address < Apple2Memory.HIRES_PAGE_1_END_EXCLUSIVE;
    }

    private static int statusBit(boolean value) {
        return value ? 0x80 : 0x00;
    }
}
