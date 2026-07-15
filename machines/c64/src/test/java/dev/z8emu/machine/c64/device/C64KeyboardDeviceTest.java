package dev.z8emu.machine.c64.device;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64KeyboardDeviceTest {
    private C64CiaDevice cia;
    private C64KeyboardDevice keyboard;

    @BeforeEach
    void setUp() {
        cia = new C64CiaDevice();
        keyboard = new C64KeyboardDevice();
        cia.setPortInputs(keyboard);
        cia.reset();
        cia.writeRegister(0x02, 0xFF);
        cia.writeRegister(0x03, 0x00);
    }

    @Test
    void selectedPaRowExposesPressedKeyOnPortB() {
        keyboard.setKeyPressed(5, 1, true);
        cia.writeRegister(0x00, 0xDF);

        assertEquals(0xFD, cia.readRegister(0x01));
    }

    @Test
    void allPaRowsHighReadIdleEvenWithKeysPressed() {
        keyboard.setKeyPressed(5, 1, true);
        cia.writeRegister(0x00, 0xFF);

        assertEquals(0xFF, cia.readRegister(0x01));
    }

    @Test
    void twoKeysInOnePaRowPullBothPortBBitsLow() {
        keyboard.setKeyPressed(5, 1, true);
        keyboard.setKeyPressed(5, 2, true);
        cia.writeRegister(0x00, 0xDF);

        assertEquals(0xF9, cia.readRegister(0x01));
    }

    @Test
    void reverseScanPullsPortABitLowFromDrivenPortB() {
        cia.writeRegister(0x02, 0x00);
        cia.writeRegister(0x03, 0xFF);
        cia.writeRegister(0x01, 0xFD);
        keyboard.setKeyPressed(5, 1, true);

        assertEquals(0xDF, cia.readRegister(0x00));
    }

    @Test
    void reverseScanCanPullAnOutputDrivenHighPortABitLow() {
        cia.writeRegister(0x00, 0xFF);
        cia.writeRegister(0x02, 0xFF);
        cia.writeRegister(0x01, 0xFD);
        cia.writeRegister(0x03, 0xFF);
        keyboard.setKeyPressed(5, 1, true);

        assertEquals(0xDF, cia.readRegister(0x00));
    }

    @Test
    void releaseAndResetRestoreIdleMatrixAndRestoreFlag() {
        keyboard.setKeyPressed(5, 1, true);
        keyboard.setRestorePressed(true);
        cia.writeRegister(0x00, 0xDF);
        assertEquals(0xFD, cia.readRegister(0x01));
        assertTrue(keyboard.restorePressed());

        keyboard.releaseAllKeys();
        assertEquals(0xFF, cia.readRegister(0x01));
        assertTrue(keyboard.restorePressed());

        keyboard.setKeyPressed(5, 1, true);
        keyboard.reset();
        assertEquals(0xFF, cia.readRegister(0x01));
        assertFalse(keyboard.restorePressed());
    }

    @Test
    void rejectsMatrixCoordinatesOutsideEightByEight() {
        assertThrows(IllegalArgumentException.class, () -> keyboard.setKeyPressed(-1, 0, true));
        assertThrows(IllegalArgumentException.class, () -> keyboard.setKeyPressed(8, 0, true));
        assertThrows(IllegalArgumentException.class, () -> keyboard.setKeyPressed(0, -1, true));
        assertThrows(IllegalArgumentException.class, () -> keyboard.setKeyPressed(0, 8, true));
    }
}
