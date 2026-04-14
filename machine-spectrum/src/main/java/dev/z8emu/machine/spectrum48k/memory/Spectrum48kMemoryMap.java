package dev.z8emu.machine.spectrum48k.memory;

import dev.z8emu.machine.spectrum.memory.SpectrumDisplayMemory;
import dev.z8emu.machine.spectrum.model.SpectrumMachineState;
import dev.z8emu.machine.spectrum.model.SpectrumModelConfig;
import dev.z8emu.platform.memory.AddressSpace;
import dev.z8emu.platform.memory.FixedSlotMemoryMap;
import dev.z8emu.platform.memory.RamMemoryBank;
import dev.z8emu.platform.memory.ReadOnlyMemoryBank;
import java.util.Objects;

public final class Spectrum48kMemoryMap implements AddressSpace, SpectrumDisplayMemory {
    public static final int ROM_SIZE = 16 * 1024;
    public static final int RAM_BANK_SIZE = 16 * 1024;
    public static final int RAM_SIZE = 48 * 1024;
    private static final int SLOT_COUNT = 4;

    private final SpectrumModelConfig config;
    private final SpectrumMachineState state;
    private final ReadOnlyMemoryBank[] romBanks;
    private final RamMemoryBank[] ramBanks;
    private final FixedSlotMemoryMap addressSpace = new FixedSlotMemoryMap(ROM_SIZE, SLOT_COUNT);
    private WriteListener writeListener;

    public Spectrum48kMemoryMap(SpectrumModelConfig config, SpectrumMachineState state, byte[]... romImages) {
        this.config = Objects.requireNonNull(config, "config");
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(romImages, "romImages");
        if (romImages.length != config.romBankCount()) {
            throw new IllegalArgumentException("Expected %d ROM banks, got %d".formatted(config.romBankCount(), romImages.length));
        }

        this.romBanks = new ReadOnlyMemoryBank[config.romBankCount()];
        for (int i = 0; i < romImages.length; i++) {
            byte[] romImage = Objects.requireNonNull(romImages[i], "romImages[" + i + "]");
            if (romImage.length != ROM_SIZE) {
                throw new IllegalArgumentException("Spectrum ROM image must be exactly 16 KB");
            }
            romBanks[i] = new ReadOnlyMemoryBank(romImage);
        }

        this.ramBanks = new RamMemoryBank[config.ramBankCount()];
        for (int i = 0; i < ramBanks.length; i++) {
            ramBanks[i] = new RamMemoryBank(RAM_BANK_SIZE);
        }
        applyState();
    }

    @Override
    public int read(int address) {
        return addressSpace.read(address);
    }

    @Override
    public void write(int address, int value) {
        int normalized = address & 0xFFFF;
        if (normalized < ROM_SIZE) {
            addressSpace.write(normalized, value);
            return;
        }

        int oldValue = addressSpace.read(normalized);
        addressSpace.write(normalized, value);
        int newValue = addressSpace.read(normalized);
        if (writeListener != null && oldValue != newValue) {
            writeListener.onWrite(normalized, oldValue, newValue);
        }
    }

    @Override
    public void reset() {
        addressSpace.reset();
        applyState();
    }

    public FixedSlotMemoryMap addressSpace() {
        return addressSpace;
    }

    public SpectrumModelConfig config() {
        return config;
    }

    public SpectrumMachineState state() {
        return state;
    }

    public ReadOnlyMemoryBank romBank(int index) {
        if (index < 0 || index >= romBanks.length) {
            throw new IllegalArgumentException("ROM bank index out of range: " + index);
        }
        return romBanks[index];
    }

    public RamMemoryBank ramBank(int index) {
        if (index < 0 || index >= ramBanks.length) {
            throw new IllegalArgumentException("RAM bank index out of range: " + index);
        }
        return ramBanks[index];
    }

    public void applyState() {
        addressSpace.mapSlot(0, romBanks[state.selectedRomIndex()]);
        addressSpace.mapSlot(1, ramBanks[config.fixedLowerRamBankIndex()]);
        addressSpace.mapSlot(2, ramBanks[config.fixedMiddleRamBankIndex()]);
        addressSpace.mapSlot(3, ramBanks[state.topRamBankIndex()]);
    }

    public void setWriteListener(WriteListener writeListener) {
        this.writeListener = writeListener;
    }

    @Override
    public int readDisplayMemory(int address) {
        int normalized = address & 0xFFFF;
        if (normalized < 0x4000 || normalized >= 0x5B00) {
            return read(normalized);
        }

        int offset = normalized - 0x4000;
        return ramBanks[state.activeScreenBankIndex()].read(offset);
    }

    @FunctionalInterface
    public interface WriteListener {
        void onWrite(int address, int oldValue, int newValue);
    }
}
