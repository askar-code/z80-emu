package dev.z8emu.machine.apple2;

import java.util.Arrays;
import java.util.Objects;

public final class Apple2Memory {
    public static final int ADDRESS_SPACE_SIZE = 0x10000;
    public static final int SYSTEM_ROM_SIZE_4K = 4 * 1024;
    public static final int SYSTEM_ROM_SIZE_8K = 8 * 1024;
    public static final int SYSTEM_ROM_SIZE_12K = 12 * 1024;
    public static final int SYSTEM_ROM_SIZE_16K = 16 * 1024;
    public static final int IO_START = 0xC000;
    public static final int IO_END_EXCLUSIVE = 0xC100;
    public static final int SLOT_ROM_START = 0xC100;
    public static final int SLOT_ROM_END_EXCLUSIVE = 0xD000;
    public static final int FIRMWARE_ROM_START_16K = 0xC000;
    public static final int SYSTEM_ROM_START = 0xD000;
    public static final int SYSTEM_ROM_MAX_SIZE = ADDRESS_SPACE_SIZE - SYSTEM_ROM_START;
    public static final int TEXT_PAGE_1_START = 0x0400;
    public static final int TEXT_PAGE_1_END_EXCLUSIVE = 0x0800;
    public static final int TEXT_PAGE_2_START = 0x0800;
    public static final int TEXT_PAGE_2_END_EXCLUSIVE = 0x0C00;
    public static final int HIRES_PAGE_1_START = 0x2000;
    public static final int HIRES_PAGE_1_END_EXCLUSIVE = 0x4000;
    public static final int HIRES_PAGE_2_START = 0x4000;
    public static final int HIRES_PAGE_2_END_EXCLUSIVE = 0x6000;

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
        if (systemRomImage.length > SYSTEM_ROM_SIZE_16K) {
            throw new IllegalArgumentException("Apple II system ROM image must be at most 16 KB");
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
        if (hasSystemRomAt(normalized)) {
            return Byte.toUnsignedInt(systemRom[normalized - systemRomStart]);
        }
        return Byte.toUnsignedInt(ram[normalized]);
    }

    public void write(int address, int value) {
        int normalized = address & 0xFFFF;
        if (hasSystemRomAt(normalized)) {
            return;
        }
        ram[normalized] = (byte) value;
    }

    public boolean hasSystemRom() {
        return systemRom.length > 0;
    }

    public boolean hasSystemRomAt(int address) {
        int normalized = address & 0xFFFF;
        int offset = normalized - systemRomStart;
        return hasSystemRom() && offset >= 0 && offset < systemRom.length;
    }

    public int systemRomStart() {
        return systemRomStart;
    }

    public int systemRomSize() {
        return systemRom.length;
    }

    public int readSystemRomOffset(int offset) {
        if (offset < 0 || offset >= systemRom.length) {
            throw new IllegalArgumentException("Apple II system ROM offset out of range: 0x%04X".formatted(offset));
        }
        return Byte.toUnsignedInt(systemRom[offset]);
    }

    public static int textPage1Address(int row, int column) {
        return textPageAddress(TEXT_PAGE_1_START, row, column);
    }

    public static int textPage2Address(int row, int column) {
        return textPageAddress(TEXT_PAGE_2_START, row, column);
    }

    public static int loresPage1Address(int row, int column) {
        return loresPageAddress(TEXT_PAGE_1_START, row, column);
    }

    public static int loresPage2Address(int row, int column) {
        return loresPageAddress(TEXT_PAGE_2_START, row, column);
    }

    public static int loresPageAddress(int baseAddress, int row, int column) {
        if (row < 0 || row >= Apple2VideoDevice.LORES_ROWS) {
            throw new IllegalArgumentException("Apple II lo-res row out of range: " + row);
        }
        return textPageAddress(baseAddress, row / 2, column);
    }

    public static int hiresPage1Address(int y, int byteColumn) {
        return hiresPageAddress(HIRES_PAGE_1_START, y, byteColumn);
    }

    public static int hiresPage2Address(int y, int byteColumn) {
        return hiresPageAddress(HIRES_PAGE_2_START, y, byteColumn);
    }

    public static int hiresPageAddress(int baseAddress, int y, int byteColumn) {
        if (y < 0 || y >= Apple2VideoDevice.HIRES_ROWS) {
            throw new IllegalArgumentException("Apple II hi-res row out of range: " + y);
        }
        if (byteColumn < 0 || byteColumn >= Apple2VideoDevice.HIRES_BYTE_COLUMNS) {
            throw new IllegalArgumentException("Apple II hi-res byte column out of range: " + byteColumn);
        }
        return baseAddress
                + ((y & 0x07) * 0x400)
                + (((y >>> 3) & 0x07) * 0x80)
                + ((y >>> 6) * 0x28)
                + byteColumn;
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
                || length == SYSTEM_ROM_SIZE_12K
                || length == SYSTEM_ROM_SIZE_16K;
    }

    public static boolean isSupportedLaunchImageSize(int length) {
        return length == ADDRESS_SPACE_SIZE || isSupportedSystemRomSize(length);
    }
}
