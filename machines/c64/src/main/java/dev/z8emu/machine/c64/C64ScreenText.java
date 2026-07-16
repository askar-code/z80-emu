package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.device.C64VideoDevice;

public final class C64ScreenText {
    private C64ScreenText() {
    }

    public static boolean contains(C64Machine machine, String expectedScreen) {
        return String.join("\n", visibleLines(machine)).contains(expectedScreen);
    }

    public static String[] visibleLines(C64Machine machine) {
        String[] lines = new String[C64VideoDevice.TEXT_ROWS];
        int d018 = machine.board().video().readRegister(0x18);
        int bank = (~machine.board().cia2().readRegister(0x00)) & 0x03;
        int matrix = bank * 0x4000 + ((d018 >> 4) & 0x0F) * 0x400;
        for (int row = 0; row < lines.length; row++) {
            StringBuilder line = new StringBuilder(C64VideoDevice.TEXT_COLUMNS);
            for (int column = 0; column < C64VideoDevice.TEXT_COLUMNS; column++) {
                int screenCode = machine.board().memory().readRam(matrix + row * C64VideoDevice.TEXT_COLUMNS + column);
                line.append(renderCharacter(screenCode));
            }
            lines[row] = line.toString();
        }
        return lines;
    }

    public static char renderCharacter(int screenCode) {
        int code = screenCode & 0x7F;
        if (code == 0x00) {
            return '@';
        }
        if (code >= 0x01 && code <= 0x1A) {
            return (char) ('A' + code - 1);
        }
        if (code >= 0x20 && code <= 0x3F) {
            return (char) code;
        }
        return '.';
    }
}
