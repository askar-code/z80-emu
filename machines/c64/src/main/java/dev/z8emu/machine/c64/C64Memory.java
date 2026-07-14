package dev.z8emu.machine.c64;

import java.util.Arrays;
import java.util.Objects;

public final class C64Memory {
    public static final int ADDRESS_SPACE_SIZE = 0x10000;
    public static final int BASIC_ROM_START = 0xA000;
    public static final int IO_START = 0xD000;
    public static final int KERNAL_ROM_START = 0xE000;
    public static final int BASIC_ROM_SIZE = 0x2000;
    public static final int KERNAL_ROM_SIZE = 0x2000;
    public static final int CHAR_ROM_SIZE = 0x1000;
    public static final int COLOR_RAM_SIZE = 0x0400;

    private final byte[] ram = new byte[ADDRESS_SPACE_SIZE];
    private final byte[] basicRom;
    private final byte[] kernalRom;
    private final byte[] chargenRom;
    private final byte[] colorRam = new byte[COLOR_RAM_SIZE];

    public C64Memory(byte[] basicRom, byte[] kernalRom, byte[] chargenRom) {
        Objects.requireNonNull(basicRom, "basicRom");
        Objects.requireNonNull(kernalRom, "kernalRom");
        Objects.requireNonNull(chargenRom, "chargenRom");
        requireSize("BASIC ROM", basicRom, BASIC_ROM_SIZE);
        requireSize("KERNAL ROM", kernalRom, KERNAL_ROM_SIZE);
        requireSize("character ROM", chargenRom, CHAR_ROM_SIZE);
        this.basicRom = Arrays.copyOf(basicRom, basicRom.length);
        this.kernalRom = Arrays.copyOf(kernalRom, kernalRom.length);
        this.chargenRom = Arrays.copyOf(chargenRom, chargenRom.length);
    }

    public void reset() {
        Arrays.fill(ram, (byte) 0);
        Arrays.fill(colorRam, (byte) 0);
    }

    public int readRam(int address) {
        return Byte.toUnsignedInt(ram[address & 0xFFFF]);
    }

    public void writeRam(int address, int value) {
        ram[address & 0xFFFF] = (byte) value;
    }

    public int readBasicRom(int offset) {
        return Byte.toUnsignedInt(basicRom[offset & (BASIC_ROM_SIZE - 1)]);
    }

    public int readKernalRom(int offset) {
        return Byte.toUnsignedInt(kernalRom[offset & (KERNAL_ROM_SIZE - 1)]);
    }

    public int readCharRom(int offset) {
        return Byte.toUnsignedInt(chargenRom[offset & (CHAR_ROM_SIZE - 1)]);
    }

    public int readColorRam(int offset) {
        return Byte.toUnsignedInt(colorRam[offset & (COLOR_RAM_SIZE - 1)]) & 0x0F;
    }

    public void writeColorRam(int offset, int value) {
        colorRam[offset & (COLOR_RAM_SIZE - 1)] = (byte) (value & 0x0F);
    }

    private static void requireSize(String name, byte[] image, int expectedSize) {
        if (image.length != expectedSize) {
            throw new IllegalArgumentException(name + " must be exactly " + expectedSize + " bytes");
        }
    }
}
