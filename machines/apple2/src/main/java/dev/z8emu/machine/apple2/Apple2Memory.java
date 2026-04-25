package dev.z8emu.machine.apple2;

import java.util.Arrays;
import java.util.Objects;

public final class Apple2Memory {
    public static final int ADDRESS_SPACE_SIZE = 0x10000;
    public static final int SYSTEM_ROM_SIZE_4K = 4 * 1024;
    public static final int SYSTEM_ROM_SIZE_8K = 8 * 1024;
    public static final int SYSTEM_ROM_SIZE_12K = 12 * 1024;
    public static final int IO_START = 0xC000;
    public static final int IO_END_EXCLUSIVE = 0xC100;
    public static final int SLOT_ROM_START = 0xC100;
    public static final int SLOT_ROM_END_EXCLUSIVE = 0xD000;
    public static final int SYSTEM_ROM_START = 0xD000;
    public static final int SYSTEM_ROM_MAX_SIZE = ADDRESS_SPACE_SIZE - SYSTEM_ROM_START;
    public static final int TEXT_PAGE_1_START = 0x0400;
    public static final int TEXT_PAGE_1_END_EXCLUSIVE = 0x0800;
    public static final int TEXT_PAGE_2_START = 0x0800;
    public static final int TEXT_PAGE_2_END_EXCLUSIVE = 0x0C00;

    private final byte[] resetImage = new byte[ADDRESS_SPACE_SIZE];
    private final byte[] ram = new byte[ADDRESS_SPACE_SIZE];
    private final byte[] systemRom;
    private final int systemRomStart;

    public Apple2Memory() {
        this(new byte[0]);
    }

    public Apple2Memory(byte[] initialMemoryImage) {
        this(initialMemoryImage, new byte[0]);
    }

    public Apple2Memory(byte[] initialMemoryImage, byte[] systemRomImage) {
        Objects.requireNonNull(initialMemoryImage, "initialMemoryImage");
        Objects.requireNonNull(systemRomImage, "systemRomImage");
        if (initialMemoryImage.length > ADDRESS_SPACE_SIZE) {
            throw new IllegalArgumentException("Apple II initial memory image must be at most 64 KB");
        }
        if (systemRomImage.length > SYSTEM_ROM_MAX_SIZE) {
            throw new IllegalArgumentException("Apple II system ROM image must be at most 12 KB");
        }
        System.arraycopy(initialMemoryImage, 0, resetImage, 0, initialMemoryImage.length);
        this.systemRom = Arrays.copyOf(systemRomImage, systemRomImage.length);
        this.systemRomStart = ADDRESS_SPACE_SIZE - systemRom.length;
        reset();
    }

    public void reset() {
        Arrays.fill(ram, (byte) 0);
        System.arraycopy(resetImage, 0, ram, 0, resetImage.length);
    }

    public int read(int address) {
        int normalized = address & 0xFFFF;
        if (hasSystemRom() && normalized >= systemRomStart) {
            return Byte.toUnsignedInt(systemRom[normalized - systemRomStart]);
        }
        return Byte.toUnsignedInt(ram[normalized]);
    }

    public void write(int address, int value) {
        int normalized = address & 0xFFFF;
        if (hasSystemRom() && normalized >= systemRomStart) {
            return;
        }
        ram[normalized] = (byte) value;
    }

    public boolean hasSystemRom() {
        return systemRom.length > 0;
    }

    public int systemRomStart() {
        return systemRomStart;
    }

    public static int textPage1Address(int row, int column) {
        return textPageAddress(TEXT_PAGE_1_START, row, column);
    }

    public static int textPage2Address(int row, int column) {
        return textPageAddress(TEXT_PAGE_2_START, row, column);
    }

    public static int textPageAddress(int baseAddress, int row, int column) {
        if (row < 0 || row >= Apple2VideoDevice.TEXT_ROWS) {
            throw new IllegalArgumentException("Apple II text row out of range: " + row);
        }
        if (column < 0 || column >= Apple2VideoDevice.TEXT_COLUMNS) {
            throw new IllegalArgumentException("Apple II text column out of range: " + column);
        }
        return baseAddress + ((row & 0x07) * 0x80) + ((row >>> 3) * 0x28) + column;
    }

    public static boolean isSupportedSystemRomSize(int length) {
        return length == SYSTEM_ROM_SIZE_4K
                || length == SYSTEM_ROM_SIZE_8K
                || length == SYSTEM_ROM_SIZE_12K;
    }

    public static boolean isSupportedLaunchImageSize(int length) {
        return length == ADDRESS_SPACE_SIZE || isSupportedSystemRomSize(length);
    }
}
