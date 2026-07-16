package dev.z8emu.machine.cpc.device;

public final class CpcKeyMap {
    private static final MatrixKey SHIFT = key(2, 5);

    private CpcKeyMap() {
    }

    public static KeyChord forCharacter(char character) {
        KeyChord chord = forCharacterOrNull(character);
        if (chord == null) {
            throw new IllegalArgumentException("Unsupported CPC host character: " + character);
        }
        return chord;
    }

    public static KeyChord forNameOrNull(String name) {
        if (name == null) {
            return null;
        }
        if ("UP".equalsIgnoreCase(name)) {
            return chord(key(0, 0));
        }
        if ("RIGHT".equalsIgnoreCase(name)) {
            return chord(key(0, 1));
        }
        if ("DOWN".equalsIgnoreCase(name)) {
            return chord(key(0, 2));
        }
        if ("LEFT".equalsIgnoreCase(name)) {
            return chord(key(1, 0));
        }
        return name.length() == 1 ? forCharacterOrNull(name.charAt(0)) : null;
    }

    public static KeyChord forCharacterOrNull(char character) {
        return switch (Character.toUpperCase(character)) {
            case '\r', '\n' -> chord(key(2, 2));
            case ' ' -> chord(key(5, 7));

            case '0' -> chord(key(4, 0));
            case '1' -> chord(key(8, 0));
            case '2' -> chord(key(8, 1));
            case '3' -> chord(key(7, 1));
            case '4' -> chord(key(7, 0));
            case '5' -> chord(key(6, 1));
            case '6' -> chord(key(6, 0));
            case '7' -> chord(key(5, 1));
            case '8' -> chord(key(5, 0));
            case '9' -> chord(key(4, 1));

            case 'A' -> chord(key(8, 5));
            case 'B' -> chord(key(6, 6));
            case 'C' -> chord(key(7, 6));
            case 'D' -> chord(key(7, 5));
            case 'E' -> chord(key(7, 2));
            case 'F' -> chord(key(6, 5));
            case 'G' -> chord(key(6, 4));
            case 'H' -> chord(key(5, 4));
            case 'I' -> chord(key(4, 3));
            case 'J' -> chord(key(5, 5));
            case 'K' -> chord(key(4, 5));
            case 'L' -> chord(key(4, 4));
            case 'M' -> chord(key(4, 6));
            case 'N' -> chord(key(5, 6));
            case 'O' -> chord(key(4, 2));
            case 'P' -> chord(key(3, 3));
            case 'Q' -> chord(key(8, 3));
            case 'R' -> chord(key(6, 2));
            case 'S' -> chord(key(7, 4));
            case 'T' -> chord(key(6, 3));
            case 'U' -> chord(key(5, 2));
            case 'V' -> chord(key(6, 7));
            case 'W' -> chord(key(7, 3));
            case 'X' -> chord(key(7, 7));
            case 'Y' -> chord(key(5, 3));
            case 'Z' -> chord(key(8, 7));

            case ',' -> chord(key(4, 7));
            case '.' -> chord(key(3, 7));
            case ':' -> chord(key(3, 5));
            case ';' -> chord(key(3, 4));
            case '-' -> chord(key(3, 1));

            case '"' -> shifted(key(8, 1));
            case '&' -> shifted(key(6, 0));
            case '(' -> shifted(key(5, 0));
            case ')' -> shifted(key(4, 1));
            default -> null;
        };
    }

    private static KeyChord shifted(MatrixKey key) {
        return chord(SHIFT, key);
    }

    private static KeyChord chord(MatrixKey... keys) {
        return new KeyChord(keys);
    }

    private static MatrixKey key(int line, int bit) {
        return new MatrixKey(line, bit);
    }

    public record MatrixKey(int line, int bit) {
    }

    public record KeyChord(MatrixKey[] keys) {
    }
}
