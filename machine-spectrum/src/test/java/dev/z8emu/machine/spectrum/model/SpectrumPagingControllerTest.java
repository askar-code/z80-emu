package dev.z8emu.machine.spectrum.model;

import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumPagingControllerTest {
    @Test
    void applies128PagingStateToMemorySlots() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum128();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(
                config,
                state,
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE]
        );
        SpectrumPagingController controller = new SpectrumPagingController(config, state, memory);

        boolean handled = controller.handlePortWrite(0x7FFD, 0x1B);

        assertTrue(handled);
        assertEquals(1, state.selectedRomIndex());
        assertEquals(3, state.topRamBankIndex());
        assertEquals(1, state.activeScreenBankOption());
        assertEquals(7, state.activeScreenBankIndex());
        assertEquals(0x1B, state.pagingPort7ffd());
        assertSame(memory.romBank(1), memory.addressSpace().bankAtSlot(0));
        assertSame(memory.ramBank(3), memory.addressSpace().bankAtSlot(3));
    }

    @Test
    void ignoresFurtherWritesAfterPagingIsLocked() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum128();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(
                config,
                state,
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE]
        );
        SpectrumPagingController controller = new SpectrumPagingController(config, state, memory);

        controller.handlePortWrite(0x7FFD, 0x20);
        controller.handlePortWrite(0x7FFD, 0x1B);

        assertTrue(state.pagingLocked());
        assertEquals(0, state.selectedRomIndex());
        assertEquals(0, state.topRamBankIndex());
        assertEquals(5, state.activeScreenBankIndex());
        assertEquals(0x20, state.pagingPort7ffd());
        assertSame(memory.romBank(0), memory.addressSpace().bankAtSlot(0));
        assertSame(memory.ramBank(0), memory.addressSpace().bankAtSlot(3));
    }

    @Test
    void mirrorsPagingValueIntoBankmSystemVariable() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum128();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(
                config,
                state,
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE]
        );
        SpectrumPagingController controller = new SpectrumPagingController(config, state, memory);

        controller.handlePortWrite(0x7FFD, 0x1B);

        assertEquals(0x1B, memory.read(0x5B5C));
    }
}
