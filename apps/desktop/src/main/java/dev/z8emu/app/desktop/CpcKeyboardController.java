package dev.z8emu.app.desktop;

import dev.z8emu.machine.cpc.device.CpcKeyboardDevice;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

final class CpcKeyboardController extends AbstractMappedHostKeyboardController<CpcKeyboardController.MatrixKey> {
    private final CpcKeyboardDevice keyboard;

    private CpcKeyboardController(Window window, CpcKeyboardDevice keyboard) {
        super(window);
        this.keyboard = keyboard;
    }

    static CpcKeyboardController bind(Window window, JComponent component, CpcKeyboardDevice keyboard) {
        CpcKeyboardController controller = new CpcKeyboardController(window, keyboard);
        controller.bindToComponent(component);
        return controller;
    }

    void releaseAllKeys() {
        keyboard.releaseAllKeys();
        markFocusLost();
    }

    @Override
    protected List<MatrixKey> keysFor(int keyCode) {
        List<MatrixKey> keys = new ArrayList<>(1);
        switch (keyCode) {
            case KeyEvent.VK_UP -> keys.add(key(0, 0));
            case KeyEvent.VK_RIGHT -> keys.add(key(0, 1));
            case KeyEvent.VK_DOWN -> keys.add(key(0, 2));
            case KeyEvent.VK_LEFT -> keys.add(key(1, 0));
            case KeyEvent.VK_ENTER -> keys.add(key(2, 2));
            case KeyEvent.VK_SHIFT -> keys.add(key(2, 5));
            case KeyEvent.VK_CONTROL -> keys.add(key(2, 7));
            case KeyEvent.VK_ESCAPE -> keys.add(key(8, 2));
            case KeyEvent.VK_TAB -> keys.add(key(8, 4));
            case KeyEvent.VK_CAPS_LOCK -> keys.add(key(8, 6));
            case KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE -> keys.add(key(9, 7));
            case KeyEvent.VK_SPACE -> keys.add(key(5, 7));

            case KeyEvent.VK_0 -> keys.add(key(4, 0));
            case KeyEvent.VK_1 -> keys.add(key(8, 0));
            case KeyEvent.VK_2 -> keys.add(key(8, 1));
            case KeyEvent.VK_3 -> keys.add(key(7, 1));
            case KeyEvent.VK_4 -> keys.add(key(7, 0));
            case KeyEvent.VK_5 -> keys.add(key(6, 1));
            case KeyEvent.VK_6 -> keys.add(key(6, 0));
            case KeyEvent.VK_7 -> keys.add(key(5, 1));
            case KeyEvent.VK_8 -> keys.add(key(5, 0));
            case KeyEvent.VK_9 -> keys.add(key(4, 1));

            case KeyEvent.VK_A -> keys.add(key(8, 5));
            case KeyEvent.VK_B -> keys.add(key(6, 6));
            case KeyEvent.VK_C -> keys.add(key(7, 6));
            case KeyEvent.VK_D -> keys.add(key(7, 5));
            case KeyEvent.VK_E -> keys.add(key(7, 2));
            case KeyEvent.VK_F -> keys.add(key(6, 5));
            case KeyEvent.VK_G -> keys.add(key(6, 4));
            case KeyEvent.VK_H -> keys.add(key(5, 4));
            case KeyEvent.VK_I -> keys.add(key(4, 3));
            case KeyEvent.VK_J -> keys.add(key(5, 5));
            case KeyEvent.VK_K -> keys.add(key(4, 5));
            case KeyEvent.VK_L -> keys.add(key(4, 4));
            case KeyEvent.VK_M -> keys.add(key(4, 6));
            case KeyEvent.VK_N -> keys.add(key(5, 6));
            case KeyEvent.VK_O -> keys.add(key(4, 2));
            case KeyEvent.VK_P -> keys.add(key(3, 3));
            case KeyEvent.VK_Q -> keys.add(key(8, 3));
            case KeyEvent.VK_R -> keys.add(key(6, 2));
            case KeyEvent.VK_S -> keys.add(key(7, 4));
            case KeyEvent.VK_T -> keys.add(key(6, 3));
            case KeyEvent.VK_U -> keys.add(key(5, 2));
            case KeyEvent.VK_V -> keys.add(key(6, 7));
            case KeyEvent.VK_W -> keys.add(key(7, 3));
            case KeyEvent.VK_X -> keys.add(key(7, 7));
            case KeyEvent.VK_Y -> keys.add(key(5, 3));
            case KeyEvent.VK_Z -> keys.add(key(8, 7));

            case KeyEvent.VK_COMMA -> keys.add(key(3, 7));
            case KeyEvent.VK_PERIOD -> keys.add(key(4, 7));
            case KeyEvent.VK_SLASH -> keys.add(key(3, 6));
            case KeyEvent.VK_SEMICOLON -> keys.add(key(3, 4));
            case KeyEvent.VK_QUOTE -> keys.add(key(5, 1));
            case KeyEvent.VK_MINUS -> keys.add(key(3, 1));
            case KeyEvent.VK_OPEN_BRACKET -> keys.add(key(2, 1));
            case KeyEvent.VK_CLOSE_BRACKET -> keys.add(key(2, 3));
            case KeyEvent.VK_BACK_SLASH, KeyEvent.VK_BACK_QUOTE -> keys.add(key(2, 6));

            case KeyEvent.VK_NUMPAD8 -> keys.add(key(9, 0));
            case KeyEvent.VK_NUMPAD2 -> keys.add(key(9, 1));
            case KeyEvent.VK_NUMPAD4 -> keys.add(key(9, 2));
            case KeyEvent.VK_NUMPAD6 -> keys.add(key(9, 3));
            case KeyEvent.VK_NUMPAD5, KeyEvent.VK_NUMPAD0 -> keys.add(key(9, 5));
            case KeyEvent.VK_DECIMAL -> keys.add(key(9, 4));
            default -> {
            }
        }
        return keys;
    }

    @Override
    protected void updateKey(MatrixKey key, boolean pressed) {
        keyboard.setKeyPressed(key.line(), key.bit(), pressed);
    }

    private MatrixKey key(int line, int bit) {
        return new MatrixKey(line, bit);
    }

    record MatrixKey(int line, int bit) {
    }
}
