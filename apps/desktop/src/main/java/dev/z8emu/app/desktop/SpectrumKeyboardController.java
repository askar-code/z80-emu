package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.KempstonJoystickDevice;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;

final class SpectrumKeyboardController extends AbstractHostKeyboardController {
    private final KeyboardMatrixDevice keyboard;
    private final KempstonJoystickDevice kempstonJoystick;
    private SpectrumJoystickProfile joystickProfile;
    private final HostActions hostActions;
    private final Set<Integer> pressedHostKeys = new HashSet<>();

    private SpectrumKeyboardController(
            Window window,
            KeyboardMatrixDevice keyboard,
            KempstonJoystickDevice kempstonJoystick,
            SpectrumJoystickProfile joystickProfile,
            HostActions hostActions
    ) {
        super(window);
        this.keyboard = keyboard;
        this.kempstonJoystick = kempstonJoystick;
        this.joystickProfile = joystickProfile;
        this.hostActions = hostActions;
        kempstonJoystick.setEnabled(joystickProfile.usesKempstonInterface());
    }

    static SpectrumKeyboardController bind(
            Window window,
            JComponent component,
            KeyboardMatrixDevice keyboard,
            KempstonJoystickDevice kempstonJoystick,
            SpectrumJoystickProfile joystickProfile,
            HostActions hostActions
    ) {
        SpectrumKeyboardController controller = new SpectrumKeyboardController(
                window,
                keyboard,
                kempstonJoystick,
                joystickProfile,
                hostActions
        );
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

        releaseAllInput();
        setLastEvent("char:" + character);
        return true;
    }

    @Override
    protected boolean handleHostAction(KeyEvent event) {
        if (event.isMetaDown() && event.isShiftDown()) {
            return switch (event.getKeyCode()) {
                case KeyEvent.VK_O -> {
                    if (event.getID() == KeyEvent.KEY_RELEASED) {
                        hostActions.loadSnapshot();
                        setLastEvent("host:Cmd+Shift+O");
                    }
                    yield true;
                }
                case KeyEvent.VK_S -> {
                    if (event.getID() == KeyEvent.KEY_RELEASED) {
                        hostActions.saveSnapshot();
                        setLastEvent("host:Cmd+Shift+S");
                    }
                    yield true;
                }
                default -> false;
            };
        }
        if (event.isMetaDown()) {
            boolean handled = switch (event.getKeyCode()) {
                case KeyEvent.VK_O -> {
                    if (event.getID() == KeyEvent.KEY_RELEASED) {
                        hostActions.openTape();
                        setLastEvent("host:Cmd+O");
                    }
                    yield true;
                }
                case KeyEvent.VK_E -> {
                    if (event.getID() == KeyEvent.KEY_RELEASED) {
                        hostActions.ejectTape();
                        setLastEvent("host:Cmd+E");
                    }
                    yield true;
                }
                case KeyEvent.VK_J -> {
                    if (event.getID() == KeyEvent.KEY_RELEASED) {
                        hostActions.cycleJoystickProfile();
                        setLastEvent("host:Cmd+J");
                    }
                    yield true;
                }
                case KeyEvent.VK_OPEN_BRACKET -> {
                    if (event.getID() == KeyEvent.KEY_RELEASED) {
                        hostActions.previousTapeBlock();
                        setLastEvent("host:Cmd+[");
                    }
                    yield true;
                }
                case KeyEvent.VK_CLOSE_BRACKET -> {
                    if (event.getID() == KeyEvent.KEY_RELEASED) {
                        hostActions.nextTapeBlock();
                        setLastEvent("host:Cmd+]");
                    }
                    yield true;
                }
                default -> false;
            };
            if (handled) {
                return true;
            }
        }
        return handleTapeHostAction(event, hostActions);
    }

    void releaseAllKeys() {
        releaseAllInput();
        markFocusLost();
    }

    void setJoystickProfile(SpectrumJoystickProfile joystickProfile) {
        synchronized (pressedHostKeys) {
            this.joystickProfile = joystickProfile;
            kempstonJoystick.setEnabled(joystickProfile.usesKempstonInterface());
            rebuildInputState();
        }
    }

    @Override
    protected void updateKeys(KeyEvent event, boolean pressed) {
        synchronized (pressedHostKeys) {
            boolean changed = pressed
                    ? pressedHostKeys.add(event.getKeyCode())
                    : pressedHostKeys.remove(event.getKeyCode());
            if (!changed) {
                return;
            }
            rebuildInputState();
        }
        setLastEvent("%s:%s".formatted(
                pressed ? "down" : "up",
                KeyEvent.getKeyText(event.getKeyCode())
        ));
    }

    private void rebuildInputState() {
        int kempstonState = 0;
        synchronized (keyboard) {
            keyboard.releaseAllKeys();
            for (int keyCode : pressedHostKeys) {
                SpectrumJoystickProfile.JoystickAction joystickAction =
                        joystickProfile.actionForHostKey(keyCode);
                if (joystickAction != null) {
                    if (joystickProfile.usesKempstonInterface()) {
                        kempstonState |= kempstonMask(joystickAction);
                    } else {
                        pressMatrixKeys(joystickProfile.matrixKeys(joystickAction));
                    }
                } else {
                    pressMatrixKeys(regularKeysFor(keyCode));
                }
            }
        }
        kempstonJoystick.setInputState(kempstonState);
    }

    private void pressMatrixKeys(List<SpectrumJoystickProfile.MatrixKey> keys) {
        for (SpectrumJoystickProfile.MatrixKey key : keys) {
            keyboard.setKeyPressed(key.row(), key.column(), true);
        }
    }

    private List<SpectrumJoystickProfile.MatrixKey> regularKeysFor(int keyCode) {
        List<SpectrumJoystickProfile.MatrixKey> keys = new ArrayList<>(2);

        switch (keyCode) {
            case KeyEvent.VK_SHIFT -> keys.add(matrixKey(0, 0));
            case KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_ALT_GRAPH -> keys.add(matrixKey(7, 1));
            case KeyEvent.VK_ENTER -> keys.add(matrixKey(6, 0));
            case KeyEvent.VK_SPACE -> keys.add(matrixKey(7, 0));

            case KeyEvent.VK_A -> keys.add(matrixKey(1, 0));
            case KeyEvent.VK_S -> keys.add(matrixKey(1, 1));
            case KeyEvent.VK_D -> keys.add(matrixKey(1, 2));
            case KeyEvent.VK_F -> keys.add(matrixKey(1, 3));
            case KeyEvent.VK_G -> keys.add(matrixKey(1, 4));

            case KeyEvent.VK_Q -> keys.add(matrixKey(2, 0));
            case KeyEvent.VK_W -> keys.add(matrixKey(2, 1));
            case KeyEvent.VK_E -> keys.add(matrixKey(2, 2));
            case KeyEvent.VK_R -> keys.add(matrixKey(2, 3));
            case KeyEvent.VK_T -> keys.add(matrixKey(2, 4));

            case KeyEvent.VK_1 -> keys.add(matrixKey(3, 0));
            case KeyEvent.VK_2 -> keys.add(matrixKey(3, 1));
            case KeyEvent.VK_3 -> keys.add(matrixKey(3, 2));
            case KeyEvent.VK_4 -> keys.add(matrixKey(3, 3));
            case KeyEvent.VK_5 -> keys.add(matrixKey(3, 4));

            case KeyEvent.VK_0 -> keys.add(matrixKey(4, 0));
            case KeyEvent.VK_9 -> keys.add(matrixKey(4, 1));
            case KeyEvent.VK_8 -> keys.add(matrixKey(4, 2));
            case KeyEvent.VK_7 -> keys.add(matrixKey(4, 3));
            case KeyEvent.VK_6 -> keys.add(matrixKey(4, 4));

            case KeyEvent.VK_P -> keys.add(matrixKey(5, 0));
            case KeyEvent.VK_O -> keys.add(matrixKey(5, 1));
            case KeyEvent.VK_I -> keys.add(matrixKey(5, 2));
            case KeyEvent.VK_U -> keys.add(matrixKey(5, 3));
            case KeyEvent.VK_Y -> keys.add(matrixKey(5, 4));

            case KeyEvent.VK_L -> keys.add(matrixKey(6, 1));
            case KeyEvent.VK_K -> keys.add(matrixKey(6, 2));
            case KeyEvent.VK_J -> keys.add(matrixKey(6, 3));
            case KeyEvent.VK_H -> keys.add(matrixKey(6, 4));

            case KeyEvent.VK_M -> keys.add(matrixKey(7, 2));
            case KeyEvent.VK_N -> keys.add(matrixKey(7, 3));
            case KeyEvent.VK_B -> keys.add(matrixKey(7, 4));
            case KeyEvent.VK_Z -> keys.add(matrixKey(0, 1));
            case KeyEvent.VK_X -> keys.add(matrixKey(0, 2));
            case KeyEvent.VK_C -> keys.add(matrixKey(0, 3));
            case KeyEvent.VK_V -> keys.add(matrixKey(0, 4));

            case KeyEvent.VK_BACK_SPACE -> {
                keys.add(matrixKey(0, 0));
                keys.add(matrixKey(4, 0));
            }
            default -> {
            }
        }

        return keys;
    }

    private void releaseAllInput() {
        synchronized (pressedHostKeys) {
            pressedHostKeys.clear();
            keyboard.releaseAllKeys();
            kempstonJoystick.reset();
        }
    }

    private static SpectrumJoystickProfile.MatrixKey matrixKey(int row, int column) {
        return new SpectrumJoystickProfile.MatrixKey(row, column);
    }

    private static int kempstonMask(SpectrumJoystickProfile.JoystickAction action) {
        return switch (action) {
            case RIGHT -> KempstonJoystickDevice.RIGHT;
            case LEFT -> KempstonJoystickDevice.LEFT;
            case DOWN -> KempstonJoystickDevice.DOWN;
            case UP -> KempstonJoystickDevice.UP;
            case FIRE -> KempstonJoystickDevice.FIRE;
        };
    }

    interface HostActions extends HostTapeActions {
        boolean typeHostCharacter(char character);

        void openTape();

        void ejectTape();

        void previousTapeBlock();

        void nextTapeBlock();

        void cycleJoystickProfile();

        void loadSnapshot();

        void saveSnapshot();
    }
}
