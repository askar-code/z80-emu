package dev.z8emu.machine.spectrum.snapshot;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable CPU, border and CPU-visible RAM state for a Spectrum 48K.
 */
public final class Spectrum48Snapshot implements SpectrumSnapshot {
    public static final int RAM_SIZE = 48 * 1024;

    private final Z80SnapshotState cpu;
    private final int borderColor;
    private final byte[] ram;

    public Spectrum48Snapshot(Z80SnapshotState cpu, int borderColor, byte[] ram) {
        this.cpu = Objects.requireNonNull(cpu, "cpu");
        if (borderColor < 0 || borderColor > 7) {
            throw new IllegalArgumentException("borderColor must be between 0 and 7: " + borderColor);
        }
        Objects.requireNonNull(ram, "ram");
        if (ram.length != RAM_SIZE) {
            throw new IllegalArgumentException("48K snapshot RAM must be exactly 49152 bytes, got " + ram.length);
        }
        this.borderColor = borderColor;
        this.ram = Arrays.copyOf(ram, ram.length);
    }

    public Z80SnapshotState cpu() {
        return cpu;
    }

    public int borderColor() {
        return borderColor;
    }

    public byte[] ram() {
        return Arrays.copyOf(ram, ram.length);
    }

    int ramByte(int offset) {
        return Byte.toUnsignedInt(ram[offset]);
    }

    void copyRamTo(byte[] target, int targetOffset) {
        System.arraycopy(ram, 0, target, targetOffset, ram.length);
    }
}
