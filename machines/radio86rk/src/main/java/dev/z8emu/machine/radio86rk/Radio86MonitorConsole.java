package dev.z8emu.machine.radio86rk;

import dev.z8emu.machine.radio86rk.device.Radio86DmaDevice;
import dev.z8emu.machine.radio86rk.device.Radio86KeyMap;
import dev.z8emu.machine.radio86rk.device.Radio86VideoDevice;

/**
 * Monitor-ROM interaction helpers shared by the headless probe launcher and
 * the test driver: screen scraping, prompt detection, and keyboard typing.
 * The type/run helpers return the number of instructions executed so callers
 * can keep their own step accounting.
 */
public final class Radio86MonitorConsole {
    private static final int PRESS_FRAMES = 2;
    private static final int GAP_FRAMES = 2;

    private Radio86MonitorConsole() {
    }

    public static String[] visibleLines(Radio86Machine machine) {
        Radio86DmaDevice dma = machine.board().dma();
        int base = dma.channelBaseAddress(Radio86DmaDevice.CHANNEL_VIDEO);
        int length = dma.channelTransferLength(Radio86DmaDevice.CHANNEL_VIDEO);
        String[] lines = new String[Radio86VideoDevice.VISIBLE_ROWS];
        for (int row = 0; row < lines.length; row++) {
            StringBuilder line = new StringBuilder(Radio86VideoDevice.VISIBLE_COLUMNS);
            int offset = Radio86VideoDevice.VISIBLE_OFFSET + (row * Radio86VideoDevice.TOTAL_COLUMNS);
            for (int column = 0; column < Radio86VideoDevice.VISIBLE_COLUMNS; column++) {
                int cellOffset = offset + column;
                int code = cellOffset < 0 || cellOffset >= length
                        ? 0x20
                        : machine.board().memory().peekRam(base + cellOffset);
                line.append(renderCharacter(code));
            }
            lines[row] = line.toString();
        }
        return lines;
    }

    public static String screenText(Radio86Machine machine) {
        return String.join("\n", visibleLines(machine));
    }

    public static boolean monitorReady(Radio86Machine machine) {
        String[] screen = visibleLines(machine);
        if (!screen[0].contains("radio-86rk")) {
            return false;
        }
        for (String line : screen) {
            if (line.stripTrailing().endsWith("-->")) {
                return true;
            }
        }
        return false;
    }

    public static char renderCharacter(int code) {
        int normalized = code & 0x7F;
        if (normalized == 0) {
            return ' ';
        }
        if (normalized >= 0x20 && normalized <= 0x7E) {
            return (char) normalized;
        }
        return '.';
    }

    public static long typeCharacter(Radio86Machine machine, char character) {
        Radio86KeyMap.KeyChord chord = Radio86KeyMap.forCharacter(character);
        setChordState(machine, chord, true);
        long executed = runFrames(machine, PRESS_FRAMES);
        setChordState(machine, chord, false);
        return executed + runFrames(machine, GAP_FRAMES);
    }

    public static long runFrames(Radio86Machine machine, int frames) {
        long executed = 0;
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            long targetTState = machine.currentTState() + machine.frameTStates();
            while (machine.currentTState() < targetTState) {
                machine.runInstruction();
                executed++;
            }
        }
        return executed;
    }

    private static void setChordState(Radio86Machine machine, Radio86KeyMap.KeyChord chord, boolean pressed) {
        for (Radio86KeyMap.MatrixKey key : chord.keys()) {
            machine.board().keyboard().setKeyPressed(key.row(), key.column(), pressed);
        }
    }
}
