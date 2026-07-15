package dev.z8emu.machine.c64.device;

import java.util.Arrays;

public final class C64KeyboardDevice implements C64CiaDevice.PortInputs {
    private final int[] pressedMaskByPaBit = new int[8];
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

    public void releaseAllKeys() {
        Arrays.fill(pressedMaskByPaBit, 0xFF);
    }

    public void reset() {
        releaseAllKeys();
        restorePressed = false;
    }

    @Override
    public int portA(int drivenPortA, int drivenPortB) {
        // Control-port 2 joystick overlay on PA0-PA4 is deferred.
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
        return inputs;
    }

    @Override
    public int portB(int drivenPortA, int drivenPortB) {
        // Control-port 1 joystick overlay on PB0-PB4 is deferred.
        int inputs = 0xFF;
        for (int portABit = 0; portABit < pressedMaskByPaBit.length; portABit++) {
            if ((drivenPortA & (1 << portABit)) == 0) {
                inputs &= pressedMaskByPaBit[portABit];
            }
        }
        // Matrix ghosting beyond direct switch conduction is not modeled.
        return inputs;
    }

    public void setRestorePressed(boolean restorePressed) {
        this.restorePressed = restorePressed;
    }

    public boolean restorePressed() {
        return restorePressed;
    }
}
