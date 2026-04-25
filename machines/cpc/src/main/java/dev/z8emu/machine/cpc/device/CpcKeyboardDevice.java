package dev.z8emu.machine.cpc.device;

import java.util.Arrays;

public final class CpcKeyboardDevice {
    public static final int LINE_COUNT = 10;
    public static final int BITS_PER_LINE = 8;

    private final boolean[][] pressed = new boolean[LINE_COUNT][BITS_PER_LINE];

    public void reset() {
        releaseAllKeys();
    }

    public void setKeyPressed(int line, int bit, boolean keyPressed) {
        validateLine(line);
        validateBit(bit);
        pressed[line][bit] = keyPressed;
    }

    public void setJoystick0Pressed(Joystick0Input input, boolean keyPressed) {
        setKeyPressed(9, input.bit(), keyPressed);
    }

    public int readLine(int line) {
        if (line < 0 || line >= LINE_COUNT) {
            return 0xFF;
        }

        int value = 0xFF;
        for (int bit = 0; bit < BITS_PER_LINE; bit++) {
            if (pressed[line][bit]) {
                value &= ~(1 << bit);
            }
        }
        return value & 0xFF;
    }

    public void releaseAllKeys() {
        for (boolean[] line : pressed) {
            Arrays.fill(line, false);
        }
    }

    private static void validateLine(int line) {
        if (line < 0 || line >= LINE_COUNT) {
            throw new IllegalArgumentException("CPC keyboard line out of range: " + line);
        }
    }

    private static void validateBit(int bit) {
        if (bit < 0 || bit >= BITS_PER_LINE) {
            throw new IllegalArgumentException("CPC keyboard bit out of range: " + bit);
        }
    }

    public enum Joystick0Input {
        UP(0),
        DOWN(1),
        LEFT(2),
        RIGHT(3),
        FIRE2(4),
        FIRE1(5);

        private final int bit;

        Joystick0Input(int bit) {
            this.bit = bit;
        }

        int bit() {
            return bit;
        }
    }
}
