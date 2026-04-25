package dev.z8emu.platform.bus;

import dev.z8emu.platform.time.TStateCounter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClockedCpuBusTest {
    @Test
    void currentTStateComesFromClockAndSaturatesToIntRange() {
        TStateCounter clock = new TStateCounter();
        TestBus bus = new TestBus(clock);

        clock.advance(123);
        assertEquals(123, bus.currentTState());

        clock.advance(Integer.MAX_VALUE);
        clock.advance(100);
        assertEquals(Integer.MAX_VALUE, bus.currentTState());
    }

    private static final class TestBus extends ClockedCpuBus {
        private TestBus(TStateCounter clock) {
            super(clock);
        }

        @Override
        public int readMemory(int address) {
            return 0;
        }

        @Override
        public void writeMemory(int address, int value) {
        }
    }
}
