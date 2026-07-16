package dev.z8emu.machine.cpc;

import dev.z8emu.machine.cpc.device.CpcKeyMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CpcKeyMapTest {
    private static final int[][] LETTER_KEYS = {
            {8, 5}, {6, 6}, {7, 6}, {7, 5}, {7, 2}, {6, 5}, {6, 4}, {5, 4}, {4, 3},
            {5, 5}, {4, 5}, {4, 4}, {4, 6}, {5, 6}, {4, 2}, {3, 3}, {8, 3}, {6, 2},
            {7, 4}, {6, 3}, {5, 2}, {6, 7}, {7, 3}, {7, 7}, {5, 3}, {8, 7}
    };
    private static final int[][] DIGIT_KEYS = {
            {4, 0}, {8, 0}, {8, 1}, {7, 1}, {7, 0}, {6, 1}, {6, 0}, {5, 1}, {5, 0}, {4, 1}
    };

    @Test
    void mapsLettersAndDigitsToTheFirmwareMatrix() {
        for (int index = 0; index < LETTER_KEYS.length; index++) {
            char upper = (char) ('A' + index);
            assertChord(upper, LETTER_KEYS[index]);
            assertChord(Character.toLowerCase(upper), LETTER_KEYS[index]);
        }
        for (int index = 0; index < DIGIT_KEYS.length; index++) {
            assertChord((char) ('0' + index), DIGIT_KEYS[index]);
        }
    }

    @Test
    void mapsReturnSpaceAndEverySupportedSymbol() {
        assertChord('\r', key(2, 2));
        assertChord('\n', key(2, 2));
        assertChord(' ', key(5, 7));
        assertChord(',', key(4, 7));
        assertChord('.', key(3, 7));
        assertChord(':', key(3, 5));
        assertChord(';', key(3, 4));
        assertChord('-', key(3, 1));
        assertChord('"', key(2, 5), key(8, 1));
        assertChord('&', key(2, 5), key(6, 0));
        assertChord('(', key(2, 5), key(5, 0));
        assertChord(')', key(2, 5), key(4, 1));
    }

    @Test
    void rejectsUnsupportedCharacters() {
        assertNull(CpcKeyMap.forCharacterOrNull('@'));
        assertThrows(IllegalArgumentException.class, () -> CpcKeyMap.forCharacter('@'));
    }

    private static void assertChord(char character, int[]... expectedKeys) {
        CpcKeyMap.MatrixKey[] expected = new CpcKeyMap.MatrixKey[expectedKeys.length];
        for (int index = 0; index < expectedKeys.length; index++) {
            expected[index] = new CpcKeyMap.MatrixKey(expectedKeys[index][0], expectedKeys[index][1]);
        }
        assertArrayEquals(expected, CpcKeyMap.forCharacter(character).keys());
    }

    private static int[] key(int line, int bit) {
        return new int[]{line, bit};
    }
}
