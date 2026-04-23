package dev.z8emu.machine.spectrum.model;

import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import java.util.Objects;

public final class SpectrumPagingController {
    private static final int PAGING_PORT_MASK = 0x8002;
    private static final int BANKM_SYSTEM_VARIABLE = 0x5B5C;
    private static final boolean TRACE_PAGING = Boolean.getBoolean("z8emu.tracePaging");

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

        if (TRACE_PAGING) {
            System.out.printf(
                    "paging-write port=0x%04X value=0x%02X locked=%s before[rom=%d top=%d screen=%d bankm=%02X]%n",
                    port & 0xFFFF,
                    value & 0xFF,
                    state.pagingLocked(),
                    state.selectedRomIndex(),
                    state.topRamBankIndex(),
                    state.activeScreenBankIndex(),
                    memory.read(BANKM_SYSTEM_VARIABLE)
            );
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
        if (TRACE_PAGING) {
            System.out.printf(
                    "paging-applied after[rom=%d top=%d screen=%d bankm=%02X locked=%s]%n",
                    state.selectedRomIndex(),
                    state.topRamBankIndex(),
                    state.activeScreenBankIndex(),
                    memory.read(BANKM_SYSTEM_VARIABLE),
                    state.pagingLocked()
            );
        }
        return true;
    }

    public boolean handlesPortWrite(int port) {
        return config.pagingSupported() && isPagingPort(port);
    }

    private boolean isPagingPort(int port) {
        return (port & PAGING_PORT_MASK) == 0;
    }

    private void syncBankmSystemVariable(int pagingValue) {
        if (!config.pagingSupported()) {
            return;
        }
        memory.write(BANKM_SYSTEM_VARIABLE, pagingValue);
    }
}
