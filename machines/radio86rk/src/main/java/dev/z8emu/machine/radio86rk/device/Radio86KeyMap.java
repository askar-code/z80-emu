package dev.z8emu.machine.radio86rk.device;

public final class Radio86KeyMap {
    private Radio86KeyMap() {
    }

    public static KeyChord forCharacter(char character) {
        return switch (Character.toUpperCase(character)) {
            case '\r', '\n' -> chord(key(1, 2));
            case '\b' -> chord(key(1, 3));
            case ' ' -> chord(key(7, 7));

            case '0' -> chord(key(2, 0));
            case '1' -> chord(key(2, 1));
            case '2' -> chord(key(2, 2));
            case '3' -> chord(key(2, 3));
            case '4' -> chord(key(2, 4));
            case '5' -> chord(key(2, 5));
            case '6' -> chord(key(2, 6));
            case '7' -> chord(key(2, 7));
            case '8' -> chord(key(3, 0));
            case '9' -> chord(key(3, 1));

            case 'A' -> chord(key(4, 1));
            case 'B' -> chord(key(4, 2));
            case 'C' -> chord(key(4, 3));
            case 'D' -> chord(key(4, 4));
            case 'E' -> chord(key(4, 5));
            case 'F' -> chord(key(4, 6));
            case 'G' -> chord(key(4, 7));
            case 'H' -> chord(key(5, 0));
            case 'I' -> chord(key(5, 1));
            case 'J' -> chord(key(5, 2));
            case 'K' -> chord(key(5, 3));
            case 'L' -> chord(key(5, 4));
            case 'M' -> chord(key(5, 5));
            case 'N' -> chord(key(5, 6));
            case 'O' -> chord(key(5, 7));
            case 'P' -> chord(key(6, 0));
            case 'Q' -> chord(key(6, 1));
            case 'R' -> chord(key(6, 2));
            case 'S' -> chord(key(6, 3));
            case 'T' -> chord(key(6, 4));
            case 'U' -> chord(key(6, 5));
            case 'V' -> chord(key(6, 6));
            case 'W' -> chord(key(6, 7));
            case 'X' -> chord(key(7, 0));
            case 'Y' -> chord(key(7, 1));
            case 'Z' -> chord(key(7, 2));

            case '-' -> chord(key(3, 5));
            case ',' -> chord(key(3, 4));
            case '.' -> chord(key(3, 6));
            case '/' -> chord(key(3, 7));
            case ':' -> chord(key(3, 2));
            default -> throw new IllegalArgumentException("Unsupported Radio-86RK host character: " + character);
        };
    }

    private static KeyChord chord(MatrixKey... keys) {
        return new KeyChord(keys);
    }

    private static MatrixKey key(int row, int column) {
        return new MatrixKey(row, column);
    }

    public record MatrixKey(int row, int column) {
    }

    public record KeyChord(MatrixKey[] keys) {
    }
}
