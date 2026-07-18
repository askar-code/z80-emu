package dev.z8emu.machine.spectrum48k.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KempstonJoystickDeviceTest {
    @Test
    void reportsOriginalActiveHighBitLayout() {
        KempstonJoystickDevice joystick = new KempstonJoystickDevice();

        joystick.setControlPressed(KempstonJoystickDevice.RIGHT, true);
        joystick.setControlPressed(KempstonJoystickDevice.LEFT, true);
        joystick.setControlPressed(KempstonJoystickDevice.DOWN, true);
        joystick.setControlPressed(KempstonJoystickDevice.UP, true);
        joystick.setControlPressed(KempstonJoystickDevice.FIRE, true);

        assertEquals(0x1F, joystick.readPort());

        joystick.setControlPressed(KempstonJoystickDevice.LEFT, false);
        joystick.setControlPressed(KempstonJoystickDevice.FIRE, false);

        assertEquals(0x0D, joystick.readPort());
    }

    @Test
    void strictDecoderMirrorsLowByteZeroThroughOneF() {
        assertTrue(KempstonJoystickDevice.portSelector().matches(0x001F));
        assertTrue(KempstonJoystickDevice.portSelector().matches(0x0000));
        assertTrue(KempstonJoystickDevice.portSelector().matches(0xA51D));
        assertFalse(KempstonJoystickDevice.portSelector().matches(0x0020));
        assertFalse(KempstonJoystickDevice.portSelector().matches(0x00FE));
    }

    @Test
    void attachedSelectorTracksInterfacePresence() {
        KempstonJoystickDevice joystick = new KempstonJoystickDevice();

        assertFalse(joystick.enabledPortSelector().matches(0x001F));

        joystick.setEnabled(true);
        assertTrue(joystick.enabledPortSelector().matches(0x001F));

        joystick.setControlPressed(KempstonJoystickDevice.FIRE, true);
        joystick.setEnabled(false);
        assertEquals(0, joystick.readPort());
        assertFalse(joystick.enabledPortSelector().matches(0x001F));
    }
}
