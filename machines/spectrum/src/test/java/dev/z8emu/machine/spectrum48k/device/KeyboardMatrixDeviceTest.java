package dev.z8emu.machine.spectrum48k.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyboardMatrixDeviceTest {
    @Test
    void readsDirectKeysFromOneOrSeveralSelectedRows() {
        KeyboardMatrixDevice keyboard = new KeyboardMatrixDevice();
        keyboard.setKeyPressed(0, 0, true);
        keyboard.setKeyPressed(1, 2, true);

        assertEquals(0xFE, keyboard.readSelectedRows(0xFEFE));
        assertEquals(0xFB, keyboard.readSelectedRows(0xFDFE));
        assertEquals(0xFA, keyboard.readSelectedRows(0xFCFE));
        assertEquals(0xFF, keyboard.readSelectedRows(0xFFFE));
    }

    @Test
    void threePressedKeysCreateTheUndiodedMatrixGhostFourthKey() {
        KeyboardMatrixDevice keyboard = new KeyboardMatrixDevice();
        keyboard.setKeyPressed(0, 0, true);
        keyboard.setKeyPressed(1, 0, true);
        keyboard.setKeyPressed(1, 1, true);

        assertEquals(
                0xFC,
                keyboard.readSelectedRows(0xFEFE),
                "row 0 reaches column 1 through column 0 and unselected row 1"
        );
    }

    @Test
    void disconnectedKeysDoNotCreateGhostColumns() {
        KeyboardMatrixDevice keyboard = new KeyboardMatrixDevice();
        keyboard.setKeyPressed(0, 0, true);
        keyboard.setKeyPressed(1, 1, true);
        keyboard.setKeyPressed(2, 2, true);

        assertEquals(0xFE, keyboard.readSelectedRows(0xFEFE));
    }

    @Test
    void releasingBridgeKeyRemovesGhostImmediately() {
        KeyboardMatrixDevice keyboard = new KeyboardMatrixDevice();
        keyboard.setKeyPressed(0, 0, true);
        keyboard.setKeyPressed(1, 0, true);
        keyboard.setKeyPressed(1, 1, true);
        assertEquals(0xFC, keyboard.readSelectedRows(0xFEFE));

        keyboard.setKeyPressed(1, 0, false);

        assertEquals(0xFE, keyboard.readSelectedRows(0xFEFE));
    }
}
