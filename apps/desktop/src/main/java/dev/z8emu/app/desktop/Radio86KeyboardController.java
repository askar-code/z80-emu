package dev.z8emu.app.desktop;

import dev.z8emu.machine.radio86rk.device.Radio86KeyboardDevice;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

final class Radio86KeyboardController extends AbstractMappedHostKeyboardController<Radio86KeyboardController.MatrixKey> {
    private static final int MINIMUM_RELEASE_FRAMES = Integer.getInteger("z8emu.radioKeyReleaseFrames", 3);

    private final HostActions hostActions;
    private final Radio86KeyLatch keyLatch;

    private Radio86KeyboardController(Window window, Radio86KeyboardDevice keyboard, HostActions hostActions) {
        super(window);
        this.hostActions = hostActions;
        this.keyLatch = new Radio86KeyLatch(keyboard, MINIMUM_RELEASE_FRAMES);
    }

    static Radio86KeyboardController bind(Window window, JComponent component, Radio86KeyboardDevice keyboard, HostActions hostActions) {
        Radio86KeyboardController controller = new Radio86KeyboardController(window, keyboard, hostActions);
        controller.bindToComponent(component);
        return controller;
    }

    @Override
    protected boolean handleHostAction(KeyEvent event) {
        return handleTapeHostAction(event, hostActions);
    }

    void releaseAllKeys() {
        keyLatch.releaseAll();
        markFocusLost();
    }

    void tick() {
        keyLatch.tick();
    }

    @Override
    protected List<MatrixKey> keysFor(int keyCode) {
        List<MatrixKey> keys = new ArrayList<>(1);
        switch (keyCode) {
            case KeyEvent.VK_HOME -> keys.add(key(0, 0));
            case KeyEvent.VK_INSERT -> keys.add(key(0, 1));
            case KeyEvent.VK_ESCAPE -> keys.add(key(0, 2));
            case KeyEvent.VK_F1 -> keys.add(key(0, 3));
            case KeyEvent.VK_F2 -> keys.add(key(0, 4));
            case KeyEvent.VK_F3 -> keys.add(key(0, 5));
            case KeyEvent.VK_F4 -> keys.add(key(0, 6));
            case KeyEvent.VK_F5 -> keys.add(key(0, 7));

            case KeyEvent.VK_TAB -> keys.add(key(1, 0));
            case KeyEvent.VK_END -> keys.add(key(1, 1));
            case KeyEvent.VK_ENTER -> keys.add(key(1, 2));
            case KeyEvent.VK_BACK_SPACE -> keys.add(key(1, 3));
            case KeyEvent.VK_LEFT -> keys.add(key(1, 4));
            case KeyEvent.VK_UP -> keys.add(key(1, 5));
            case KeyEvent.VK_RIGHT -> keys.add(key(1, 6));
            case KeyEvent.VK_DOWN -> keys.add(key(1, 7));

            case KeyEvent.VK_0 -> keys.add(key(2, 0));
            case KeyEvent.VK_1 -> keys.add(key(2, 1));
            case KeyEvent.VK_2 -> keys.add(key(2, 2));
            case KeyEvent.VK_3 -> keys.add(key(2, 3));
            case KeyEvent.VK_4 -> keys.add(key(2, 4));
            case KeyEvent.VK_5 -> keys.add(key(2, 5));
            case KeyEvent.VK_6 -> keys.add(key(2, 6));
            case KeyEvent.VK_7 -> keys.add(key(2, 7));

            case KeyEvent.VK_8 -> keys.add(key(3, 0));
            case KeyEvent.VK_9 -> keys.add(key(3, 1));
            case KeyEvent.VK_EQUALS -> keys.add(key(3, 2));
            case KeyEvent.VK_SEMICOLON -> keys.add(key(3, 3));
            case KeyEvent.VK_COMMA -> keys.add(key(3, 4));
            case KeyEvent.VK_MINUS -> keys.add(key(3, 5));
            case KeyEvent.VK_PERIOD -> keys.add(key(3, 6));
            case KeyEvent.VK_SLASH -> keys.add(key(3, 7));

            case KeyEvent.VK_BACK_QUOTE -> keys.add(key(4, 0));
            case KeyEvent.VK_A -> keys.add(key(4, 1));
            case KeyEvent.VK_B -> keys.add(key(4, 2));
            case KeyEvent.VK_C -> keys.add(key(4, 3));
            case KeyEvent.VK_D -> keys.add(key(4, 4));
            case KeyEvent.VK_E -> keys.add(key(4, 5));
            case KeyEvent.VK_F -> keys.add(key(4, 6));
            case KeyEvent.VK_G -> keys.add(key(4, 7));

            case KeyEvent.VK_H -> keys.add(key(5, 0));
            case KeyEvent.VK_I -> keys.add(key(5, 1));
            case KeyEvent.VK_J -> keys.add(key(5, 2));
            case KeyEvent.VK_K -> keys.add(key(5, 3));
            case KeyEvent.VK_L -> keys.add(key(5, 4));
            case KeyEvent.VK_M -> keys.add(key(5, 5));
            case KeyEvent.VK_N -> keys.add(key(5, 6));
            case KeyEvent.VK_O -> keys.add(key(5, 7));

            case KeyEvent.VK_P -> keys.add(key(6, 0));
            case KeyEvent.VK_Q -> keys.add(key(6, 1));
            case KeyEvent.VK_R -> keys.add(key(6, 2));
            case KeyEvent.VK_S -> keys.add(key(6, 3));
            case KeyEvent.VK_T -> keys.add(key(6, 4));
            case KeyEvent.VK_U -> keys.add(key(6, 5));
            case KeyEvent.VK_V -> keys.add(key(6, 6));
            case KeyEvent.VK_W -> keys.add(key(6, 7));

            case KeyEvent.VK_X -> keys.add(key(7, 0));
            case KeyEvent.VK_Y -> keys.add(key(7, 1));
            case KeyEvent.VK_Z -> keys.add(key(7, 2));
            case KeyEvent.VK_OPEN_BRACKET -> keys.add(key(7, 3));
            case KeyEvent.VK_BACK_SLASH -> keys.add(key(7, 4));
            case KeyEvent.VK_CLOSE_BRACKET -> keys.add(key(7, 5));
            case KeyEvent.VK_QUOTE -> keys.add(key(7, 6));
            case KeyEvent.VK_SPACE -> keys.add(key(7, 7));

            case KeyEvent.VK_SHIFT -> keys.add(key(8, 5));
            case KeyEvent.VK_CONTROL -> keys.add(key(8, 6));
            case KeyEvent.VK_ALT, KeyEvent.VK_ALT_GRAPH -> keys.add(key(8, 7));
            default -> {
            }
        }
        return keys;
    }

    @Override
    protected void updateKey(MatrixKey key, boolean pressed) {
        if (pressed) {
            keyLatch.press(key.row(), key.column());
        } else {
            keyLatch.release(key.row(), key.column());
        }
    }

    private MatrixKey key(int row, int column) {
        return new MatrixKey(row, column);
    }

    record MatrixKey(int row, int column) {
    }

    interface HostActions extends HostTapeActions {
    }
}
