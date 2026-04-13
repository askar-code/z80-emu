package dev.z8emu.machine.spectrum48k.memory;

import java.util.Arrays;
import java.util.Objects;

public final class Spectrum48kMemoryMap {
    public static final int ROM_SIZE = 16 * 1024;
    public static final int RAM_SIZE = 48 * 1024;

    private final byte[] rom;
    private final byte[] ram = new byte[RAM_SIZE];

    public Spectrum48kMemoryMap(byte[] romImage) {
        Objects.requireNonNull(romImage, "romImage");
        if (romImage.length != ROM_SIZE) {
            throw new IllegalArgumentException("Spectrum 48K ROM image must be exactly 16 KB");
        }

        this.rom = Arrays.copyOf(romImage, romImage.length);
    }

    public int read(int address) {
        int normalized = address & 0xFFFF;
        if (normalized < ROM_SIZE) {
            return Byte.toUnsignedInt(rom[normalized]);
        }

        return Byte.toUnsignedInt(ram[normalized - ROM_SIZE]);
    }

    public void write(int address, int value) {
        int normalized = address & 0xFFFF;
        if (normalized < ROM_SIZE) {
            return;
        }

        ram[normalized - ROM_SIZE] = (byte) value;
    }

    public void reset() {
        Arrays.fill(ram, (byte) 0);
    }
}

