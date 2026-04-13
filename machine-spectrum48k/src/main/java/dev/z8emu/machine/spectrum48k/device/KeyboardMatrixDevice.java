package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.platform.device.TimedDevice;
import java.util.Arrays;

public final class KeyboardMatrixDevice implements TimedDevice {
    private final int[] rows = new int[8];

    public KeyboardMatrixDevice() {
        reset();
    }

    public void setRowState(int row, int activeLowBits) {
        if (row < 0 || row >= rows.length) {
            throw new IllegalArgumentException("row must be between 0 and 7");
        }

        rows[row] = activeLowBits & 0x1F;
    }

    public void setKeyPressed(int row, int column, boolean pressed) {
        if (row < 0 || row >= rows.length) {
            throw new IllegalArgumentException("row must be between 0 and 7");
        }
        if (column < 0 || column >= 5) {
            throw new IllegalArgumentException("column must be between 0 and 4");
        }

        int mask = 1 << column;
        if (pressed) {
            rows[row] &= ~mask;
        } else {
            rows[row] |= mask;
        }
    }

    public int readSelectedRows(int port) {
        int result = 0x1F;
        int highByte = (port >>> 8) & 0xFF;

        for (int row = 0; row < rows.length; row++) {
            if ((highByte & (1 << row)) == 0) {
                result &= rows[row];
            }
        }

        return result | 0xE0;
    }

    @Override
    public void reset() {
        Arrays.fill(rows, 0x1F);
    }

    public void releaseAllKeys() {
        reset();
    }
}
