package dev.z8emu.machine.c64.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64KeyMapTest {
    @Test
    void mapsRepresentativeLettersDigitsPunctuationAndReturn() {
        assertKey(C64KeyMap.forCharacter('P'), 5, 1);
        assertKey(C64KeyMap.forCharacter('2'), 7, 3);
        assertKey(C64KeyMap.forCharacter('+'), 5, 0);
        assertKey(C64KeyMap.forCharacter('\r'), 0, 1);
    }

    @Test
    void mapsDoubleQuoteToLeftShiftAndTwo() {
        C64KeyMap.MatrixKey[] keys = C64KeyMap.forCharacter('"').keys();

        assertEquals(2, keys.length);
        assertEquals(new C64KeyMap.MatrixKey(1, 7), keys[0]);
        assertEquals(new C64KeyMap.MatrixKey(7, 3), keys[1]);
    }

    @Test
    void rejectsUnsupportedCharacters() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> C64KeyMap.forCharacter('[')
        );

        assertTrue(failure.getMessage().contains("["));
    }

    private static void assertKey(C64KeyMap.KeyChord chord, int portABit, int portBBit) {
        assertArrayEquals(
                new C64KeyMap.MatrixKey[]{new C64KeyMap.MatrixKey(portABit, portBBit)},
                chord.keys()
        );
    }
}
