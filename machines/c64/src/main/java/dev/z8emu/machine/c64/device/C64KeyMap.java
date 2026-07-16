package dev.z8emu.machine.c64.device;

public final class C64KeyMap {
    private static final MatrixKey LEFT_SHIFT = key(1, 7);

    private C64KeyMap() {
    }

    public static KeyChord forCharacter(char character) {
        KeyChord chord = forCharacterOrNull(character);
        if (chord == null) {
            throw new IllegalArgumentException("Unsupported C64 host character: " + character);
        }
        return chord;
    }

    public static KeyChord forCharacterOrNull(char character) {
        return switch (Character.toUpperCase(character)) {
            case '\r', '\n' -> chord(key(0, 1));
            case ' ' -> chord(key(7, 4));

            case '0' -> chord(key(4, 3));
            case '1' -> chord(key(7, 0));
            case '2' -> chord(key(7, 3));
            case '3' -> chord(key(1, 0));
            case '4' -> chord(key(1, 3));
            case '5' -> chord(key(2, 0));
            case '6' -> chord(key(2, 3));
            case '7' -> chord(key(3, 0));
            case '8' -> chord(key(3, 3));
            case '9' -> chord(key(4, 0));

            case 'A' -> chord(key(1, 2));
            case 'B' -> chord(key(3, 4));
            case 'C' -> chord(key(2, 4));
            case 'D' -> chord(key(2, 2));
            case 'E' -> chord(key(1, 6));
            case 'F' -> chord(key(2, 5));
            case 'G' -> chord(key(3, 2));
            case 'H' -> chord(key(3, 5));
            case 'I' -> chord(key(4, 1));
            case 'J' -> chord(key(4, 2));
            case 'K' -> chord(key(4, 5));
            case 'L' -> chord(key(5, 2));
            case 'M' -> chord(key(4, 4));
            case 'N' -> chord(key(4, 7));
            case 'O' -> chord(key(4, 6));
            case 'P' -> chord(key(5, 1));
            case 'Q' -> chord(key(7, 6));
            case 'R' -> chord(key(2, 1));
            case 'S' -> chord(key(1, 5));
            case 'T' -> chord(key(2, 6));
            case 'U' -> chord(key(3, 6));
            case 'V' -> chord(key(3, 7));
            case 'W' -> chord(key(1, 1));
            case 'X' -> chord(key(2, 7));
            case 'Y' -> chord(key(3, 1));
            case 'Z' -> chord(key(1, 4));

            case '+' -> chord(key(5, 0));
            case '-' -> chord(key(5, 3));
            case '.' -> chord(key(5, 4));
            case ',' -> chord(key(5, 7));
            case ':' -> chord(key(5, 5));
            case ';' -> chord(key(6, 2));
            case '*' -> chord(key(6, 1));
            case '@' -> chord(key(5, 6));
            case '/' -> chord(key(6, 7));
            case '=' -> chord(key(6, 5));

            case '"' -> shifted(key(7, 3));
            case '!' -> shifted(key(7, 0));
            case '$' -> shifted(key(1, 3));
            case '(' -> shifted(key(3, 3));
            case ')' -> shifted(key(4, 0));
            case '?' -> shifted(key(6, 7));
            case '<' -> shifted(key(5, 7));
            case '>' -> shifted(key(5, 4));
            default -> null;
        };
    }

    private static KeyChord shifted(MatrixKey key) {
        return chord(LEFT_SHIFT, key);
    }

    private static KeyChord chord(MatrixKey... keys) {
        return new KeyChord(keys);
    }

    private static MatrixKey key(int portABit, int portBBit) {
        return new MatrixKey(portABit, portBBit);
    }

    public record MatrixKey(int portABit, int portBBit) {
    }

    public record KeyChord(MatrixKey[] keys) {
    }
}
