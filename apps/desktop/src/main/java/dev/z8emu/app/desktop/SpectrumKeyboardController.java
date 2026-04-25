package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

final class SpectrumKeyboardController extends AbstractMappedHostKeyboardController<SpectrumKeyboardController.MatrixKey> {
    private final KeyboardMatrixDevice keyboard;
    private final HostActions hostActions;

    private SpectrumKeyboardController(Window window, KeyboardMatrixDevice keyboard, HostActions hostActions) {
        super(window);
        this.keyboard = keyboard;
        this.hostActions = hostActions;
    }

    static SpectrumKeyboardController bind(Window window, JComponent component, KeyboardMatrixDevice keyboard, HostActions hostActions) {
        SpectrumKeyboardController controller = new SpectrumKeyboardController(window, keyboard, hostActions);
        controller.bindToComponent(component);
        return controller;
    }

    @Override
    protected boolean handleMetaKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.VK_META) {
            setLastEvent(event.getID() == KeyEvent.KEY_RELEASED ? "host:Cmd-up" : "host:Cmd-down");
            return true;
        }
        return false;
    }

    @Override
    protected boolean handleTypedCharacter(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_TYPED) {
            return false;
        }

        if (event.isMetaDown()) {
            return true;
        }

        char character = event.getKeyChar();
        if (character == KeyEvent.CHAR_UNDEFINED) {
            return false;
        }

        if (!hostActions.typeHostCharacter(character)) {
            return false;
        }

        keyboard.releaseAllKeys();
        setLastEvent("char:" + character);
        return true;
    }

    @Override
    protected boolean handleHostAction(KeyEvent event) {
        return handleTapeHostAction(event, hostActions);
    }

    void releaseAllKeys() {
        keyboard.releaseAllKeys();
        markFocusLost();
    }

    @Override
    protected List<MatrixKey> keysFor(int keyCode) {
        List<MatrixKey> keys = new ArrayList<>(2);

        switch (keyCode) {
            case KeyEvent.VK_SHIFT -> keys.add(key(0, 0));
            case KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_ALT_GRAPH -> keys.add(key(7, 1));
            case KeyEvent.VK_ENTER -> keys.add(key(6, 0));
            case KeyEvent.VK_SPACE -> keys.add(key(7, 0));

            case KeyEvent.VK_A -> keys.add(key(1, 0));
            case KeyEvent.VK_S -> keys.add(key(1, 1));
            case KeyEvent.VK_D -> keys.add(key(1, 2));
            case KeyEvent.VK_F -> keys.add(key(1, 3));
            case KeyEvent.VK_G -> keys.add(key(1, 4));

            case KeyEvent.VK_Q -> keys.add(key(2, 0));
            case KeyEvent.VK_W -> keys.add(key(2, 1));
            case KeyEvent.VK_E -> keys.add(key(2, 2));
            case KeyEvent.VK_R -> keys.add(key(2, 3));
            case KeyEvent.VK_T -> keys.add(key(2, 4));

            case KeyEvent.VK_1 -> keys.add(key(3, 0));
            case KeyEvent.VK_2 -> keys.add(key(3, 1));
            case KeyEvent.VK_3 -> keys.add(key(3, 2));
            case KeyEvent.VK_4 -> keys.add(key(3, 3));
            case KeyEvent.VK_5 -> keys.add(key(3, 4));

            case KeyEvent.VK_0 -> keys.add(key(4, 0));
            case KeyEvent.VK_9 -> keys.add(key(4, 1));
            case KeyEvent.VK_8 -> keys.add(key(4, 2));
            case KeyEvent.VK_7 -> keys.add(key(4, 3));
            case KeyEvent.VK_6 -> keys.add(key(4, 4));

            case KeyEvent.VK_P -> keys.add(key(5, 0));
            case KeyEvent.VK_O -> keys.add(key(5, 1));
            case KeyEvent.VK_I -> keys.add(key(5, 2));
            case KeyEvent.VK_U -> keys.add(key(5, 3));
            case KeyEvent.VK_Y -> keys.add(key(5, 4));

            case KeyEvent.VK_L -> keys.add(key(6, 1));
            case KeyEvent.VK_K -> keys.add(key(6, 2));
            case KeyEvent.VK_J -> keys.add(key(6, 3));
            case KeyEvent.VK_H -> keys.add(key(6, 4));

            case KeyEvent.VK_M -> keys.add(key(7, 2));
            case KeyEvent.VK_N -> keys.add(key(7, 3));
            case KeyEvent.VK_B -> keys.add(key(7, 4));
            case KeyEvent.VK_Z -> keys.add(key(0, 1));
            case KeyEvent.VK_X -> keys.add(key(0, 2));
            case KeyEvent.VK_C -> keys.add(key(0, 3));
            case KeyEvent.VK_V -> keys.add(key(0, 4));

            case KeyEvent.VK_LEFT -> {
                keys.add(key(0, 0));
                keys.add(key(3, 4));
            }
            case KeyEvent.VK_DOWN -> {
                keys.add(key(0, 0));
                keys.add(key(4, 4));
            }
            case KeyEvent.VK_UP -> {
                keys.add(key(0, 0));
                keys.add(key(4, 3));
            }
            case KeyEvent.VK_RIGHT -> {
                keys.add(key(0, 0));
                keys.add(key(4, 2));
            }
            case KeyEvent.VK_BACK_SPACE -> {
                keys.add(key(0, 0));
                keys.add(key(4, 0));
            }
            default -> {
            }
        }

        return keys;
    }

    @Override
    protected void updateKey(MatrixKey key, boolean pressed) {
        keyboard.setKeyPressed(key.row(), key.column(), pressed);
    }

    private static MatrixKey key(int row, int column) {
        return new MatrixKey(row, column);
    }

    record MatrixKey(int row, int column) {
    }

    interface HostActions extends HostTapeActions {
        boolean typeHostCharacter(char character);
    }
}
