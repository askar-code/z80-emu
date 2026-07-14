package dev.z8emu.platform.machine;

import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.cpu.Cpu;
import dev.z8emu.platform.time.TStateCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineRuntimeTest {
    private TestCpu cpu;
    private TestBoard board;
    private TStateCounter clock;
    private MachineRuntime runtime;

    @BeforeEach
    void setUp() {
        cpu = new TestCpu();
        board = new TestBoard();
        clock = new TStateCounter();
        runtime = new MachineRuntime(cpu, board, clock);
        runtime.reset();
    }

    @Test
    void risingNmiEdgeRequestsOneNonMaskableInterrupt() {
        board.nmiLineActive = true;

        runtime.runInstruction();

        assertEquals(1, cpu.nonMaskableInterruptRequests);
    }

    @Test
    void heldNmiLineDoesNotRetrigger() {
        board.nmiLineActive = true;

        for (int instruction = 0; instruction < 10; instruction++) {
            runtime.runInstruction();
        }

        assertEquals(1, cpu.nonMaskableInterruptRequests);
    }

    @Test
    void droppingAndRaisingNmiLineRequestsAnotherInterrupt() {
        board.nmiLineActive = true;
        runtime.runInstruction();
        board.nmiLineActive = false;
        runtime.runInstruction();
        board.nmiLineActive = true;

        runtime.runInstruction();

        assertEquals(2, cpu.nonMaskableInterruptRequests);
    }

    @Test
    void resetClearsNmiEdgeStateWhileLineRemainsActive() {
        board.nmiLineActive = true;
        runtime.runInstruction();

        runtime.reset();
        runtime.runInstruction();

        assertEquals(1, cpu.nonMaskableInterruptRequests);
    }

    @Test
    void maskableInterruptLineIsPolledAfterEveryInstruction() {
        board.maskableLineActive = true;
        runtime.runInstruction();
        runtime.runInstruction();
        board.maskableLineActive = false;

        runtime.runInstruction();

        assertEquals(2, cpu.maskableInterruptRequests);
        assertEquals(1, cpu.maskableInterruptClears);
    }

    @Test
    void runInstructionReturnsCpuTStatesAndAdvancesTheClock() {
        int tStates = runtime.runInstruction();

        assertEquals(4, tStates);
        assertEquals(4, runtime.currentTState());
        assertEquals(4, clock.value());
        assertEquals(4, board.lastElapsedTStates);
        assertEquals(4, board.lastCurrentTState);
    }

    private static final class TestCpu implements Cpu {
        private int maskableInterruptRequests;
        private int maskableInterruptClears;
        private int nonMaskableInterruptRequests;

        @Override
        public void reset() {
            maskableInterruptRequests = 0;
            maskableInterruptClears = 0;
            nonMaskableInterruptRequests = 0;
        }

        @Override
        public void requestMaskableInterrupt() {
            maskableInterruptRequests++;
        }

        @Override
        public void clearMaskableInterrupt() {
            maskableInterruptClears++;
        }

        @Override
        public void requestNonMaskableInterrupt() {
            nonMaskableInterruptRequests++;
        }

        @Override
        public int runInstruction() {
            return 4;
        }
    }

    private static final class TestBoard implements MachineBoard {
        private final CpuBus bus = new TestBus();
        private boolean maskableLineActive;
        private boolean nmiLineActive;
        private int lastElapsedTStates;
        private long lastCurrentTState;

        @Override
        public CpuBus cpuBus() {
            return bus;
        }

        @Override
        public void reset() {
            lastElapsedTStates = 0;
            lastCurrentTState = 0;
        }

        @Override
        public void onTStatesElapsed(int tStates, long currentTState) {
            lastElapsedTStates = tStates;
            lastCurrentTState = currentTState;
        }

        @Override
        public boolean maskableInterruptLineActive(long currentTState) {
            return maskableLineActive;
        }

        @Override
        public boolean nonMaskableInterruptLineActive(long currentTState) {
            return nmiLineActive;
        }
    }

    private static final class TestBus implements CpuBus {
        @Override
        public int readMemory(int address) {
            return 0;
        }

        @Override
        public void writeMemory(int address, int value) {
        }
    }
}
