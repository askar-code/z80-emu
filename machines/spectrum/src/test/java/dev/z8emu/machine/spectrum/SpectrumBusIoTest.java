package dev.z8emu.machine.spectrum;

import dev.z8emu.machine.spectrum128k.Spectrum128Board;
import dev.z8emu.machine.spectrum48k.Spectrum48kBoard;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.time.TStateCounter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectrumBusIoTest {
    @Test
    void floatingBusReturnsScreenAndAttributeBytesOn48k() {
        TStateCounter clock = new TStateCounter();
        Spectrum48kBoard board = new Spectrum48kBoard(new byte[Spectrum48kMemoryMap.ROM_SIZE], clock);
        var memory = board.memory();
        var bus = board.cpuBus();

        memory.write(0x4000, 0x12);
        memory.write(0x5800, 0x34);
        memory.write(0x4001, 0x56);
        memory.write(0x5801, 0x78);

        clock.advance(14_347);
        assertEquals(0x12, bus.readPort(0xFFFF));
        clock.advance(1);
        assertEquals(0x34, bus.readPort(0xFFFF));
        clock.advance(1);
        assertEquals(0x56, bus.readPort(0xFFFF));
        clock.advance(1);
        assertEquals(0x78, bus.readPort(0xFFFF));
        clock.advance(1);
        assertEquals(0xFF, bus.readPort(0xFFFF));
    }

    @Test
    void floatingBusAlsoAppliesToUnmappedPortsOn128k() {
        TStateCounter clock = new TStateCounter();
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );
        var memory = board.memory();
        var bus = board.cpuBus();

        memory.ramBank(5).write(0, 0x9A);
        memory.ramBank(5).write(0x1800, 0xBC);

        clock.advance(14_368);
        assertEquals(0x9A, bus.readPort(0x7FFD));
        clock.advance(1);
        assertEquals(0xBC, bus.readPort(0x7FFD));
    }

    @Test
    void pagingPortSelectsDistinctTopRamBanksForReadsAndWrites() {
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new TStateCounter()
        );
        var bus = board.cpuBus();

        bus.writePort(0x7FFD, 0x03);
        bus.writeMemory(0xC000, 0x5A);

        bus.writePort(0x7FFD, 0x04);
        bus.writeMemory(0xC000, 0xA5);

        bus.writePort(0x7FFD, 0x03);
        assertEquals(0x5A, bus.readMemory(0xC000));

        bus.writePort(0x7FFD, 0x04);
        assertEquals(0xA5, bus.readMemory(0xC000));
    }

    @Test
    void portFeUpdatesBorderWithoutChanging128kPagingState() {
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new TStateCounter()
        );
        var state = board.machineState();
        var ula = board.ula();
        var bus = board.cpuBus();

        bus.writePort(0x7FFD, 0x1B);
        bus.writePort(0x00FE, 0x05);

        assertEquals(5, ula.borderColor());
        assertEquals(1, state.selectedRomIndex());
        assertEquals(3, state.topRamBankIndex());
        assertEquals(7, state.activeScreenBankIndex());
        assertEquals(0x1B, state.pagingPort7ffd());
    }

    @Test
    void ayPortsDoNotChangePagingStateOrMemoryMapping() {
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new TStateCounter()
        );
        var state = board.machineState();
        var bus = board.cpuBus();

        bus.writePort(0x7FFD, 0x1B);
        bus.writePort(0xFFFD, 0x08);
        bus.writePort(0xBFFD, 0x0F);

        assertEquals(1, state.selectedRomIndex());
        assertEquals(3, state.topRamBankIndex());
        assertEquals(7, state.activeScreenBankIndex());
        assertEquals(0x1B, state.pagingPort7ffd());
        assertEquals(0x0F, bus.readPort(0xFFFD));
    }

    @Test
    void phasedAyReadsReturnSelectedRegisterInsteadOfFloatingBus() {
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new TStateCounter()
        );
        var bus = board.cpuBus();

        bus.writePort(0xFFFD, 0x08);
        bus.writePort(0xBFFD, 0x1F);

        assertEquals(0x1F, bus.readPort(0xFFFD, 8));
    }
}
