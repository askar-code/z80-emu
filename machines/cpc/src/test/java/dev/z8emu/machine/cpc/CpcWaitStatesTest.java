package dev.z8emu.machine.cpc;

import dev.z8emu.cpu.z80.Z80Cpu;
import dev.z8emu.machine.cpc.memory.CpcMemory;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.time.TStateCounter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CpcWaitStatesTest {
    private static final int NOP = 0x00;
    private static final int LD_A_IMMEDIATE = 0x3E;
    private static final int LD_ADDRESS_A = 0x32;
    private static final int JP = 0xC3;
    private static final int CALL = 0xCD;
    private static final int IN_A_IMMEDIATE = 0xDB;
    private static final int OUT_IMMEDIATE_A = 0xD3;
    private static final int ED_PREFIX = 0xED;
    private static final int LDIR = 0xB0;

    @Test
    void allCpuAccessHooksAlignToTheFourTStateGrid() {
        TStateCounter clock = new TStateCounter();
        CpcBoard board = new CpcBoard(new byte[CpcMemory.ROM_SIZE], clock);
        CpuBus bus = board.cpuBus();

        for (int clockValue = 0; clockValue <= 7; clockValue++) {
            for (int phase = 0; phase <= 5; phase++) {
                int expected = gridAlignWaits(clockValue, phase);
                String message = "clock=" + clockValue + ", phase=" + phase;
                assertEquals(expected, bus.fetchOpcodeWaitStates(0x0000, phase), message);
                assertEquals(expected, bus.readMemoryWaitStates(0x4000, phase), message);
                assertEquals(expected, bus.writeMemoryWaitStates(0x4000, 0x55, phase), message);
                assertEquals(expected, bus.readPortWaitStates(0xF400, phase), message);
                assertEquals(expected, bus.writePortWaitStates(0x7F00, 0x55, phase), message);
            }
            clock.advance(1);
        }

        assertEquals(0, gridAlignWaits(0, 0));
        assertEquals(3, gridAlignWaits(1, 0));
        assertEquals(1, gridAlignWaits(2, 1));
        assertEquals(0, gridAlignWaits(4_002, 2));
    }

    @Test
    void portAccessesUseTheWaitAdjustedAbsoluteTState() {
        TStateCounter clock = new TStateCounter();
        CpcBoard board = new CpcBoard(new byte[CpcMemory.ROM_SIZE], clock);
        CpuBus bus = board.cpuBus();
        List<IoAccess> accesses = new ArrayList<>();
        board.setIoTraceSink((mappingName, read, access, value) -> accesses.add(access));
        clock.advance(2);

        bus.readPort(0xF400, 1);
        bus.writePort(0x7F00, 0x55, 1);

        assertEquals(2, accesses.size());
        for (IoAccess access : accesses) {
            assertEquals(3, access.tState());
            assertEquals(1, access.phaseTStates());
            assertEquals(4, access.effectiveTState());
        }
    }

    @Test
    void pinsSingleInstructionTotalsFromAnAlignedStart() {
        // Fetch at phase 0 is already aligned.
        assertEquals(4, rig(NOP).runInstruction());

        // Fetch at 0 and operand read at 4 are both aligned.
        assertEquals(7, rig(LD_A_IMMEDIATE, 0x42).runInstruction());

        // Fetch at 0, low byte at 4, high byte at 7: one wait before the high byte.
        assertEquals(11, rig(JP, 0x05, 0x00).runInstruction());

        // Reads add 0+0+1 waits and the two stack pushes add 1+1: 17+3.
        assertEquals(20, rig(CALL, 0x05, 0x00).runInstruction());

        // Fetch at 0, operand at 4, port write at 7: 11+1.
        assertEquals(12, rig(OUT_IMMEDIATE_A, 0x00).runInstruction());
    }

    @Test
    void pinsLdirFirstAndSteadyStateIterations() {
        Rig rig = rig(ED_PREFIX, LDIR);
        rig.cpu().registers().setHl(0x4000);
        rig.cpu().registers().setDe(0x5000);
        rig.cpu().registers().setBc(0x0003);
        rig.bus().writeMemory(0x4000, 0x11);
        rig.bus().writeMemory(0x4001, 0x22);

        // ED at 0, B0 at 4, read at 8, write at 11: 21+1.
        assertEquals(22, rig.runInstruction());
        // The repeat starts at clock 2 mod 4; fetch waits 2 and the write waits 1: 21+3.
        assertEquals(24, rig.runInstruction());
    }

    @Test
    void nextFetchAbsorbsTrailingSubGridMisalignment() {
        Rig rig = rig(LD_A_IMMEDIATE, 0x42, NOP);

        assertEquals(7, rig.runInstruction());
        assertEquals(5, rig.runInstruction());
        assertEquals(12, rig.clock().value());
    }

    @Test
    void misalignedStartDelaysTheFirstFetch() {
        Rig rig = rig(NOP);
        rig.clock().advance(2);

        assertEquals(6, rig.runInstruction());
    }

    @Test
    void mixedInstructionStreamKeepsEveryAccessOnGrid() {
        TStateCounter clock = new TStateCounter();
        byte[] rom = program(
                LD_A_IMMEDIATE, 0x12,
                LD_ADDRESS_A, 0x00, 0x40,
                IN_A_IMMEDIATE, 0x00,
                OUT_IMMEDIATE_A, 0x00,
                NOP
        );
        CpcBoard board = new CpcBoard(rom, clock);
        RecordingBus bus = new RecordingBus(board.cpuBus(), clock);
        Z80Cpu cpu = new Z80Cpu(bus);

        int total = 0;
        for (int instruction = 0; instruction < 5; instruction++) {
            int tStates = cpu.runInstruction();
            total += tStates;
            clock.advance(tStates);
        }

        // Per-access alignment gives 7 + 16 + 13 + 12 + 4 for this stream.
        assertEquals(52, total);
        assertEquals(52, clock.value());
        assertEquals(
                new HashSet<>(List.of("fetch", "read", "write", "port-read", "port-write")),
                new HashSet<>(bus.accessKinds())
        );
    }

    private static int gridAlignWaits(long clockValue, int phaseTStates) {
        return (4 - (int) ((clockValue + phaseTStates) & 3)) & 3;
    }

    private static Rig rig(int... opcodes) {
        TStateCounter clock = new TStateCounter();
        CpcBoard board = new CpcBoard(program(opcodes), clock);
        CpuBus bus = board.cpuBus();
        return new Rig(new Z80Cpu(bus), bus, clock);
    }

    private static byte[] program(int... opcodes) {
        byte[] rom = new byte[CpcMemory.ROM_SIZE];
        for (int index = 0; index < opcodes.length; index++) {
            rom[index] = (byte) opcodes[index];
        }
        return rom;
    }

    private record Rig(Z80Cpu cpu, CpuBus bus, TStateCounter clock) {
        int runInstruction() {
            int tStates = cpu.runInstruction();
            clock.advance(tStates);
            return tStates;
        }
    }

    private static final class RecordingBus implements CpuBus {
        private final CpuBus delegate;
        private final TStateCounter clock;
        private final List<String> accessKinds = new ArrayList<>();

        private RecordingBus(CpuBus delegate, TStateCounter clock) {
            this.delegate = delegate;
            this.clock = clock;
        }

        List<String> accessKinds() {
            return List.copyOf(accessKinds);
        }

        @Override
        public int fetchOpcode(int address) {
            return delegate.fetchOpcode(address);
        }

        @Override
        public int fetchOpcodeWaitStates(int address, int phaseTStates) {
            return record("fetch", phaseTStates, delegate.fetchOpcodeWaitStates(address, phaseTStates));
        }

        @Override
        public int readMemory(int address) {
            return delegate.readMemory(address);
        }

        @Override
        public int readMemoryWaitStates(int address, int phaseTStates) {
            return record("read", phaseTStates, delegate.readMemoryWaitStates(address, phaseTStates));
        }

        @Override
        public void writeMemory(int address, int value) {
            delegate.writeMemory(address, value);
        }

        @Override
        public int writeMemoryWaitStates(int address, int value, int phaseTStates) {
            return record("write", phaseTStates, delegate.writeMemoryWaitStates(address, value, phaseTStates));
        }

        @Override
        public int readPort(int port, int phaseTStates) {
            return delegate.readPort(port, phaseTStates);
        }

        @Override
        public int readPortWaitStates(int port, int phaseTStates) {
            return record("port-read", phaseTStates, delegate.readPortWaitStates(port, phaseTStates));
        }

        @Override
        public void writePort(int port, int value, int phaseTStates) {
            delegate.writePort(port, value, phaseTStates);
        }

        @Override
        public int writePortWaitStates(int port, int value, int phaseTStates) {
            return record("port-write", phaseTStates, delegate.writePortWaitStates(port, value, phaseTStates));
        }

        private int record(String kind, int phaseTStates, int waitStates) {
            accessKinds.add(kind);
            assertEquals(
                    0,
                    (clock.value() + phaseTStates + waitStates) & 3,
                    kind + " at clock=" + clock.value() + ", phase=" + phaseTStates
            );
            return waitStates;
        }
    }
}
