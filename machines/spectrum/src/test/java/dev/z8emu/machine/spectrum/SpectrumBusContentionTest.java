package dev.z8emu.machine.spectrum;

import dev.z8emu.cpu.z80.Z80Cpu;
import dev.z8emu.machine.spectrum128k.Spectrum128Board;
import dev.z8emu.machine.spectrum48k.Spectrum48kBoard;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.time.TStateCounter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectrumBusContentionTest {
    private static final int LD_IMMEDIATE_ADDRESS_A = 0x32;
    private static final int ED_PREFIX = 0xED;
    private static final int LDIR = 0xB0;

    @Test
    void spectrum48kDelaysOnlyLowerRamDuringVisibleFetchWindow() {
        TStateCounter clock = new TStateCounter();
        Spectrum48kBoard board = new Spectrum48kBoard(new byte[Spectrum48kMemoryMap.ROM_SIZE], clock);
        var bus = board.cpuBus();

        clock.advance(14_335);

        assertEquals(6, bus.readMemoryWaitStates(0x4000, 0));
        assertEquals(5, bus.readMemoryWaitStates(0x4000, 1));
        assertEquals(3, bus.writeMemoryWaitStates(0x7FFF, 0x55, 3));
        assertEquals(0, bus.readMemoryWaitStates(0x8000, 0));
        assertEquals(0, bus.fetchOpcodeWaitStates(0x0000, 0));
    }

    @Test
    void spectrum128kDelaysLowerScreenRamAndPagedOddTopBanks() {
        TStateCounter clock = new TStateCounter();
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );
        var state = board.machineState();
        var memory = board.memory();
        var bus = board.cpuBus();

        clock.advance(14_361);

        assertEquals(6, bus.readMemoryWaitStates(0x4000, 0));
        assertEquals(5, bus.readMemoryWaitStates(0x4000, 1));
        assertEquals(3, bus.writeMemoryWaitStates(0x7FFF, 0x55, 3));
        assertEquals(0, bus.readMemoryWaitStates(0x8000, 0));
        assertEquals(0, bus.readMemoryWaitStates(0xC000, 0));

        state.setTopRamBankIndex(7);
        memory.applyState();

        assertEquals(6, bus.readMemoryWaitStates(0xC000, 0));
    }

    @Test
    void spectrum128kDelaysCpuScreenRamWriteUsingInstructionPhase() {
        byte[] rom0 = new byte[Spectrum48kMemoryMap.ROM_SIZE];
        rom0[0] = (byte) LD_IMMEDIATE_ADDRESS_A;
        rom0[1] = 0x00;
        rom0[2] = 0x40;
        TStateCounter clock = new TStateCounter();
        Spectrum128Board board = new Spectrum128Board(
                rom0,
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );
        var memory = board.memory();
        var bus = board.cpuBus();
        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setA(0xA5);
        clock.advance(14_351);

        assertEquals(19, cpu.runInstruction());
        assertEquals(0xA5, memory.read(0x4000));
    }

    @Test
    void spectrum128kDelaysLdirScreenRamWriteUsingInstructionPhase() {
        byte[] rom0 = new byte[Spectrum48kMemoryMap.ROM_SIZE];
        rom0[0] = (byte) ED_PREFIX;
        rom0[1] = (byte) LDIR;
        TStateCounter clock = new TStateCounter();
        Spectrum128Board board = new Spectrum128Board(
                rom0,
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );
        var memory = board.memory();
        memory.write(0x8000, 0x3C);
        var bus = board.cpuBus();
        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setHl(0x8000);
        cpu.registers().setDe(0x4000);
        cpu.registers().setBc(0x0001);
        clock.advance(14_350);

        assertEquals(22, cpu.runInstruction());
        assertEquals(0x3C, memory.read(0x4000));
    }
}
