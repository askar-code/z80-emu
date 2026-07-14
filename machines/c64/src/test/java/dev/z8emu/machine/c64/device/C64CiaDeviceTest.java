package dev.z8emu.machine.c64.device;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64CiaDeviceTest {
    private static final int TOD_TENTH_T_STATES = 98_525;

    private C64CiaDevice cia;

    @BeforeEach
    void setUp() {
        cia = new C64CiaDevice();
        cia.reset();
    }

    @Test
    void continuousTimerUnderflowsEveryLatchPlusOneCycles() {
        writeTimerA(3);
        cia.writeRegister(0x0E, 0x01);

        for (int tick = 0; tick < 3; tick++) {
            cia.onTStatesElapsed(1);
        }
        assertEquals(0, readTimerA());
        assertEquals(0, cia.readRegister(0x0D));

        cia.onTStatesElapsed(1);

        assertEquals(3, readTimerA());
        assertEquals(0x01, cia.readRegister(0x0D));

        for (int tick = 0; tick < 4; tick++) {
            cia.onTStatesElapsed(1);
        }
        assertEquals(3, readTimerA());
        assertEquals(0x01, cia.readRegister(0x0D));
    }

    @Test
    void oneShotUnderflowReloadsAndStopsTheTimer() {
        cia.writeRegister(0x0E, 0x08);
        writeTimerA(1);

        cia.onTStatesElapsed(2);

        assertEquals(0, cia.readRegister(0x0E) & 0x01);
        assertEquals(1, readTimerA());
        assertEquals(0x01, cia.readRegister(0x0D));

        cia.onTStatesElapsed(10);

        assertEquals(1, readTimerA());
        assertEquals(0, cia.readRegister(0x0D));
    }

    @Test
    void forceLoadStrobeLoadsCounterAndDoesNotReadBack() {
        writeTimerA(3);
        cia.writeRegister(0x0E, 0x01);
        cia.onTStatesElapsed(2);
        assertEquals(1, readTimerA());

        cia.writeRegister(0x0E, 0x11);

        assertEquals(3, readTimerA());
        assertEquals(0x01, cia.readRegister(0x0E));
    }

    @Test
    void timerAHighWriteUsesStoppedRunningAndOneShotLoadRules() {
        cia.writeRegister(0x04, 0x34);
        cia.writeRegister(0x05, 0x12);
        assertEquals(0x1234, readTimerA());

        cia.writeRegister(0x0E, 0x01);
        cia.onTStatesElapsed(1);
        cia.writeRegister(0x05, 0x56);
        assertEquals(0x1233, readTimerA());
        cia.writeRegister(0x0E, 0x11);
        assertEquals(0x5634, readTimerA());

        cia.reset();
        cia.writeRegister(0x0E, 0x08);
        cia.writeRegister(0x04, 0x78);
        cia.writeRegister(0x05, 0x56);

        assertEquals(0x5678, readTimerA());
        assertEquals(0x01, cia.readRegister(0x0E) & 0x01);
    }

    @Test
    void timerBCascadesFromTimerAUnderflowsInBothCascadeModes() {
        assertTimerBCascadeMode(0x40);
        cia.reset();
        assertTimerBCascadeMode(0x60);
    }

    @Test
    void interruptControlReadClearsFlagsAndRaisedLine() {
        cia.writeRegister(0x0D, 0x81);
        writeTimerA(0);
        cia.writeRegister(0x0E, 0x01);

        cia.onTStatesElapsed(1);

        assertTrue(cia.interruptLineActive());
        assertEquals(0x81, cia.readRegister(0x0D));
        assertFalse(cia.interruptLineActive());
        assertEquals(0, cia.readRegister(0x0D));

        cia.reset();
        writeTimerA(0);
        cia.writeRegister(0x0E, 0x01);
        cia.onTStatesElapsed(1);

        assertFalse(cia.interruptLineActive());
        assertEquals(0x01, cia.readRegister(0x0D));
    }

    @Test
    void maskingInALatchedFlagRaisesTheInterruptWithoutAnotherUnderflow() {
        writeTimerA(0);
        cia.writeRegister(0x0E, 0x01);
        cia.onTStatesElapsed(1);
        assertFalse(cia.interruptLineActive());

        cia.writeRegister(0x0D, 0x81);

        assertTrue(cia.interruptLineActive());
        assertEquals(0x81, cia.readRegister(0x0D));
    }

    @Test
    void raisedInterruptIsSetOnlyUntilInterruptControlRead() {
        cia.writeRegister(0x0D, 0x81);
        writeTimerA(0);
        cia.writeRegister(0x0E, 0x01);
        cia.onTStatesElapsed(1);
        assertTrue(cia.interruptLineActive());

        cia.writeRegister(0x0D, 0x01);

        assertTrue(cia.interruptLineActive());
        assertEquals(0x81, cia.readRegister(0x0D));
        assertFalse(cia.interruptLineActive());
    }

    @Test
    void interruptMaskWritesSetOrClearOnlyListedBits() {
        cia.writeRegister(0x0D, 0x83);
        cia.writeRegister(0x0D, 0x01);
        triggerTimerAUnderflow();
        assertFalse(cia.interruptLineActive());
        assertEquals(0x01, cia.readRegister(0x0D));

        cia.writeRegister(0x0E, 0x00);
        triggerTimerBUnderflow();
        assertTrue(cia.interruptLineActive());
        assertEquals(0x82, cia.readRegister(0x0D));

        cia.writeRegister(0x0D, 0x81);
        cia.writeRegister(0x0D, 0x02);
        cia.writeRegister(0x0F, 0x00);
        triggerTimerAUnderflow();
        assertTrue(cia.interruptLineActive());
        assertEquals(0x81, cia.readRegister(0x0D));

        cia.writeRegister(0x0E, 0x00);
        triggerTimerBUnderflow();
        assertFalse(cia.interruptLineActive());
        assertEquals(0x02, cia.readRegister(0x0D));
    }

    @Test
    void portsMixOutputLatchWithPulledUpInputsAndReadBackDdr() {
        cia.writeRegister(0x00, 0x5A);
        cia.writeRegister(0x02, 0xF0);

        assertEquals(0x5F, cia.readRegister(0x00));
        assertEquals(0xF0, cia.readRegister(0x02));
        assertEquals(0xF0, cia.readRegister(0x12));
    }

    @Test
    void todStartsHaltedAndAcceptsMultiCycleTickChunksAfterResume() {
        cia.onTStatesElapsed(TOD_TENTH_T_STATES);
        assertEquals(0, cia.readRegister(0x08));

        cia.writeRegister(0x08, 0);
        cia.onTStatesElapsed(TOD_TENTH_T_STATES);

        assertEquals(1, cia.readRegister(0x08));
    }

    @Test
    void todRollsFromElevenToTwelveAndTogglesPm() {
        cia.writeRegister(0x0B, 0x11);
        cia.writeRegister(0x0A, 0x59);
        cia.writeRegister(0x09, 0x59);
        cia.writeRegister(0x08, 0x09);

        cia.onTStatesElapsed(TOD_TENTH_T_STATES);

        assertEquals(0x92, cia.readRegister(0x0B));
        assertEquals(0, cia.readRegister(0x0A));
        assertEquals(0, cia.readRegister(0x09));
        assertEquals(0, cia.readRegister(0x08));
    }

    @Test
    void todHourTwelveWriteFlipsAmPmBeforeStoring() {
        cia.writeRegister(0x0B, 0x12);

        assertEquals(0x92, cia.readRegister(0x0B));
    }

    @Test
    void todHourReadLatchesUntilTenthsReadReleasesSnapshot() {
        cia.writeRegister(0x0B, 0x01);
        cia.writeRegister(0x0A, 0x02);
        cia.writeRegister(0x09, 0x03);
        cia.writeRegister(0x08, 0x04);

        assertEquals(0x01, cia.readRegister(0x0B));
        cia.onTStatesElapsed(TOD_TENTH_T_STATES);

        assertEquals(0x01, cia.readRegister(0x0B));
        assertEquals(0x02, cia.readRegister(0x0A));
        assertEquals(0x03, cia.readRegister(0x09));
        assertEquals(0x04, cia.readRegister(0x08));
        assertEquals(0x05, cia.readRegister(0x08));
    }

    @Test
    void resetRestoresDatasheetStateAndHaltedTod() {
        cia.writeRegister(0x00, 0xFF);
        cia.writeRegister(0x02, 0xFF);
        cia.writeRegister(0x0E, 0x01);
        cia.writeRegister(0x08, 0x09);

        cia.reset();

        assertEquals(0xFF, cia.readRegister(0x00));
        assertEquals(0xFF, cia.readRegister(0x01));
        assertEquals(0, cia.readRegister(0x02));
        assertEquals(0, cia.readRegister(0x03));
        assertEquals(0xFFFF, readTimerA());
        assertEquals(0xFFFF, readTimerB());
        assertEquals(0, cia.readRegister(0x0C));
        assertEquals(0, cia.readRegister(0x0D));
        assertEquals(0, cia.readRegister(0x0E));
        assertEquals(0, cia.readRegister(0x0F));
        assertFalse(cia.interruptLineActive());
        assertEquals(0x01, cia.readRegister(0x0B));
        assertEquals(0, cia.readRegister(0x0A));
        assertEquals(0, cia.readRegister(0x09));
        assertEquals(0, cia.readRegister(0x08));

        cia.onTStatesElapsed(TOD_TENTH_T_STATES);

        assertEquals(0, cia.readRegister(0x08));
    }

    private void assertTimerBCascadeMode(int inputMode) {
        writeTimerA(1);
        writeTimerB(2);
        cia.writeRegister(0x0E, 0x01);
        cia.writeRegister(0x0F, inputMode | 0x01);

        cia.onTStatesElapsed(1);
        assertEquals(2, readTimerB());
        cia.onTStatesElapsed(1);
        assertEquals(1, readTimerB());
        cia.onTStatesElapsed(2);
        assertEquals(0, readTimerB());
        cia.onTStatesElapsed(2);

        assertEquals(2, readTimerB());
        assertEquals(0x02, cia.readRegister(0x0D) & 0x02);
    }

    private void triggerTimerAUnderflow() {
        writeTimerA(0);
        cia.writeRegister(0x0E, 0x01);
        cia.onTStatesElapsed(1);
    }

    private void triggerTimerBUnderflow() {
        writeTimerB(0);
        cia.writeRegister(0x0F, 0x01);
        cia.onTStatesElapsed(1);
    }

    private void writeTimerA(int value) {
        cia.writeRegister(0x04, value);
        cia.writeRegister(0x05, value >>> 8);
    }

    private int readTimerA() {
        return cia.readRegister(0x04) | (cia.readRegister(0x05) << 8);
    }

    private void writeTimerB(int value) {
        cia.writeRegister(0x06, value);
        cia.writeRegister(0x07, value >>> 8);
    }

    private int readTimerB() {
        return cia.readRegister(0x06) | (cia.readRegister(0x07) << 8);
    }
}
