package dev.z8emu.machine.radio86rk.memory;

import dev.z8emu.platform.memory.RamMemoryBank;
import dev.z8emu.platform.memory.ReadOnlyMemoryBank;
import java.util.Arrays;
import java.util.Objects;

public final class Radio86Memory {
    public static final int RAM_SIZE = 0x8000;
    public static final int ROM_SIZE_2K = 0x0800;
    public static final int ROM_SIZE_4K = 0x1000;
    public static final int BOOT_SHADOW_SIZE = ROM_SIZE_4K;
    public static final int ROM_START = 0xF000;
    public static final int VIDEO_MEMORY_START = 0x76D0;
    public static final int VIDEO_MEMORY_LENGTH = 78 * 30;

    private final RamMemoryBank ram = new RamMemoryBank(RAM_SIZE);
    private final ReadOnlyMemoryBank rom;
    private boolean bootShadowEnabled = true;

    public Radio86Memory(byte[] romImage) {
        Objects.requireNonNull(romImage, "romImage");
        this.rom = new ReadOnlyMemoryBank(normalizeRom(romImage));
    }

    public void reset() {
        ram.reset();
        bootShadowEnabled = true;
    }

    public int readLowMemory(int address) {
        int normalized = address & 0x7FFF;
        if (bootShadowEnabled && normalized < BOOT_SHADOW_SIZE) {
            return rom.read(normalized);
        }
        return ram.read(normalized);
    }

    public void writeLowMemory(int address, int value) {
        ram.write(address & 0x7FFF, value);
    }

    public int readTopRom(int address) {
        disableBootShadow();
        return peekTopRom(address);
    }

    public int peekTopRom(int address) {
        return rom.read((address - ROM_START) & (ROM_SIZE_4K - 1));
    }

    public int readVideoByte(int offset) {
        if (offset < 0 || offset >= VIDEO_MEMORY_LENGTH) {
            return 0x20;
        }
        return ram.read(VIDEO_MEMORY_START + offset);
    }

    public int peekRam(int address) {
        return ram.read(address & 0x7FFF);
    }

    public boolean bootShadowEnabled() {
        return bootShadowEnabled;
    }

    public void disableBootShadow() {
        bootShadowEnabled = false;
    }

    private static byte[] normalizeRom(byte[] romImage) {
        if (romImage.length == ROM_SIZE_2K) {
            byte[] mirrored = new byte[ROM_SIZE_4K];
            System.arraycopy(romImage, 0, mirrored, 0, ROM_SIZE_2K);
            System.arraycopy(romImage, 0, mirrored, ROM_SIZE_2K, ROM_SIZE_2K);
            return mirrored;
        }
        if (romImage.length == ROM_SIZE_4K) {
            return Arrays.copyOf(romImage, ROM_SIZE_4K);
        }
        throw new IllegalArgumentException("Radio-86RK ROM must be exactly 2 KB or 4 KB");
    }
}
