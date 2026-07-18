package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.platform.device.TimedDevice;
import java.util.Arrays;

public final class KeyboardMatrixDevice implements TimedDevice {
    private final int[] rows = new int[8];

    public KeyboardMatrixDevice() {
        reset();
    }

    public synchronized void setKeyPressed(int row, int column, boolean pressed) {
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

    public synchronized int readSelectedRows(int port) {
        int highByte = (port >>> 8) & 0xFF;
        int reachableRows = (~highByte) & 0xFF;
        int reachableColumns = 0;

        // The original keyboard membrane has no isolation diodes. Treat the
        // pressed keys as a bipartite row/column graph: a selected row can pull
        // a column low, which can in turn pull another row low and expose a
        // ghost key in a different column.
        boolean changed;
        do {
            int previousRows = reachableRows;
            int previousColumns = reachableColumns;
            for (int row = 0; row < rows.length; row++) {
                int pressedColumns = (~rows[row]) & 0x1F;
                if ((reachableRows & (1 << row)) != 0) {
                    reachableColumns |= pressedColumns;
                }
                if ((pressedColumns & reachableColumns) != 0) {
                    reachableRows |= 1 << row;
                }
            }
            changed = reachableRows != previousRows || reachableColumns != previousColumns;
        } while (changed);

        return ((~reachableColumns) & 0x1F) | 0xE0;
    }

    @Override
    public synchronized void reset() {
        Arrays.fill(rows, 0x1F);
    }

    public synchronized void releaseAllKeys() {
        reset();
    }
}
