package dev.z8emu.platform.bus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CpuBusTest {
    @Test
    void defaultOpcodeFetchUsesMemoryRead() {
        CpuBus bus = new MemoryOnlyBus();

        assertEquals(0x42, bus.fetchOpcode(0x1234));
    }

    @Test
    void defaultIoAndInterruptHooksAreIdle() {
        CpuBus bus = new MemoryOnlyBus();

        assertEquals(0xFF, bus.readPort(0x00FE));
        assertDoesNotThrow(() -> bus.writePort(0x00FE, 0x12));
        assertEquals(0xFF, bus.acknowledgeInterrupt());
        assertDoesNotThrow(() -> bus.onRefresh(0x1234));
        assertEquals(0, bus.currentTState());
    }

    private static final class MemoryOnlyBus implements CpuBus {
        @Override
        public int readMemory(int address) {
            return 0x42;
        }

        @Override
        public void writeMemory(int address, int value) {
        }
    }
}
