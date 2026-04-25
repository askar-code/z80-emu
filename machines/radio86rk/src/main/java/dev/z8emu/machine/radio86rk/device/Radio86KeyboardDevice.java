package dev.z8emu.machine.radio86rk.device;

public final class Radio86KeyboardDevice {
    public static final int ROW_COUNT = 9;
    public static final int COLUMN_COUNT = 8;

    private final boolean[][] pressedKeys = new boolean[ROW_COUNT][COLUMN_COUNT];
    private int keyboardMask;
    private int portCOutput;
    private boolean tapeInputLow;

    public void reset() {
        releaseAllKeys();
        keyboardMask = 0;
        portCOutput = 0;
        tapeInputLow = false;
    }

    public void setKeyPressed(int row, int column, boolean pressed) {
        validatePosition(row, column);
        pressedKeys[row][column] = pressed;
    }

    public void releaseAllKeys() {
        for (boolean[] row : pressedKeys) {
            java.util.Arrays.fill(row, false);
        }
    }

    public int readRegister(int registerIndex) {
        return switch (registerIndex & 0x03) {
            case 1 -> readSelectedColumns();
            case 2 -> readModifiersAndTape();
            case 3 -> 0xFF;
            default -> 0xFF;
        };
    }

    public void writeRegister(int registerIndex, int value) {
        int normalizedValue = value & 0xFF;
        switch (registerIndex & 0x03) {
            case 0 -> keyboardMask = (~normalizedValue) & 0xFF;
            case 2 -> portCOutput = normalizedValue;
            case 3 -> applyBitSetReset(normalizedValue);
            default -> {
            }
        }
    }

    public int keyboardMask() {
        return keyboardMask;
    }

    public int portCOutput() {
        return portCOutput;
    }

    public void setTapeInputLow(boolean tapeInputLow) {
        this.tapeInputLow = tapeInputLow;
    }

    private int readSelectedColumns() {
        int value = 0xFF;
        for (int row = 0; row < 8; row++) {
            if ((keyboardMask & (1 << row)) != 0) {
                value &= rowValue(row);
            }
        }
        return value;
    }

    private int readModifiersAndTape() {
        int value = rowValue(8);
        if (tapeInputLow) {
            value ^= 0x10;
        }
        return value;
    }

    private int rowValue(int row) {
        int value = 0xFF;
        for (int column = 0; column < COLUMN_COUNT; column++) {
            if (pressedKeys[row][column]) {
                value &= ~(1 << column);
            }
        }
        return value;
    }

    private void applyBitSetReset(int controlWord) {
        if ((controlWord & 0x80) != 0) {
            return;
        }

        int bitIndex = (controlWord >>> 1) & 0x07;
        if ((controlWord & 0x01) != 0) {
            portCOutput |= 1 << bitIndex;
        } else {
            portCOutput &= ~(1 << bitIndex);
        }
    }

    private void validatePosition(int row, int column) {
        if (row < 0 || row >= ROW_COUNT) {
            throw new IllegalArgumentException("row out of range: " + row);
        }
        if (column < 0 || column >= COLUMN_COUNT) {
            throw new IllegalArgumentException("column out of range: " + column);
        }
    }
}
