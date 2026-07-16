package dev.z8emu.machine.c64;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class C64ScreenTextTest {
    @Test
    void rendersC64ScreenCodes() {
        assertEquals('@', C64ScreenText.renderCharacter(0x00));
        assertEquals('A', C64ScreenText.renderCharacter(0x01));
        assertEquals('*', C64ScreenText.renderCharacter(0x2A));
        assertEquals('A', C64ScreenText.renderCharacter(0x80 | 0x01));
        assertEquals('.', C64ScreenText.renderCharacter(0x1B));
    }
}
