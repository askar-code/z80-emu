package dev.z8emu.machine.c64;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64CpuPortTest {
    @Test
    void resetExposesPlanFrozenPortDefaults() {
        C64CpuPort port = new C64CpuPort();

        port.reset();

        assertEquals(0x2F, port.readDirection());
        assertEquals(0x37, port.readData());
        assertTrue(port.loram());
        assertTrue(port.hiram());
        assertTrue(port.charen());
    }

    @Test
    void inputBitsFollowPullUpsRatherThanTheDataLatch() {
        C64CpuPort port = new C64CpuPort();
        port.writeData(0xFF);

        port.writeDirection(0x00);

        assertEquals(C64CpuPort.PULL_UP_BITS, port.readData());
        assertEquals(0x17, port.readData() & 0x17);
        assertEquals(0x00, port.readData() & 0xE8);

        port.writeData(0x00);
        assertEquals(C64CpuPort.PULL_UP_BITS, port.readData());
    }

    @Test
    void latchedValueSurvivesAnInputToOutputRoundTrip() {
        C64CpuPort port = new C64CpuPort();
        port.writeData(0x40);
        port.writeDirection(0x00);
        assertEquals(0x00, port.readData() & 0x40);

        port.writeDirection(0x40);
        assertEquals(0x40, port.readData() & 0x40);

        port.writeDirection(0x00);
        assertEquals(0x00, port.readData() & 0x40);
        port.writeDirection(0x40);
        assertEquals(0x40, port.readData() & 0x40);
    }

    @Test
    void bankingSignalsFollowTheEffectivePortValue() {
        C64CpuPort port = new C64CpuPort();
        port.writeData(0x00);
        port.writeDirection(0x00);

        assertTrue(port.loram());
        assertTrue(port.hiram());
        assertTrue(port.charen());

        port.writeDirection(0x07);
        port.writeData(0x05);
        assertTrue(port.loram());
        assertFalse(port.hiram());
        assertTrue(port.charen());
    }
}
