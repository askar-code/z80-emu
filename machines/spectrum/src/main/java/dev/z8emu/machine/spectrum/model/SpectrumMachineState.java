package dev.z8emu.machine.spectrum.model;

import java.util.Objects;

public final class SpectrumMachineState {
    private final SpectrumModelConfig config;

    private int selectedRomIndex;
    private int topRamBankIndex;
    private int activeScreenBankOption;
    private boolean pagingLocked;
    private int pagingPort7ffd;

    public SpectrumMachineState(SpectrumModelConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        reset();
    }

    public SpectrumModelConfig config() {
        return config;
    }

    public void reset() {
        selectedRomIndex = config.defaultRomIndex();
        topRamBankIndex = config.defaultTopRamBankIndex();
        activeScreenBankOption = config.defaultScreenBankIndex();
        pagingLocked = false;
        pagingPort7ffd = 0;
    }

    public int selectedRomIndex() {
        return selectedRomIndex;
    }

    public void setSelectedRomIndex(int selectedRomIndex) {
        validateIndex(selectedRomIndex, config.romBankCount(), "selectedRomIndex");
        this.selectedRomIndex = selectedRomIndex;
    }

    public int topRamBankIndex() {
        return topRamBankIndex;
    }

    public void setTopRamBankIndex(int topRamBankIndex) {
        validateIndex(topRamBankIndex, config.ramBankCount(), "topRamBankIndex");
        this.topRamBankIndex = topRamBankIndex;
    }

    public int activeScreenBankOption() {
        return activeScreenBankOption;
    }

    public void setActiveScreenBankOption(int activeScreenBankOption) {
        validateIndex(activeScreenBankOption, config.screenBankIndices().length, "activeScreenBankOption");
        this.activeScreenBankOption = activeScreenBankOption;
    }

    public int activeScreenBankIndex() {
        return config.screenBankIndex(activeScreenBankOption);
    }

    public boolean pagingLocked() {
        return pagingLocked;
    }

    public void setPagingLocked(boolean pagingLocked) {
        this.pagingLocked = pagingLocked;
    }

    public int pagingPort7ffd() {
        return pagingPort7ffd & 0xFF;
    }

    public void setPagingPort7ffd(int pagingPort7ffd) {
        this.pagingPort7ffd = pagingPort7ffd & 0xFF;
    }

    private static void validateIndex(int index, int size, String name) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("%s out of range: %d".formatted(name, index));
        }
    }
}
