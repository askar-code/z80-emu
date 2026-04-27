package dev.z8emu.machine.apple2.device;

import java.util.Arrays;

public final class Apple2GamePortDevice {
    private static final int PUSH_BUTTON_COUNT = 3;
    private static final int PADDLE_COUNT = 4;
    private static final int CENTER_POSITION = 128;
    private static final int PADDLE_BASE_TSTATES = 16;
    private static final int PADDLE_TSTATES_PER_UNIT = 11;

    private final boolean[] pushButtons = new boolean[PUSH_BUTTON_COUNT];
    private final int[] paddlePositions = new int[PADDLE_COUNT];
    private long paddleTriggerTState = Long.MIN_VALUE;

    public void reset() {
        Arrays.fill(pushButtons, false);
        Arrays.fill(paddlePositions, CENTER_POSITION);
        paddleTriggerTState = Long.MIN_VALUE;
    }

    public int readPushButton(int address) {
        int index = (address & 0x03) - 1;
        if (index < 0 || index >= pushButtons.length) {
            return 0x00;
        }
        return pushButtons[index] ? 0x80 : 0x00;
    }

    public void setPushButton(int index, boolean pressed) {
        checkPushButtonIndex(index);
        pushButtons[index] = pressed;
    }

    public boolean pushButtonPressed(int index) {
        checkPushButtonIndex(index);
        return pushButtons[index];
    }

    public void triggerPaddles(long tState) {
        paddleTriggerTState = tState;
    }

    public int readPaddle(int address, long tState) {
        if (paddleTriggerTState == Long.MIN_VALUE) {
            return 0x00;
        }
        int index = address & 0x03;
        long elapsed = Math.max(0L, tState - paddleTriggerTState);
        return elapsed <= paddleDurationTStates(paddlePositions[index]) ? 0x80 : 0x00;
    }

    public void setPaddlePosition(int index, int position) {
        checkPaddleIndex(index);
        paddlePositions[index] = Math.max(0, Math.min(255, position));
    }

    public int paddlePosition(int index) {
        checkPaddleIndex(index);
        return paddlePositions[index];
    }

    private static void checkPushButtonIndex(int index) {
        if (index < 0 || index >= PUSH_BUTTON_COUNT) {
            throw new IllegalArgumentException("Apple II push button index out of range: " + index);
        }
    }

    private static void checkPaddleIndex(int index) {
        if (index < 0 || index >= PADDLE_COUNT) {
            throw new IllegalArgumentException("Apple II paddle index out of range: " + index);
        }
    }

    private static int paddleDurationTStates(int position) {
        return PADDLE_BASE_TSTATES + (position * PADDLE_TSTATES_PER_UNIT);
    }
}
