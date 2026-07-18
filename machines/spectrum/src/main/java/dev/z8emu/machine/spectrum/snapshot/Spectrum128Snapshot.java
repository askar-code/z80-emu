package dev.z8emu.machine.spectrum.snapshot;

import java.util.Arrays;
import java.util.Objects;

/** Immutable CPU, paging, AY and all eight physical RAM-bank states for a Spectrum 128K. */
public final class Spectrum128Snapshot implements SpectrumSnapshot {
    public static final int RAM_BANK_COUNT = 8;
    public static final int RAM_BANK_SIZE = 16 * 1024;
    public static final int AY_REGISTER_COUNT = 16;

    private final Z80SnapshotState cpu;
    private final int borderColor;
    private final int pagingPort7ffd;
    private final int selectedAyRegister;
    private final byte[] ayRegisters;
    private final byte[][] ramBanks;

    public Spectrum128Snapshot(
            Z80SnapshotState cpu,
            int borderColor,
            int pagingPort7ffd,
            int selectedAyRegister,
            byte[] ayRegisters,
            byte[][] ramBanks
    ) {
        this.cpu = Objects.requireNonNull(cpu, "cpu");
        requireUnsigned("borderColor", borderColor, 7);
        requireUnsigned("pagingPort7ffd", pagingPort7ffd, 0xFF);
        requireUnsigned("selectedAyRegister", selectedAyRegister, 15);
        Objects.requireNonNull(ayRegisters, "ayRegisters");
        if (ayRegisters.length != AY_REGISTER_COUNT) {
            throw new IllegalArgumentException(
                    "AY state must contain exactly 16 registers, got " + ayRegisters.length
            );
        }
        Objects.requireNonNull(ramBanks, "ramBanks");
        if (ramBanks.length != RAM_BANK_COUNT) {
            throw new IllegalArgumentException(
                    "128K snapshot must contain exactly 8 RAM banks, got " + ramBanks.length
            );
        }

        this.borderColor = borderColor;
        this.pagingPort7ffd = pagingPort7ffd;
        this.selectedAyRegister = selectedAyRegister;
        this.ayRegisters = Arrays.copyOf(ayRegisters, ayRegisters.length);
        this.ramBanks = new byte[RAM_BANK_COUNT][];
        for (int index = 0; index < RAM_BANK_COUNT; index++) {
            byte[] bank = Objects.requireNonNull(ramBanks[index], "ramBanks[" + index + "]");
            if (bank.length != RAM_BANK_SIZE) {
                throw new IllegalArgumentException(
                        "RAM bank " + index + " must be exactly 16384 bytes, got " + bank.length
                );
            }
            this.ramBanks[index] = Arrays.copyOf(bank, bank.length);
        }
    }

    @Override
    public Z80SnapshotState cpu() {
        return cpu;
    }

    @Override
    public int borderColor() {
        return borderColor;
    }

    public int pagingPort7ffd() {
        return pagingPort7ffd;
    }

    public int selectedAyRegister() {
        return selectedAyRegister;
    }

    public byte[] ayRegisters() {
        return Arrays.copyOf(ayRegisters, ayRegisters.length);
    }

    public byte[] ramBank(int index) {
        validateBankIndex(index);
        return Arrays.copyOf(ramBanks[index], RAM_BANK_SIZE);
    }

    int ayRegister(int index) {
        return Byte.toUnsignedInt(ayRegisters[index]);
    }

    void copyRamBankTo(int index, byte[] target, int targetOffset) {
        validateBankIndex(index);
        System.arraycopy(ramBanks[index], 0, target, targetOffset, RAM_BANK_SIZE);
    }

    private void validateBankIndex(int index) {
        if (index < 0 || index >= RAM_BANK_COUNT) {
            throw new IllegalArgumentException("RAM bank index out of range: " + index);
        }
    }

    private static void requireUnsigned(String name, int value, int max) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(name + " must be between 0 and " + max + ": " + value);
        }
    }
}
