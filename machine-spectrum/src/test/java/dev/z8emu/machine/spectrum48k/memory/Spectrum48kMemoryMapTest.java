package dev.z8emu.machine.spectrum48k.memory;

import dev.z8emu.machine.spectrum.model.SpectrumMachineState;
import dev.z8emu.machine.spectrum.model.SpectrumModelConfig;
import dev.z8emu.platform.memory.RamMemoryBank;
import dev.z8emu.platform.memory.ReadOnlyMemoryBank;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class Spectrum48kMemoryMapTest {
    @Test
    void keepsFixed48kSlotLayout() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum48k();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(config, state, new byte[Spectrum48kMemoryMap.ROM_SIZE]);

        memory.write(0x4000, 0x11);
        memory.write(0x8000, 0x22);
        memory.write(0xC000, 0x33);

        assertEquals(0x11, memory.read(0x4000));
        assertEquals(0x22, memory.read(0x8000));
        assertEquals(0x33, memory.read(0xC000));
        assertInstanceOf(ReadOnlyMemoryBank.class, memory.addressSpace().bankAtSlot(0));
        assertInstanceOf(RamMemoryBank.class, memory.addressSpace().bankAtSlot(1));
        assertInstanceOf(RamMemoryBank.class, memory.addressSpace().bankAtSlot(2));
        assertInstanceOf(RamMemoryBank.class, memory.addressSpace().bankAtSlot(3));
        assertSame(memory.romBank(0), memory.addressSpace().bankAtSlot(0));
        assertSame(memory.ramBank(2), memory.addressSpace().bankAtSlot(3));
    }

    @Test
    void usesMachineStateForPagedTopSlotWhileKeeping48kDefaults() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum48k();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(config, state, new byte[Spectrum48kMemoryMap.ROM_SIZE]);

        assertEquals(0, state.selectedRomIndex());
        assertEquals(2, state.topRamBankIndex());
        assertEquals(0, state.activeScreenBankIndex());
        assertEquals(false, state.pagingLocked());
        assertEquals(0, state.pagingPort7ffd());

        state.setTopRamBankIndex(1);
        memory.applyState();
        memory.write(0xC000, 0x5A);

        assertEquals(0x5A, memory.read(0x8000), "Top slot should follow the machine state mapping");
    }
}
