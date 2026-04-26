package dev.z8emu.machine.spectrum.model;

import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.bus.io.IoSelector;
import java.util.Objects;

public final class SpectrumPagingController {
    private static final int PAGING_PORT_MASK = 0x8002;
    private static final int PAGING_PORT_VALUE = 0x0000;
    private static final int BANKM_SYSTEM_VARIABLE = 0x5B5C;
    private static final IoSelector PAGING_PORT_SELECTOR = IoSelector.mask(PAGING_PORT_MASK, PAGING_PORT_VALUE);

    private final SpectrumModelConfig config;
    private final SpectrumMachineState state;
    private final Spectrum48kMemoryMap memory;

    public SpectrumPagingController(SpectrumModelConfig config, SpectrumMachineState state, Spectrum48kMemoryMap memory) {
        this.config = Objects.requireNonNull(config, "config");
        this.state = Objects.requireNonNull(state, "state");
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    public boolean handlePortWrite(int port, int value) {
        if (!handlesPortWrite(port)) {
            return false;
        }

        if (state.pagingLocked()) {
            return true;
        }

        int normalized = value & 0xFF;
        state.setPagingPort7ffd(normalized);
        state.setTopRamBankIndex(normalized & 0x07);

        if (config.screenBankIndices().length > 1) {
            state.setActiveScreenBankOption((normalized >>> 3) & 0x01);
        }

        if (config.romBankCount() > 1) {
            state.setSelectedRomIndex((normalized >>> 4) & 0x01);
        }

        if ((normalized & 0x20) != 0) {
            state.setPagingLocked(true);
        }

        memory.applyState();
        syncBankmSystemVariable(normalized);
        return true;
    }

    public boolean handlesPortWrite(int port) {
        return config.pagingSupported() && isPagingPort(port);
    }

    public IoSelector portSelector() {
        return PAGING_PORT_SELECTOR;
    }

    private boolean isPagingPort(int port) {
        return PAGING_PORT_SELECTOR.matches(port);
    }

    private void syncBankmSystemVariable(int pagingValue) {
        if (!config.pagingSupported()) {
            return;
        }
        memory.write(BANKM_SYSTEM_VARIABLE, pagingValue);
    }
}
