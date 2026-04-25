package dev.z8emu.app.desktop;

import dev.z8emu.machine.radio86rk.device.Radio86KeyboardDevice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Radio86KeyLatchTest {
    @Test
    void releaseIsDeferredForConfiguredNumberOfFrames() {
        Radio86KeyboardDevice keyboard = new Radio86KeyboardDevice();
        keyboard.writeRegister(0, 0xFD);
        Radio86KeyLatch latch = new Radio86KeyLatch(keyboard, 3);

        latch.press(1, 4);
        latch.release(1, 4);

        assertEquals(0xEF, keyboard.readRegister(1));
        latch.tick();
        assertEquals(0xEF, keyboard.readRegister(1));
        latch.tick();
        assertEquals(0xEF, keyboard.readRegister(1));
        latch.tick();
        assertEquals(0xFF, keyboard.readRegister(1));
    }

    @Test
    void pressCancelsPendingDeferredRelease() {
        Radio86KeyboardDevice keyboard = new Radio86KeyboardDevice();
        keyboard.writeRegister(0, 0xFD);
        Radio86KeyLatch latch = new Radio86KeyLatch(keyboard, 3);

        latch.press(1, 4);
        latch.release(1, 4);
        latch.tick();
        latch.press(1, 4);
        latch.tick();
        latch.tick();
        latch.tick();

        assertEquals(0xEF, keyboard.readRegister(1));
    }
}
