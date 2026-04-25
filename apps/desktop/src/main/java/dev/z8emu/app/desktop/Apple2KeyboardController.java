package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.device.Apple2KeyboardDevice;
import java.awt.Window;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;

final class Apple2KeyboardController extends AbstractHostKeyboardController {
    private final Apple2KeyboardDevice keyboard;

    private Apple2KeyboardController(Window window, Apple2KeyboardDevice keyboard) {
        super(window);
        this.keyboard = keyboard;
    }

    static Apple2KeyboardController bind(Window window, JComponent component, Apple2KeyboardDevice keyboard) {
        Apple2KeyboardController controller = new Apple2KeyboardController(window, keyboard);
        controller.bindToComponent(component);
        return controller;
    }

    void releaseAllKeys() {
        keyboard.releaseAllKeys();
        markFocusLost();
    }

    @Override
    protected boolean handleTypedCharacter(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_TYPED) {
            return false;
        }

        int ascii = asciiForTypedCharacter(event.getKeyChar());
        if (ascii < 0) {
            return false;
        }

        keyboard.pressKey(ascii);
        setLastEvent("type:" + displayCharacter(ascii));
        return true;
    }

    @Override
    protected void updateKeys(KeyEvent event, boolean pressed) {
        if (!pressed) {
            return;
        }

        int ascii = asciiForSpecialKey(event.getKeyCode());
        if (ascii < 0) {
            return;
        }

        keyboard.pressKey(ascii);
        setLastEvent("down:" + KeyEvent.getKeyText(event.getKeyCode()));
    }

    private static int asciiForTypedCharacter(char character) {
        return switch (character) {
            case '\n', '\r' -> 0x0D;
            case '\b' -> 0x08;
            case 0x1B -> 0x1B;
            default -> {
                if (character >= 0x20 && character <= 0x7E) {
                    yield Character.toUpperCase(character) & 0x7F;
                }
                yield -1;
            }
        };
    }

    private static int asciiForSpecialKey(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_ENTER -> 0x0D;
            case KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE, KeyEvent.VK_LEFT -> 0x08;
            case KeyEvent.VK_RIGHT -> 0x15;
            case KeyEvent.VK_ESCAPE -> 0x1B;
            default -> -1;
        };
    }

    private static String displayCharacter(int ascii) {
        return switch (ascii) {
            case 0x08 -> "Backspace";
            case 0x0D -> "Enter";
            case 0x15 -> "Right";
            case 0x1B -> "Escape";
            default -> Character.toString((char) ascii);
        };
    }
}
