package dev.z8emu.app.desktop;

import dev.z8emu.machine.radio86rk.device.Radio86KeyboardDevice;

final class Radio86KeyLatch {
    private final Radio86KeyboardDevice keyboard;
    private final int minimumReleaseFrames;
    private final boolean[][] pressedState = new boolean[Radio86KeyboardDevice.ROW_COUNT][Radio86KeyboardDevice.COLUMN_COUNT];
    private final int[][] releaseCountdown = new int[Radio86KeyboardDevice.ROW_COUNT][Radio86KeyboardDevice.COLUMN_COUNT];

    Radio86KeyLatch(Radio86KeyboardDevice keyboard, int minimumReleaseFrames) {
        this.keyboard = keyboard;
        this.minimumReleaseFrames = Math.max(0, minimumReleaseFrames);
    }

    void press(int row, int column) {
        pressedState[row][column] = true;
        releaseCountdown[row][column] = -1;
        keyboard.setKeyPressed(row, column, true);
    }

    void release(int row, int column) {
        if (!pressedState[row][column]) {
            return;
        }
        if (minimumReleaseFrames == 0) {
            pressedState[row][column] = false;
            releaseCountdown[row][column] = 0;
            keyboard.setKeyPressed(row, column, false);
            return;
        }
        releaseCountdown[row][column] = minimumReleaseFrames;
    }

    void tick() {
        for (int row = 0; row < releaseCountdown.length; row++) {
            for (int column = 0; column < releaseCountdown[row].length; column++) {
                int countdown = releaseCountdown[row][column];
                if (countdown <= 0) {
                    continue;
                }
                countdown--;
                releaseCountdown[row][column] = countdown;
                if (countdown == 0) {
                    pressedState[row][column] = false;
                    keyboard.setKeyPressed(row, column, false);
                }
            }
        }
    }

    void releaseAll() {
        for (int row = 0; row < pressedState.length; row++) {
            for (int column = 0; column < pressedState[row].length; column++) {
                pressedState[row][column] = false;
                releaseCountdown[row][column] = 0;
            }
        }
        keyboard.releaseAllKeys();
    }
}
