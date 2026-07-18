package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import java.awt.event.KeyEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpectrumJoystickProfileTest {
    @Test
    void missingOrUnknownSettingKeepsSafeCursorDefault() {
        assertEquals(SpectrumJoystickProfile.CURSOR, SpectrumJoystickProfile.fromSetting(null));
        assertEquals(SpectrumJoystickProfile.CURSOR, SpectrumJoystickProfile.fromSetting(""));
        assertEquals(SpectrumJoystickProfile.CURSOR, SpectrumJoystickProfile.fromSetting("unknown"));
        assertEquals(SpectrumJoystickProfile.KEMPSTON, SpectrumJoystickProfile.fromSetting("Kempston"));
        assertEquals(SpectrumJoystickProfile.SINCLAIR_1, SpectrumJoystickProfile.fromSetting("sinclair-1"));
        assertEquals(SpectrumJoystickProfile.SINCLAIR_2, SpectrumJoystickProfile.fromSetting("sinclair_2"));
    }

    @Test
    void cursorProfilePreservesExistingArrowChords() {
        assertEquals(
                List.of(key(0, 0), key(3, 4)),
                SpectrumJoystickProfile.CURSOR.matrixKeys(SpectrumJoystickProfile.JoystickAction.LEFT)
        );
        assertEquals(
                List.of(key(0, 0), key(4, 2)),
                SpectrumJoystickProfile.CURSOR.matrixKeys(SpectrumJoystickProfile.JoystickAction.RIGHT)
        );

        assertEquals(0xEE, readChord(
                SpectrumJoystickProfile.CURSOR,
                SpectrumJoystickProfile.JoystickAction.LEFT,
                0xF6FE
        ));
    }

    @Test
    void sinclairProfilesProduceInterfaceTwoMatrixKeys() {
        assertEquals(
                List.of(key(4, 4), key(4, 3), key(4, 1), key(4, 2), key(4, 0)),
                List.of(
                        onlyKey(SpectrumJoystickProfile.SINCLAIR_1, SpectrumJoystickProfile.JoystickAction.LEFT),
                        onlyKey(SpectrumJoystickProfile.SINCLAIR_1, SpectrumJoystickProfile.JoystickAction.RIGHT),
                        onlyKey(SpectrumJoystickProfile.SINCLAIR_1, SpectrumJoystickProfile.JoystickAction.UP),
                        onlyKey(SpectrumJoystickProfile.SINCLAIR_1, SpectrumJoystickProfile.JoystickAction.DOWN),
                        onlyKey(SpectrumJoystickProfile.SINCLAIR_1, SpectrumJoystickProfile.JoystickAction.FIRE)
                )
        );
        assertEquals(
                List.of(key(3, 0), key(3, 1), key(3, 3), key(3, 2), key(3, 4)),
                List.of(
                        onlyKey(SpectrumJoystickProfile.SINCLAIR_2, SpectrumJoystickProfile.JoystickAction.LEFT),
                        onlyKey(SpectrumJoystickProfile.SINCLAIR_2, SpectrumJoystickProfile.JoystickAction.RIGHT),
                        onlyKey(SpectrumJoystickProfile.SINCLAIR_2, SpectrumJoystickProfile.JoystickAction.UP),
                        onlyKey(SpectrumJoystickProfile.SINCLAIR_2, SpectrumJoystickProfile.JoystickAction.DOWN),
                        onlyKey(SpectrumJoystickProfile.SINCLAIR_2, SpectrumJoystickProfile.JoystickAction.FIRE)
                )
        );

        assertEquals(0xFD, readChord(
                SpectrumJoystickProfile.SINCLAIR_1,
                SpectrumJoystickProfile.JoystickAction.UP,
                0xEFFE
        ));
        assertEquals(0xEF, readChord(
                SpectrumJoystickProfile.SINCLAIR_2,
                SpectrumJoystickProfile.JoystickAction.FIRE,
                0xF7FE
        ));
    }

    @Test
    void hostArrowsAndFireAreProfileAware() {
        assertEquals(
                SpectrumJoystickProfile.JoystickAction.UP,
                SpectrumJoystickProfile.KEMPSTON.actionForHostKey(KeyEvent.VK_UP)
        );
        assertEquals(
                SpectrumJoystickProfile.JoystickAction.FIRE,
                SpectrumJoystickProfile.KEMPSTON.actionForHostKey(KeyEvent.VK_CONTROL)
        );
        assertEquals(
                SpectrumJoystickProfile.JoystickAction.FIRE,
                SpectrumJoystickProfile.CURSOR.actionForHostKey(KeyEvent.VK_NUMPAD0)
        );
        assertNull(SpectrumJoystickProfile.CURSOR.actionForHostKey(KeyEvent.VK_CONTROL));
    }

    @Test
    void profileCycleIsStableAndWraps() {
        assertEquals(SpectrumJoystickProfile.KEMPSTON, SpectrumJoystickProfile.CURSOR.next());
        assertEquals(SpectrumJoystickProfile.SINCLAIR_1, SpectrumJoystickProfile.KEMPSTON.next());
        assertEquals(SpectrumJoystickProfile.SINCLAIR_2, SpectrumJoystickProfile.SINCLAIR_1.next());
        assertEquals(SpectrumJoystickProfile.CURSOR, SpectrumJoystickProfile.SINCLAIR_2.next());
    }

    private static int readChord(
            SpectrumJoystickProfile profile,
            SpectrumJoystickProfile.JoystickAction action,
            int port
    ) {
        KeyboardMatrixDevice keyboard = new KeyboardMatrixDevice();
        for (SpectrumJoystickProfile.MatrixKey key : profile.matrixKeys(action)) {
            keyboard.setKeyPressed(key.row(), key.column(), true);
        }
        return keyboard.readSelectedRows(port);
    }

    private static SpectrumJoystickProfile.MatrixKey onlyKey(
            SpectrumJoystickProfile profile,
            SpectrumJoystickProfile.JoystickAction action
    ) {
        return profile.matrixKeys(action).getFirst();
    }

    private static SpectrumJoystickProfile.MatrixKey key(int row, int column) {
        return new SpectrumJoystickProfile.MatrixKey(row, column);
    }
}
