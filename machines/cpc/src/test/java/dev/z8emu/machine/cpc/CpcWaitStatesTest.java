package dev.z8emu.machine.cpc;

import dev.z8emu.cpu.z80.Z80Cpu;
import dev.z8emu.cpu.z80.Z80Registers;
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
                int expectedMemory = gridAlignWaits(clockValue, phase);
                int expectedIo = ioAlignWaits(clockValue, phase);
                String message = "clock=" + clockValue + ", phase=" + phase;
                assertEquals(expectedMemory, bus.fetchOpcodeWaitStates(0x0000, phase), message);
                assertEquals(expectedMemory, bus.readMemoryWaitStates(0x4000, phase), message);
                assertEquals(expectedMemory, bus.writeMemoryWaitStates(0x4000, 0x55, phase), message);
                assertEquals(expectedIo, bus.readPortWaitStates(0xF400, phase), message);
                assertEquals(expectedIo, bus.writePortWaitStates(0x7F00, 0x55, phase), message);
                assertEquals(expectedIo,
                        bus.internalCycleWaitStates(CpuBus.NO_ADDRESS, phase, 7, CpuBus.InternalCycleType.INTERRUPT_ACKNOWLEDGE),
                        message);
                assertEquals(0,
                        bus.internalCycleWaitStates(0x4000, phase, 2, CpuBus.InternalCycleType.WRITE_NO_MREQ),
                        message);
            }
            clock.advance(1);
        }

        assertEquals(0, gridAlignWaits(0, 0));
        assertEquals(3, gridAlignWaits(1, 0));
        assertEquals(1, gridAlignWaits(2, 1));
        assertEquals(0, gridAlignWaits(4_002, 2));
        assertEquals(3, ioAlignWaits(0, 0));
        assertEquals(0, ioAlignWaits(2, 1));
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

        // clock 2 + phase 1 is already io-aligned, so the timestamp stays 2 and its effective time is 3.
        assertEquals(2, accesses.size());
        for (IoAccess access : accesses) {
            assertEquals(2, access.tState());
            assertEquals(1, access.phaseTStates());
            assertEquals(3, access.effectiveTState());
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

        // Reads add 0+0+1 waits; +1 internal moves the writes to phases 12/15 with waits 0+1: 17+2.
        assertEquals(19, rig(CALL, 0x05, 0x00).runInstruction());

        // Fetch at 0, operand at 4, port write at phase 7 is already on the io-class grid: 11+0.
        assertEquals(11, rig(OUT_IMMEDIATE_A, 0x00).runInstruction());
    }

    @Test
    void pinsLdirFirstAndSteadyStateIterations() {
        Rig rig = rig(ED_PREFIX, LDIR);
        rig.cpu().registers().setHl(0x4000);
        rig.cpu().registers().setDe(0x5000);
        rig.cpu().registers().setBc(0x0003);
        rig.bus().writeMemory(0x4000, 0x11);
        rig.bus().writeMemory(0x4001, 0x22);

        // ED at 0, B0 at 4, read at 8, write at 11, then +2/+5 internal: 21+1.
        assertEquals(22, rig.runInstruction());
        // The repeat starts at clock 2 mod 4; fetch waits 2 and the write waits 1: 21+3.
        assertEquals(24, rig.runInstruction());
    }

    @Test
    void nextFetchAbsorbsTrailingSubGridMisalignment() {
        Rig rig = rig(LD_A_IMMEDIATE, 0x42, NOP);

        // LD ends after 7t; the following fetch waits 1t, so its NOP costs 5t and the pair ends at 12t.
        assertEquals(7, rig.runInstruction());
        assertEquals(5, rig.runInstruction());
        assertEquals(12, rig.clock().value());
    }

    @Test
    void misalignedStartDelaysTheFirstFetch() {
        Rig rig = rig(NOP);
        rig.clock().advance(2);

        // A NOP starting at clock 2 waits 2t before its 4t fetch.
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

        // The io rule moves IN's old wait into the final fetch: 7 + 16 + 12 + 12 + 5 = 52.
        assertEquals(52, total);
        assertEquals(52, clock.value());
        assertEquals(
                new HashSet<>(List.of("fetch", "read", "write", "port-read", "port-write")),
                new HashSet<>(bus.accessKinds())
        );
    }

    @Test
    void effectiveDurationsMatchMeasuredCpcNopTable() {
        assertEffectiveNops("NOP", 1, new int[]{0x00}, rig -> { });
        assertEffectiveNops("LD A,n", 2, new int[]{0x3E, 0x42}, rig -> { });
        assertEffectiveNops("JP nn", 3, new int[]{0xC3, 0x03, 0x00}, rig -> { });
        assertEffectiveNops("JR d", 3, new int[]{0x18, 0x00}, rig -> { });
        assertEffectiveNops("JR NZ,d not taken", 2, new int[]{0x20, 0x00},
                rig -> rig.cpu().registers().setF(Z80Registers.FLAG_Z));
        assertEffectiveNops("CALL nn", 5, new int[]{0xCD, 0x03, 0x00}, CpcWaitStatesTest::prepareStack);
        assertEffectiveNops("CALL NZ,nn not taken", 3, new int[]{0xC4, 0x03, 0x00},
                rig -> rig.cpu().registers().setF(Z80Registers.FLAG_Z));
        assertEffectiveNops("PUSH BC", 4, new int[]{0xC5}, CpcWaitStatesTest::prepareStack);
        assertEffectiveNops("PUSH IX", 5, new int[]{0xDD, 0xE5}, CpcWaitStatesTest::prepareStack);
        assertEffectiveNops("RST 38h", 4, new int[]{0xFF}, CpcWaitStatesTest::prepareStack);
        assertEffectiveNops("RET", 3, new int[]{0xC9}, CpcWaitStatesTest::prepareReturn);
        assertEffectiveNops("RET NZ taken", 4, new int[]{0xC0}, CpcWaitStatesTest::prepareReturn);
        assertEffectiveNops("RET NZ not taken", 2, new int[]{0xC0}, rig ->
                rig.cpu().registers().setF(Z80Registers.FLAG_Z));
        assertEffectiveNops("DJNZ taken", 4, new int[]{0x10, 0x00}, rig -> rig.cpu().registers().setB(2));
        assertEffectiveNops("DJNZ not taken", 3, new int[]{0x10, 0x00}, rig -> rig.cpu().registers().setB(1));
        assertEffectiveNops("INC BC", 2, new int[]{0x03}, rig -> { });
        assertEffectiveNops("LD SP,HL", 2, new int[]{0xF9}, rig -> { });
        assertEffectiveNops("ADD HL,BC", 3, new int[]{0x09}, rig -> { });
        assertEffectiveNops("LD A,I", 3, new int[]{0xED, 0x57}, rig -> { });
        assertEffectiveNops("EX (SP),HL", 6, new int[]{0xE3}, CpcWaitStatesTest::prepareStack);
        assertEffectiveNops("EX (SP),IX", 7, new int[]{0xDD, 0xE3}, CpcWaitStatesTest::prepareStack);
        assertEffectiveNops("INC (HL)", 3, new int[]{0x34}, CpcWaitStatesTest::prepareHl);
        assertEffectiveNops("SET 0,(HL)", 4, new int[]{0xCB, 0xC6}, CpcWaitStatesTest::prepareHl);
        assertEffectiveNops("BIT 0,(HL)", 3, new int[]{0xCB, 0x46}, CpcWaitStatesTest::prepareHl);
        assertEffectiveNops("RRD", 5, new int[]{0xED, 0x67}, CpcWaitStatesTest::prepareHl);
        assertEffectiveNops("LD B,(IX+d)", 5, new int[]{0xDD, 0x46, 0x00}, CpcWaitStatesTest::prepareIx);
        assertEffectiveNops("LD (IX+d),B", 5, new int[]{0xDD, 0x70, 0x00}, CpcWaitStatesTest::prepareIx);
        assertEffectiveNops("LD (IX+d),n", 6, new int[]{0xDD, 0x36, 0x00, 0x55}, CpcWaitStatesTest::prepareIx);
        assertEffectiveNops("SET 0,(IX+d)", 7, new int[]{0xDD, 0xCB, 0x00, 0xC6}, CpcWaitStatesTest::prepareIx);
        assertEffectiveNops("BIT 0,(IX+d)", 6, new int[]{0xDD, 0xCB, 0x00, 0x46}, CpcWaitStatesTest::prepareIx);
        assertEffectiveNops("OUT (n),A", 3, new int[]{0xD3, 0x00}, rig -> { });
        assertEffectiveNops("IN A,(n)", 3, new int[]{0xDB, 0x00}, rig -> { });
        assertEffectiveNops("OUT (C),B", 4, new int[]{0xED, 0x41}, CpcWaitStatesTest::prepareBlockIo);
        assertEffectiveNops("IN B,(C)", 4, new int[]{0xED, 0x40}, CpcWaitStatesTest::prepareBlockIo);
        assertEffectiveNops("LDI", 5, new int[]{0xED, 0xA0}, CpcWaitStatesTest::prepareBlockTransfer);
        assertEffectiveNops("CPI", 4, new int[]{0xED, 0xA1}, CpcWaitStatesTest::prepareBlockCompare);
        assertEffectiveNops("OUTI", 5, new int[]{0xED, 0xA3}, CpcWaitStatesTest::prepareBlockIo);
        assertEffectiveNops("INI", 5, new int[]{0xED, 0xA2}, CpcWaitStatesTest::prepareBlockIo);

        MeasuredRig ldirRepeat = measuredRig(0xED, 0xB0);
        prepareBlockTransfer(ldirRepeat);
        ldirRepeat.cpu().registers().setBc(3);
        ldirRepeat.runInstruction();
        assertNextFetchDuration("LDIR repeat iteration", 6, ldirRepeat);
        assertEffectiveNops("LDIR final iteration", 5, new int[]{0xED, 0xB0}, rig -> {
            prepareBlockTransfer(rig);
            rig.cpu().registers().setBc(1);
        });

        MeasuredRig otirRepeat = measuredRig(0xED, 0xB3);
        prepareBlockIo(otirRepeat);
        otirRepeat.cpu().registers().setB(3);
        otirRepeat.runInstruction();
        assertNextFetchDuration("OTIR repeat iteration", 6, otirRepeat);
        assertEffectiveNops("OTIR final iteration", 5, new int[]{0xED, 0xB3}, rig -> {
            prepareBlockIo(rig);
            rig.cpu().registers().setB(1);
        });

        MeasuredRig inirRepeat = measuredRig(0xED, 0xB2);
        prepareBlockIo(inirRepeat);
        inirRepeat.cpu().registers().setB(3);
        inirRepeat.runInstruction();
        assertNextFetchDuration("INIR repeat iteration", 6, inirRepeat);
        assertEffectiveNops("INIR final iteration", 5, new int[]{0xED, 0xB2}, rig -> {
            prepareBlockIo(rig);
            rig.cpu().registers().setB(1);
        });

        MeasuredRig interrupt = measuredRig();
        prepareStack(interrupt);
        interrupt.cpu().registers().setInterruptMode(1);
        interrupt.cpu().registers().setIff1(true);
        interrupt.cpu().requestMaskableInterrupt();
        assertInterruptDuration("IM1 interrupt accept", 5, interrupt);

        MeasuredRig im2 = measuredRig();
        prepareStack(im2);
        im2.cpu().registers().setInterruptMode(2);
        im2.cpu().registers().setIff1(true);
        im2.cpu().registers().setI(0x40);
        im2.bus().writeMemory(0x40FF, 0x00);
        im2.bus().writeMemory(0x4100, 0x00);
        im2.cpu().requestMaskableInterrupt();
        assertInterruptDuration("IM2 interrupt accept", 7, im2);

        MeasuredRig halt = measuredRig(0x76);
        halt.runInstruction();
        assertNextFetchDuration("HALT idle cycle", 1, halt);
    }

    private static void assertEffectiveNops(
            String instruction,
            int expectedNops,
            int[] opcodes,
            MeasuredSetup setup
    ) {
        MeasuredRig rig = measuredRig(opcodes);
        setup.apply(rig);
        assertNextFetchDuration(instruction, expectedNops, rig);
    }

    private static void assertNextFetchDuration(String instruction, int expectedNops, MeasuredRig rig) {
        int instructionFetchIndex = rig.bus().fetchCount();
        rig.runInstruction();
        int followingFetchIndex = rig.bus().fetchCount();
        rig.runInstruction();

        long instructionStart = rig.bus().fetchTState(instructionFetchIndex);
        long followingInstructionStart = rig.bus().fetchTState(followingFetchIndex);
        assertEquals(expectedNops * 4L, followingInstructionStart - instructionStart, instruction);
    }

    private static void assertInterruptDuration(String instruction, int expectedNops, MeasuredRig rig) {
        long serviceStart = rig.clock().value();
        rig.runInstruction();
        int followingFetchIndex = rig.bus().fetchCount();
        rig.runInstruction();
        assertEquals(expectedNops * 4L, rig.bus().fetchTState(followingFetchIndex) - serviceStart, instruction);
    }

    private static void prepareStack(MeasuredRig rig) {
        rig.cpu().registers().setSp(0x9000);
        rig.bus().writeMemory(0x9000, 0x00);
        rig.bus().writeMemory(0x9001, 0x00);
    }

    private static void prepareReturn(MeasuredRig rig) {
        prepareStack(rig);
        rig.bus().writeMemory(0x9000, 0x01);
    }

    private static void prepareHl(MeasuredRig rig) {
        rig.cpu().registers().setHl(0x4000);
        rig.bus().writeMemory(0x4000, 0x11);
    }

    private static void prepareIx(MeasuredRig rig) {
        rig.cpu().registers().setIx(0x4000);
        rig.bus().writeMemory(0x4000, 0x11);
    }

    private static void prepareBlockTransfer(MeasuredRig rig) {
        rig.cpu().registers().setHl(0x4000);
        rig.cpu().registers().setDe(0x5000);
        rig.cpu().registers().setBc(1);
        rig.bus().writeMemory(0x4000, 0x11);
        rig.bus().writeMemory(0x4001, 0x22);
        rig.bus().writeMemory(0x4002, 0x33);
    }

    private static void prepareBlockCompare(MeasuredRig rig) {
        rig.cpu().registers().setHl(0x4000);
        rig.cpu().registers().setBc(1);
        rig.cpu().registers().setA(0x22);
        rig.bus().writeMemory(0x4000, 0x11);
    }

    private static void prepareBlockIo(MeasuredRig rig) {
        rig.cpu().registers().setHl(0x4000);
        rig.cpu().registers().setBc(0x0100);
        rig.bus().writeMemory(0x4000, 0x11);
        rig.bus().writeMemory(0x4001, 0x22);
        rig.bus().writeMemory(0x4002, 0x33);
    }

    private static MeasuredRig measuredRig(int... opcodes) {
        TStateCounter clock = new TStateCounter();
        CpcBoard board = new CpcBoard(program(opcodes), clock);
        RecordingBus bus = new RecordingBus(board.cpuBus(), clock);
        return new MeasuredRig(new Z80Cpu(bus), bus, clock);
    }

    private static int gridAlignWaits(long clockValue, int phaseTStates) {
        return (4 - (int) ((clockValue + phaseTStates) & 3)) & 3;
    }

    private static int ioAlignWaits(long clockValue, int phaseTStates) {
        return (3 - (int) ((clockValue + phaseTStates) & 3)) & 3;
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

    private record MeasuredRig(Z80Cpu cpu, RecordingBus bus, TStateCounter clock) {
        int runInstruction() {
            int tStates = cpu.runInstruction();
            clock.advance(tStates);
            return tStates;
        }
    }

    @FunctionalInterface
    private interface MeasuredSetup {
        void apply(MeasuredRig rig);
    }

    private static final class RecordingBus implements CpuBus {
        private final CpuBus delegate;
        private final TStateCounter clock;
        private final List<String> accessKinds = new ArrayList<>();
        private final List<Long> fetchTStates = new ArrayList<>();

        private RecordingBus(CpuBus delegate, TStateCounter clock) {
            this.delegate = delegate;
            this.clock = clock;
        }

        List<String> accessKinds() {
            return List.copyOf(accessKinds);
        }

        int fetchCount() {
            return fetchTStates.size();
        }

        long fetchTState(int index) {
            return fetchTStates.get(index);
        }

        @Override
        public int fetchOpcode(int address) {
            return delegate.fetchOpcode(address);
        }

        @Override
        public int fetchOpcodeWaitStates(int address, int phaseTStates) {
            int waitStates = delegate.fetchOpcodeWaitStates(address, phaseTStates);
            fetchTStates.add(clock.value() + phaseTStates + waitStates);
            return record("fetch", phaseTStates, waitStates);
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

        @Override
        public int internalCycleWaitStates(int address, int phaseTStates, int tStates, CpuBus.InternalCycleType type) {
            int waitStates = delegate.internalCycleWaitStates(address, phaseTStates, tStates, type);
            if (type == CpuBus.InternalCycleType.INTERRUPT_ACKNOWLEDGE) {
                return record("interrupt-ack", phaseTStates, waitStates);
            }
            return waitStates;
        }

        @Override
        public int acknowledgeInterrupt() {
            return delegate.acknowledgeInterrupt();
        }

        private int record(String kind, int phaseTStates, int waitStates) {
            accessKinds.add(kind);
            int expectedRemainder = kind.startsWith("port-") || kind.equals("interrupt-ack") ? 3 : 0;
            assertEquals(
                    expectedRemainder,
                    (clock.value() + phaseTStates + waitStates) & 3,
                    kind + " at clock=" + clock.value() + ", phase=" + phaseTStates
            );
            return waitStates;
        }
    }
}
