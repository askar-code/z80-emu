package dev.z8emu.app.desktop;

import dev.z8emu.machine.c64.device.C64KeyboardDevice;
import dev.z8emu.machine.c64.device.C64KeyMap;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

final class C64KeyboardController extends AbstractMappedHostKeyboardController<C64KeyMap.MatrixKey> {
    private final C64KeyboardDevice keyboard;
    private final Runnable keyActivityListener;

    private C64KeyboardController(Window window, C64KeyboardDevice keyboard, Runnable keyActivityListener) {
        super(window);
        this.keyboard = keyboard;
        this.keyActivityListener = keyActivityListener;
    }

    static C64KeyboardController bind(
            Window window,
            JComponent component,
            C64KeyboardDevice keyboard,
            Runnable keyActivityListener
    ) {
        C64KeyboardController controller = new C64KeyboardController(window, keyboard, keyActivityListener);
        controller.bindToComponent(component);
        return controller;
    }

    @Override
    protected boolean handleHostAction(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.VK_PAGE_UP) {
            return false;
        }
        if (event.getID() == KeyEvent.KEY_PRESSED) {
            keyboard.setRestorePressed(true);
            notifyKeyActivity();
        } else if (event.getID() == KeyEvent.KEY_RELEASED) {
            keyboard.setRestorePressed(false);
        }
        setLastEvent("restore");
        return true;
    }

    @Override
    void releaseAllKeys() {
        keyboard.releaseAllKeys();
        keyboard.setRestorePressed(false);
        markFocusLost();
    }

    @Override
    protected List<C64KeyMap.MatrixKey> keysFor(int keyCode) {
        List<C64KeyMap.MatrixKey> keys = new ArrayList<>(2);
        switch (keyCode) {
            case KeyEvent.VK_SHIFT -> keys.add(new C64KeyMap.MatrixKey(1, 7));
            case KeyEvent.VK_BACK_SPACE -> keys.add(new C64KeyMap.MatrixKey(0, 0));
            case KeyEvent.VK_HOME -> keys.add(new C64KeyMap.MatrixKey(6, 3));
            case KeyEvent.VK_ESCAPE -> keys.add(new C64KeyMap.MatrixKey(7, 7));
            case KeyEvent.VK_RIGHT -> keys.add(new C64KeyMap.MatrixKey(0, 2));
            case KeyEvent.VK_DOWN -> keys.add(new C64KeyMap.MatrixKey(0, 7));
            case KeyEvent.VK_LEFT -> {
                keys.add(new C64KeyMap.MatrixKey(1, 7));
                keys.add(new C64KeyMap.MatrixKey(0, 2));
            }
            case KeyEvent.VK_UP -> {
                keys.add(new C64KeyMap.MatrixKey(1, 7));
                keys.add(new C64KeyMap.MatrixKey(0, 7));
            }
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE,
                    KeyEvent.VK_COMMA, KeyEvent.VK_PERIOD, KeyEvent.VK_SLASH,
                    KeyEvent.VK_SEMICOLON, KeyEvent.VK_EQUALS, KeyEvent.VK_MINUS ->
                    addCharacterKeys(keys, keyCode);
            default -> {
                if ((keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
                        || (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z)) {
                    addCharacterKeys(keys, keyCode);
                }
            }
        }
        return keys;
    }

    private static void addCharacterKeys(List<C64KeyMap.MatrixKey> keys, int keyCode) {
        C64KeyMap.KeyChord chord = C64KeyMap.forCharacterOrNull((char) keyCode);
        if (chord != null) {
            for (C64KeyMap.MatrixKey key : chord.keys()) {
                keys.add(key);
            }
        }
    }

    @Override
    protected void updateKey(C64KeyMap.MatrixKey key, boolean pressed) {
        keyboard.setKeyPressed(key.portABit(), key.portBBit(), pressed);
        if (pressed) {
            notifyKeyActivity();
        }
    }

    private void notifyKeyActivity() {
        if (keyActivityListener != null) {
            keyActivityListener.run();
        }
    }

}
