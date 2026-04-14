package dev.z8emu.cpu.i8080;

import dev.z8emu.platform.bus.CpuBus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class I8080CpuTest {
    @Test
    void movRegisterToRegisterUses8080Timing() {
        TestBus bus = new TestBus(0x41);
        I8080Cpu cpu = new I8080Cpu(bus);
        cpu.registers().setC(0x5A);

        int tStates = cpu.runInstruction();

        assertEquals(5, tStates);
        assertEquals(0x5A, cpu.registers().b());
    }

    @Test
    void inxAndDadUse8080Timing() {
        TestBus bus = new TestBus(0x03, 0x09);
        I8080Cpu cpu = new I8080Cpu(bus);
        cpu.registers().setBc(0x0001);
        cpu.registers().setHl(0x1000);

        assertEquals(5, cpu.runInstruction());
        assertEquals(0x0002, cpu.registers().bc());

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x1002, cpu.registers().hl());
    }

    @Test
    void outImmediateUsesEightBitPortAddressingAnd8080Timing() {
        TestBus bus = new TestBus(0xD3, 0x34);
        I8080Cpu cpu = new I8080Cpu(bus);
        cpu.registers().setA(0x12);

        int tStates = cpu.runInstruction();

        assertEquals(10, tStates);
        assertEquals(0x34, bus.lastPort());
        assertEquals(0x12, bus.lastPortValue());
    }

    @Test
    void illegal8080OpcodeStopsExecution() {
        TestBus bus = new TestBus(0xDD);
        I8080Cpu cpu = new I8080Cpu(bus);

        IllegalStateException failure = assertThrows(IllegalStateException.class, cpu::runInstruction);

        assertEquals("Illegal Intel 8080 opcode 0xDD at 0x0000", failure.getMessage());
        assertEquals(0, cpu.registers().pc());
    }

    @Test
    void undocumentedSingleByteGapOpcodesBehaveAsNoOp() {
        TestBus bus = new TestBus(0x20, 0x30, 0x00);
        I8080Cpu cpu = new I8080Cpu(bus);

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x0001, cpu.registers().pc());

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x0002, cpu.registers().pc());
    }

    @Test
    void inteOutputTracksEiAndDi() {
        TestBus bus = new TestBus(0xFB, 0x00, 0xF3);
        I8080Cpu cpu = new I8080Cpu(bus);
        List<Boolean> transitions = new ArrayList<>();
        cpu.setInteOutputListener(transitions::add);

        assertEquals(List.of(false), transitions);

        cpu.runInstruction();
        assertEquals(List.of(false), transitions, "EI should arm INTE after the following instruction");

        cpu.runInstruction();
        assertEquals(List.of(false, true), transitions);

        cpu.runInstruction();
        assertEquals(List.of(false, true, false), transitions);
    }

    private static final class TestBus implements CpuBus {
        private final byte[] memory = new byte[0x10000];
        private int lastPort = -1;
        private int lastPortValue = -1;

        private TestBus(int... program) {
            for (int i = 0; i < program.length; i++) {
                memory[i] = (byte) program[i];
            }
        }

        int lastPort() {
            return lastPort;
        }

        int lastPortValue() {
            return lastPortValue;
        }

        @Override
        public int fetchOpcode(int address) {
            return readMemory(address);
        }

        @Override
        public int readMemory(int address) {
            return Byte.toUnsignedInt(memory[address & 0xFFFF]);
        }

        @Override
        public void writeMemory(int address, int value) {
            memory[address & 0xFFFF] = (byte) value;
        }

        @Override
        public int readPort(int port) {
            return 0xFF;
        }

        @Override
        public void writePort(int port, int value) {
            lastPort = port & 0xFFFF;
            lastPortValue = value & 0xFF;
        }

        @Override
        public int acknowledgeInterrupt() {
            return 0xFF;
        }

        @Override
        public void onRefresh(int irValue) {
        }

        @Override
        public int currentTState() {
            return 0;
        }
    }
}
