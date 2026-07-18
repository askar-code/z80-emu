package dev.z8emu.cpu.z80;

import dev.z8emu.platform.bus.CpuBus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Z80InternalCycleTest {
    @Test
    void indexedMemoryFamiliesExposeTheirDistinctFuseSequences() {
        SequenceBus incBus = new SequenceBus();
        incBus.load(0x0000, 0xDD, 0x34, 0x02);
        incBus.load(0x4002, 0x7F);
        Z80Cpu incCpu = new Z80Cpu(incBus);
        incCpu.registers().setIx(0x4000);

        assertEquals(23, incCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                m1(0x0001, 4),
                read(0x0002, 8),
                internal(0x0002, 11, 5, CpuBus.InternalCycleType.READ_NO_MREQ),
                read(0x4002, 16),
                internal(0x4002, 19, 1, CpuBus.InternalCycleType.READ_NO_MREQ),
                write(0x4002, 20)
        ), incBus.events());

        SequenceBus immediateBus = new SequenceBus();
        immediateBus.load(0x0000, 0xDD, 0x36, 0x02, 0x55);
        Z80Cpu immediateCpu = new Z80Cpu(immediateBus);
        immediateCpu.registers().setIx(0x4000);

        assertEquals(19, immediateCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                m1(0x0001, 4),
                read(0x0002, 8),
                read(0x0003, 11),
                internal(0x0003, 14, 2, CpuBus.InternalCycleType.READ_NO_MREQ),
                write(0x4002, 16)
        ), immediateBus.events());

        SequenceBus ddcbBus = new SequenceBus();
        ddcbBus.load(0x0000, 0xDD, 0xCB, 0x02, 0x46);
        ddcbBus.load(0x4002, 0x01);
        Z80Cpu ddcbCpu = new Z80Cpu(ddcbBus);
        ddcbCpu.registers().setIx(0x4000);

        assertEquals(20, ddcbCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                m1(0x0001, 4),
                read(0x0002, 8),
                read(0x0003, 11),
                internal(0x0003, 14, 2, CpuBus.InternalCycleType.READ_NO_MREQ),
                read(0x4002, 16),
                internal(0x4002, 19, 1, CpuBus.InternalCycleType.READ_NO_MREQ)
        ), ddcbBus.events());
    }

    @Test
    void memoryCbAndExchangeStackExposeReadWritePolarityAndOrder() {
        SequenceBus cbBus = new SequenceBus();
        cbBus.load(0x0000, 0xCB, 0x06);
        cbBus.load(0x4000, 0x81);
        Z80Cpu cbCpu = new Z80Cpu(cbBus);
        cbCpu.registers().setHl(0x4000);

        assertEquals(15, cbCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                m1(0x0001, 4),
                read(0x4000, 8),
                internal(0x4000, 11, 1, CpuBus.InternalCycleType.READ_NO_MREQ),
                write(0x4000, 12)
        ), cbBus.events());

        SequenceBus exchangeBus = new SequenceBus();
        exchangeBus.load(0x0000, 0xE3);
        exchangeBus.load(0x8000, 0x78, 0x56);
        Z80Cpu exchangeCpu = new Z80Cpu(exchangeBus);
        exchangeCpu.registers().setSp(0x8000);
        exchangeCpu.registers().setHl(0x1234);

        assertEquals(19, exchangeCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                read(0x8000, 4),
                read(0x8001, 7),
                internal(0x8001, 10, 1, CpuBus.InternalCycleType.READ_NO_MREQ),
                write(0x8001, 11),
                write(0x8000, 14),
                internal(0x8000, 17, 2, CpuBus.InternalCycleType.WRITE_NO_MREQ)
        ), exchangeBus.events());
    }

    @Test
    void controlAndSixteenBitInstructionsExposeInternalPhases() {
        SequenceBus addBus = new SequenceBus();
        addBus.load(0x0000, 0x09);
        Z80Cpu addCpu = new Z80Cpu(addBus);
        addCpu.registers().setBc(1);

        assertEquals(11, addCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                internal(0x0001, 4, 7, CpuBus.InternalCycleType.READ_NO_MREQ)
        ), addBus.events());

        SequenceBus callBus = new SequenceBus();
        callBus.load(0x0000, 0xCD, 0x34, 0x12);
        Z80Cpu callCpu = new Z80Cpu(callBus);
        callCpu.registers().setSp(0x9000);

        assertEquals(17, callCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                read(0x0001, 4),
                read(0x0002, 7),
                internal(0x0002, 10, 1, CpuBus.InternalCycleType.READ_NO_MREQ),
                write(0x8FFF, 11),
                write(0x8FFE, 14)
        ), callBus.events());

        SequenceBus djnzBus = new SequenceBus();
        djnzBus.load(0x0000, 0x10, 0x02);
        Z80Cpu djnzCpu = new Z80Cpu(djnzBus);
        djnzCpu.registers().setB(2);

        assertEquals(13, djnzCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                internal(0x0001, 4, 1, CpuBus.InternalCycleType.READ_NO_MREQ),
                read(0x0001, 5),
                internal(0x0001, 8, 5, CpuBus.InternalCycleType.READ_NO_MREQ)
        ), djnzBus.events());
    }

    @Test
    void registerPairIrTransferRrdAndConditionalRetCoverRemainingInternalShapes() {
        SequenceBus pairBus = new SequenceBus();
        pairBus.load(0x0000, 0x03); // INC BC
        Z80Cpu pairCpu = new Z80Cpu(pairBus);

        assertEquals(6, pairCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                internal(0x0001, 4, 2, CpuBus.InternalCycleType.READ_NO_MREQ)
        ), pairBus.events());

        SequenceBus irBus = new SequenceBus();
        irBus.load(0x0000, 0xED, 0x47); // LD I,A
        Z80Cpu irCpu = new Z80Cpu(irBus);

        assertEquals(9, irCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                m1(0x0001, 4),
                internal(0x0002, 8, 1, CpuBus.InternalCycleType.READ_NO_MREQ)
        ), irBus.events());

        SequenceBus rrdBus = new SequenceBus();
        rrdBus.load(0x0000, 0xED, 0x67);
        rrdBus.load(0x4000, 0x12);
        Z80Cpu rrdCpu = new Z80Cpu(rrdBus);
        rrdCpu.registers().setHl(0x4000);

        assertEquals(18, rrdCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                m1(0x0001, 4),
                read(0x4000, 8),
                internal(0x4000, 11, 4, CpuBus.InternalCycleType.READ_NO_MREQ),
                write(0x4000, 15)
        ), rrdBus.events());

        SequenceBus retBus = new SequenceBus();
        retBus.load(0x0000, 0xC0); // RET NZ, not taken
        Z80Cpu retCpu = new Z80Cpu(retBus);
        retCpu.registers().setF(Z80Registers.FLAG_Z);

        assertEquals(5, retCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                internal(0x0001, 4, 1, CpuBus.InternalCycleType.READ_NO_MREQ)
        ), retBus.events());
    }

    @Test
    void blockInstructionsKeepFuseOldAndNewAddresses() {
        SequenceBus transferBus = new SequenceBus();
        transferBus.load(0x0000, 0xED, 0xB0);
        transferBus.load(0x4000, 0x55);
        Z80Cpu transferCpu = new Z80Cpu(transferBus);
        transferCpu.registers().setHl(0x4000);
        transferCpu.registers().setDe(0x5000);
        transferCpu.registers().setBc(2);

        assertEquals(21, transferCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                m1(0x0001, 4),
                read(0x4000, 8),
                write(0x5000, 11),
                internal(0x5000, 14, 2, CpuBus.InternalCycleType.WRITE_NO_MREQ),
                internal(0x5000, 16, 5, CpuBus.InternalCycleType.WRITE_NO_MREQ)
        ), transferBus.events());

        SequenceBus compareBus = new SequenceBus();
        compareBus.load(0x0000, 0xED, 0xB1);
        compareBus.load(0x4000, 0x11);
        Z80Cpu compareCpu = new Z80Cpu(compareBus);
        compareCpu.registers().setA(0x22);
        compareCpu.registers().setHl(0x4000);
        compareCpu.registers().setBc(2);

        assertEquals(21, compareCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                m1(0x0001, 4),
                read(0x4000, 8),
                internal(0x4000, 11, 5, CpuBus.InternalCycleType.READ_NO_MREQ),
                internal(0x4000, 16, 5, CpuBus.InternalCycleType.READ_NO_MREQ)
        ), compareBus.events());

        SequenceBus inputBus = new SequenceBus();
        inputBus.load(0x0000, 0xED, 0xB2);
        inputBus.setPortValue(0x0201, 0x66);
        Z80Cpu inputCpu = new Z80Cpu(inputBus);
        inputCpu.registers().setBc(0x0201);
        inputCpu.registers().setHl(0x4000);

        assertEquals(21, inputCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                m1(0x0001, 4),
                internal(0x0002, 8, 1, CpuBus.InternalCycleType.READ_NO_MREQ),
                portRead(0x0201, 9),
                write(0x4000, 13),
                internal(0x4000, 16, 5, CpuBus.InternalCycleType.WRITE_NO_MREQ)
        ), inputBus.events());

        SequenceBus outputBus = new SequenceBus();
        outputBus.load(0x0000, 0xED, 0xB3);
        outputBus.load(0x4000, 0x77);
        Z80Cpu outputCpu = new Z80Cpu(outputBus);
        outputCpu.registers().setBc(0x0201);
        outputCpu.registers().setHl(0x4000);

        assertEquals(21, outputCpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                m1(0x0001, 4),
                internal(0x0002, 8, 1, CpuBus.InternalCycleType.READ_NO_MREQ),
                read(0x4000, 9),
                portWrite(0x0101, 12),
                internal(0x0101, 16, 5, CpuBus.InternalCycleType.READ_NO_MREQ)
        ), outputBus.events());
    }

    @Test
    void interruptAcknowledgeAndHaltCyclesRemainObservableWithoutNoMreqContention() {
        SequenceBus haltBus = new SequenceBus();
        haltBus.load(0x0000, 0x76, 0x00);
        Z80Cpu haltCpu = new Z80Cpu(haltBus);

        assertEquals(4, haltCpu.runInstruction());
        haltBus.clearEvents();
        assertEquals(4, haltCpu.runInstruction());
        assertEquals(List.of(m1(0x0000, 0)), haltBus.events());

        SequenceBus nmiBus = new SequenceBus();
        Z80Cpu nmiCpu = new Z80Cpu(nmiBus);
        nmiCpu.registers().setI(0x40);
        nmiCpu.registers().setPc(0x1234);
        nmiCpu.registers().setSp(0x9000);
        nmiCpu.requestNonMaskableInterrupt();

        assertEquals(11, nmiCpu.runInstruction());
        assertEquals(List.of(
                internal(CpuBus.NO_ADDRESS, 0, 5, CpuBus.InternalCycleType.NON_MASKABLE_INTERRUPT_ACKNOWLEDGE),
                write(0x8FFF, 5),
                write(0x8FFE, 8)
        ), nmiBus.events());

        SequenceBus intBus = new SequenceBus();
        intBus.setInterruptVector(0xFE);
        intBus.load(0x40FE, 0x78, 0x56);
        Z80Cpu intCpu = new Z80Cpu(intBus);
        intCpu.registers().setI(0x40);
        intCpu.registers().setPc(0x1234);
        intCpu.registers().setSp(0x9000);
        intCpu.registers().setInterruptMode(2);
        intCpu.registers().setIff1(true);
        intCpu.requestMaskableInterrupt();

        assertEquals(19, intCpu.runInstruction());
        assertEquals(List.of(
                internal(CpuBus.NO_ADDRESS, 0, 7, CpuBus.InternalCycleType.INTERRUPT_ACKNOWLEDGE),
                write(0x8FFF, 7),
                write(0x8FFE, 10),
                read(0x40FE, 13),
                read(0x40FF, 16)
        ), intBus.events());
    }

    @Test
    void internalWaitStatesShiftEveryFollowingBusPhase() {
        SequenceBus bus = new SequenceBus();
        bus.load(0x0000, 0xCD, 0x34, 0x12);
        bus.setInternalWaitStates(2);
        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setSp(0x9000);

        assertEquals(19, cpu.runInstruction());
        assertEquals(List.of(
                m1(0x0000, 0),
                read(0x0001, 4),
                read(0x0002, 7),
                internal(0x0002, 10, 1, CpuBus.InternalCycleType.READ_NO_MREQ),
                write(0x8FFF, 13),
                write(0x8FFE, 16)
        ), bus.events());
    }

    private static BusEvent m1(int address, int phase) {
        return new BusEvent("M1", address, phase, 4);
    }

    private static BusEvent read(int address, int phase) {
        return new BusEvent("READ", address, phase, 3);
    }

    private static BusEvent write(int address, int phase) {
        return new BusEvent("WRITE", address, phase, 3);
    }

    private static BusEvent portRead(int address, int phase) {
        return new BusEvent("PORT_READ", address, phase, 4);
    }

    private static BusEvent portWrite(int address, int phase) {
        return new BusEvent("PORT_WRITE", address, phase, 4);
    }

    private static BusEvent internal(
            int address,
            int phase,
            int tStates,
            CpuBus.InternalCycleType type
    ) {
        return new BusEvent(type.name(), address, phase, tStates);
    }

    private record BusEvent(String type, int address, int phase, int tStates) {
    }

    private static final class SequenceBus implements CpuBus {
        private final byte[] memory = new byte[0x10000];
        private final int[] portValues = new int[0x10000];
        private final List<BusEvent> events = new ArrayList<>();
        private int interruptVector = 0xFF;
        private int internalWaitStates;

        void load(int address, int... values) {
            for (int index = 0; index < values.length; index++) {
                memory[(address + index) & 0xFFFF] = (byte) values[index];
            }
        }

        void setPortValue(int port, int value) {
            portValues[port & 0xFFFF] = value & 0xFF;
        }

        void setInterruptVector(int interruptVector) {
            this.interruptVector = interruptVector & 0xFF;
        }

        void setInternalWaitStates(int internalWaitStates) {
            this.internalWaitStates = internalWaitStates;
        }

        List<BusEvent> events() {
            return List.copyOf(events);
        }

        void clearEvents() {
            events.clear();
        }

        @Override
        public int fetchOpcodeWaitStates(int address, int phaseTStates) {
            events.add(m1(address & 0xFFFF, phaseTStates));
            return 0;
        }

        @Override
        public int readMemory(int address) {
            return Byte.toUnsignedInt(memory[address & 0xFFFF]);
        }

        @Override
        public int readMemoryWaitStates(int address, int phaseTStates) {
            events.add(read(address & 0xFFFF, phaseTStates));
            return 0;
        }

        @Override
        public void writeMemory(int address, int value) {
            memory[address & 0xFFFF] = (byte) value;
        }

        @Override
        public int writeMemoryWaitStates(int address, int value, int phaseTStates) {
            events.add(write(address & 0xFFFF, phaseTStates));
            return 0;
        }

        @Override
        public int internalCycleWaitStates(
                int address,
                int phaseTStates,
                int tStates,
                InternalCycleType type
        ) {
            int observedAddress = address == CpuBus.NO_ADDRESS ? CpuBus.NO_ADDRESS : address & 0xFFFF;
            events.add(internal(observedAddress, phaseTStates, tStates, type));
            return internalWaitStates;
        }

        @Override
        public int readPort(int port) {
            return portValues[port & 0xFFFF];
        }

        @Override
        public int readPortWaitStates(int port, int phaseTStates) {
            events.add(portRead(port & 0xFFFF, phaseTStates));
            return 0;
        }

        @Override
        public void writePort(int port, int value) {
        }

        @Override
        public int writePortWaitStates(int port, int value, int phaseTStates) {
            events.add(portWrite(port & 0xFFFF, phaseTStates));
            return 0;
        }

        @Override
        public int acknowledgeInterrupt() {
            return interruptVector;
        }
    }
}
