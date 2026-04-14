package dev.z8emu.machine.spectrum.model;

import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import java.util.Objects;

public final class SpectrumPagingController {
    private static final int PAGING_PORT_MASK = 0x8002;

    private final SpectrumModelConfig config;
    private final SpectrumMachineState state;
    private final Spectrum48kMemoryMap memory;

    public SpectrumPagingController(SpectrumModelConfig config, SpectrumMachineState state, Spectrum48kMemoryMap memory) {
        this.config = Objects.requireNonNull(config, "config");
        this.state = Objects.requireNonNull(state, "state");
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    public boolean handlePortWrite(int port, int value) {
        if (!config.pagingSupported() || !isPagingPort(port)) {
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
        return true;
    }

    private boolean isPagingPort(int port) {
        return (port & PAGING_PORT_MASK) == 0;
    }
}
