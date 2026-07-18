package dev.z8emu.app.desktop;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;

enum SpectrumJoystickProfile {
    CURSOR("cursor"),
    KEMPSTON("kempston"),
    SINCLAIR_1("sinclair1"),
    SINCLAIR_2("sinclair2");

    static final String PROPERTY_NAME = "z8emu.spectrum.joystick";

    private static final MatrixKey CAPS_SHIFT = new MatrixKey(0, 0);

    private final String settingValue;

    SpectrumJoystickProfile(String settingValue) {
        this.settingValue = settingValue;
    }

    static SpectrumJoystickProfile fromSetting(String value) {
        if (value == null || value.isBlank()) {
            return CURSOR;
        }

        return switch (value.strip().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "")) {
            case "kempston" -> KEMPSTON;
            case "sinclair1" -> SINCLAIR_1;
            case "sinclair2" -> SINCLAIR_2;
            case "cursor" -> CURSOR;
            default -> CURSOR;
        };
    }

    String settingValue() {
        return settingValue;
    }

    SpectrumJoystickProfile next() {
        SpectrumJoystickProfile[] profiles = values();
        return profiles[(ordinal() + 1) % profiles.length];
    }

    boolean usesKempstonInterface() {
        return this == KEMPSTON;
    }

    String fireKeyHint() {
        return this == CURSOR ? "Numpad0" : "Ctrl/Numpad0";
    }

    JoystickAction actionForHostKey(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_LEFT -> JoystickAction.LEFT;
            case KeyEvent.VK_RIGHT -> JoystickAction.RIGHT;
            case KeyEvent.VK_UP -> JoystickAction.UP;
            case KeyEvent.VK_DOWN -> JoystickAction.DOWN;
            case KeyEvent.VK_NUMPAD0 -> JoystickAction.FIRE;
            case KeyEvent.VK_CONTROL -> this == CURSOR ? null : JoystickAction.FIRE;
            default -> null;
        };
    }

    List<MatrixKey> matrixKeys(JoystickAction action) {
        return switch (this) {
            case KEMPSTON -> List.of();
            case CURSOR -> switch (action) {
                case LEFT -> List.of(CAPS_SHIFT, new MatrixKey(3, 4));
                case RIGHT -> List.of(CAPS_SHIFT, new MatrixKey(4, 2));
                case UP -> List.of(CAPS_SHIFT, new MatrixKey(4, 3));
                case DOWN -> List.of(CAPS_SHIFT, new MatrixKey(4, 4));
                case FIRE -> List.of(new MatrixKey(4, 0));
            };
            case SINCLAIR_1 -> switch (action) {
                case LEFT -> List.of(new MatrixKey(4, 4));
                case RIGHT -> List.of(new MatrixKey(4, 3));
                case UP -> List.of(new MatrixKey(4, 1));
                case DOWN -> List.of(new MatrixKey(4, 2));
                case FIRE -> List.of(new MatrixKey(4, 0));
            };
            case SINCLAIR_2 -> switch (action) {
                case LEFT -> List.of(new MatrixKey(3, 0));
                case RIGHT -> List.of(new MatrixKey(3, 1));
                case UP -> List.of(new MatrixKey(3, 3));
                case DOWN -> List.of(new MatrixKey(3, 2));
                case FIRE -> List.of(new MatrixKey(3, 4));
            };
        };
    }

    enum JoystickAction {
        LEFT,
        RIGHT,
        UP,
        DOWN,
        FIRE
    }

    record MatrixKey(int row, int column) {
    }
}
