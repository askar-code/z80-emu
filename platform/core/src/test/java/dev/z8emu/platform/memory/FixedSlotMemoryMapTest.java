package dev.z8emu.platform.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedSlotMemoryMapTest {
    @Test
    void routesReadsAndWritesThroughMappedSlots() {
        FixedSlotMemoryMap addressSpace = new FixedSlotMemoryMap(0x4000, 4);
        ReadOnlyMemoryBank rom = new ReadOnlyMemoryBank(new byte[0x4000]);
        RamMemoryBank ram0 = new RamMemoryBank(0x4000);
        RamMemoryBank ram1 = new RamMemoryBank(0x4000);
        RamMemoryBank ram2 = new RamMemoryBank(0x4000);

        addressSpace.mapSlot(0, rom);
        addressSpace.mapSlot(1, ram0);
        addressSpace.mapSlot(2, ram1);
        addressSpace.mapSlot(3, ram2);

        addressSpace.write(0x4000, 0x12);
        addressSpace.write(0x8000, 0x34);
        addressSpace.write(0xC000, 0x56);
        addressSpace.write(0x0000, 0x7F);

        assertEquals(0x00, addressSpace.read(0x0000), "ROM writes must be ignored");
        assertEquals(0x12, addressSpace.read(0x4000));
        assertEquals(0x34, addressSpace.read(0x8000));
        assertEquals(0x56, addressSpace.read(0xC000));
    }

    @Test
    void resetClearsUniqueRamBanksOnlyOnce() {
        FixedSlotMemoryMap addressSpace = new FixedSlotMemoryMap(0x4000, 4);
        ReadOnlyMemoryBank rom = new ReadOnlyMemoryBank(new byte[0x4000]);
        RamMemoryBank sharedRam = new RamMemoryBank(0x4000);
        RamMemoryBank topRam = new RamMemoryBank(0x4000);

        addressSpace.mapSlot(0, rom);
        addressSpace.mapSlot(1, sharedRam);
        addressSpace.mapSlot(2, sharedRam);
        addressSpace.mapSlot(3, topRam);

        addressSpace.write(0x4000, 0xAA);
        addressSpace.write(0x8000, 0xBB);
        addressSpace.write(0xC000, 0xCC);

        addressSpace.reset();

        assertEquals(0x00, addressSpace.read(0x4000));
        assertEquals(0x00, addressSpace.read(0x8000));
        assertEquals(0x00, addressSpace.read(0xC000));
    }
}
