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
    void portTwoFirePullsDrivenHighAndUndrivenPortALow() {
        keyboard.setJoystickPressed(2, 4, true);
        cia.writeRegister(0x00, 0xFF);
        cia.writeRegister(0x02, 0xFF);

        assertEquals(0xEF, cia.readRegister(0x00));

        cia.writeRegister(0x02, 0x00);

        assertEquals(0xEF, cia.readRegister(0x00));
    }

    @Test
    void portOneDirectionsPullOnlyLowPortBBitsLow() {
        keyboard.setJoystickPressed(1, 0, true);
        keyboard.setJoystickPressed(1, 2, true);
        keyboard.setJoystickPressed(1, 3, true);

        assertEquals(0xF2, cia.readRegister(0x01));
    }

    @Test
    void eachJoystickLinePullsExactlyItsOwnBit() {
        cia.writeRegister(0x02, 0x00);
        for (int port = 1; port <= 2; port++) {
            int register = port == 1 ? 0x01 : 0x00;
            for (int line = 0; line <= 4; line++) {
                keyboard.setJoystickPressed(port, line, true);
                assertEquals(0xFF & ~(1 << line), cia.readRegister(register),
                        "port " + port + " line " + line);
                keyboard.setJoystickPressed(port, line, false);
                assertEquals(0xFF, cia.readRegister(register));
            }
        }
    }

    @Test
    void joystickAndPressedMatrixKeyCombineOnPortB() {
        keyboard.setJoystickPressed(1, 0, true);
        keyboard.setKeyPressed(5, 1, true);
        cia.writeRegister(0x00, 0xDF);

        assertEquals(0xFC, cia.readRegister(0x01));
    }

    @Test
    void reverseKeyboardScanStillPullsHighPortABitsWithPortTwoHeld() {
        keyboard.setJoystickPressed(2, 4, true);
        keyboard.setKeyPressed(5, 1, true);
        keyboard.setKeyPressed(6, 1, true);
        keyboard.setKeyPressed(7, 1, true);
        cia.writeRegister(0x02, 0x00);
        cia.writeRegister(0x03, 0xFF);
        cia.writeRegister(0x01, 0xFD);

        assertEquals(0x0F, cia.readRegister(0x00));
    }

    @Test
    void releaseAndResetRestoreIdleMatrixAndRestoreFlag() {
        keyboard.setKeyPressed(5, 1, true);
        keyboard.setJoystickPressed(1, 0, true);
        keyboard.setJoystickPressed(2, 4, true);
        keyboard.setRestorePressed(true);
        cia.writeRegister(0x00, 0xDF);
        assertEquals(0xFC, cia.readRegister(0x01));
        cia.writeRegister(0x00, 0xFF);
        assertEquals(0xEF, cia.readRegister(0x00));
        assertTrue(keyboard.restorePressed());

        keyboard.releaseAllKeys();
        assertEquals(0xFF, cia.readRegister(0x01));
        assertEquals(0xFF, cia.readRegister(0x00));
        assertTrue(keyboard.restorePressed());

        keyboard.setKeyPressed(5, 1, true);
        keyboard.setJoystickPressed(1, 0, true);
        keyboard.setJoystickPressed(2, 4, true);
        keyboard.reset();
        assertEquals(0xFF, cia.readRegister(0x01));
        assertEquals(0xFF, cia.readRegister(0x00));
        assertFalse(keyboard.restorePressed());
    }

    @Test
    void rejectsMatrixCoordinatesOutsideEightByEight() {
        assertThrows(IllegalArgumentException.class, () -> keyboard.setKeyPressed(-1, 0, true));
        assertThrows(IllegalArgumentException.class, () -> keyboard.setKeyPressed(8, 0, true));
        assertThrows(IllegalArgumentException.class, () -> keyboard.setKeyPressed(0, -1, true));
        assertThrows(IllegalArgumentException.class, () -> keyboard.setKeyPressed(0, 8, true));
    }

    @Test
    void rejectsJoystickCoordinatesOutsideTwoPortsAndFiveLines() {
        assertThrows(IllegalArgumentException.class, () -> keyboard.setJoystickPressed(0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> keyboard.setJoystickPressed(3, 0, true));
        assertThrows(IllegalArgumentException.class, () -> keyboard.setJoystickPressed(1, -1, true));
        assertThrows(IllegalArgumentException.class, () -> keyboard.setJoystickPressed(2, 5, true));
    }
}
