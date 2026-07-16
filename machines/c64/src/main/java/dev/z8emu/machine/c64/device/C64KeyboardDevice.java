package dev.z8emu.machine.c64.device;

import java.util.Arrays;

public final class C64KeyboardDevice implements C64CiaDevice.PortInputs {
    private final int[] pressedMaskByPaBit = new int[8];
    private int joystick1Mask = 0xFF;
    private int joystick2Mask = 0xFF;
    private boolean restorePressed;

    public C64KeyboardDevice() {
        releaseAllKeys();
    }

    public void setKeyPressed(int portABit, int portBBit, boolean pressed) {
        if (portABit < 0 || portABit >= pressedMaskByPaBit.length) {
            throw new IllegalArgumentException("portABit must be between 0 and 7");
        }
        if (portBBit < 0 || portBBit >= 8) {
            throw new IllegalArgumentException("portBBit must be between 0 and 7");
        }

        int mask = 1 << portBBit;
        if (pressed) {
            pressedMaskByPaBit[portABit] &= ~mask;
        } else {
            pressedMaskByPaBit[portABit] |= mask;
        }
    }

    public void setJoystickPressed(int port, int line, boolean pressed) {
        if (port < 1 || port > 2) {
            throw new IllegalArgumentException("port must be 1 or 2");
        }
        if (line < 0 || line > 4) {
            throw new IllegalArgumentException("line must be between 0 and 4");
        }

        int mask = 1 << line;
        if (port == 1) {
            joystick1Mask = pressed ? joystick1Mask & ~mask : joystick1Mask | mask;
        } else {
            joystick2Mask = pressed ? joystick2Mask & ~mask : joystick2Mask | mask;
        }
    }

    public void releaseAllKeys() {
        Arrays.fill(pressedMaskByPaBit, 0xFF);
        joystick1Mask = 0xFF;
        joystick2Mask = 0xFF;
    }

    public void reset() {
        releaseAllKeys();
        restorePressed = false;
    }

    @Override
    public int portA(int drivenPortA, int drivenPortB) {
        int inputs = 0xFF;
        for (int portABit = 0; portABit < pressedMaskByPaBit.length; portABit++) {
            for (int portBBit = 0; portBBit < 8; portBBit++) {
                if ((drivenPortB & (1 << portBBit)) == 0
                        && (pressedMaskByPaBit[portABit] & (1 << portBBit)) == 0) {
                    inputs &= ~(1 << portABit);
                    break;
                }
            }
        }
        return inputs & joystick2Mask;
    }

    @Override
    public int portB(int drivenPortA, int drivenPortB) {
        int inputs = 0xFF;
        for (int portABit = 0; portABit < pressedMaskByPaBit.length; portABit++) {
            if ((drivenPortA & (1 << portABit)) == 0) {
                inputs &= pressedMaskByPaBit[portABit];
            }
        }
        // Matrix ghosting beyond direct switch conduction is not modeled.
        return inputs & joystick1Mask;
    }

    public void setRestorePressed(boolean restorePressed) {
        this.restorePressed = restorePressed;
    }

    public boolean restorePressed() {
        return restorePressed;
    }
}
