package dev.z8emu.machine.radio86rk;

import dev.z8emu.machine.radio86rk.device.Radio86KeyMap;
import dev.z8emu.machine.radio86rk.device.Radio86VideoDevice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class Radio86MonitorDriver {
    private static final long DEFAULT_MAX_BOOT_INSTRUCTIONS = 200_000L;
    private static final int PRESS_FRAMES = 2;
    private static final int GAP_FRAMES = 2;

    private final Radio86Machine machine;
    private long steps;

    private Radio86MonitorDriver(Radio86Machine machine) {
        this.machine = machine;
    }

    static Radio86MonitorDriver bootDefaultRom() throws IOException {
        Path romPath = locateMonitorRom();
        if (romPath == null) {
            throw new IOException("Monitor ROM not found");
        }
        return boot(Files.readAllBytes(romPath));
    }

    static Path locateMonitorRom() {
        String explicitPath = System.getProperty("radio86.monitorRom");
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path path = Path.of(explicitPath).toAbsolutePath().normalize();
            return Files.exists(path) ? path : null;
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("mon32.bin");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    static Radio86MonitorDriver boot(byte[] romImage) {
        Radio86MonitorDriver driver = new Radio86MonitorDriver(new Radio86Machine(romImage));
        driver.runUntilPrompt(DEFAULT_MAX_BOOT_INSTRUCTIONS);
        return driver;
    }

    Radio86Machine machine() {
        return machine;
    }

    long steps() {
        return steps;
    }

    void typeText(String text) {
        for (int i = 0; i < text.length(); i++) {
            typeCharacter(text.charAt(i));
        }
    }

    String[] visibleLines() {
        String[] lines = new String[Radio86VideoDevice.VISIBLE_ROWS];
        for (int row = 0; row < lines.length; row++) {
            StringBuilder line = new StringBuilder(Radio86VideoDevice.VISIBLE_COLUMNS);
            int offset = Radio86VideoDevice.VISIBLE_OFFSET + (row * Radio86VideoDevice.TOTAL_COLUMNS);
            for (int column = 0; column < Radio86VideoDevice.VISIBLE_COLUMNS; column++) {
                int code = machine.board().memory().readVideoByte(offset + column);
                line.append(renderCharacter(code));
            }
            lines[row] = line.toString();
        }
        return lines;
    }

    boolean monitorReady() {
        String[] screen = visibleLines();
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

    void runUntilPrompt(long maxInstructions) {
        runUntil(this::monitorReady, maxInstructions);
    }

    void runUntilScreenContains(String expectedText, long maxInstructions) {
        runUntil(() -> screenContains(expectedText), maxInstructions);
    }

    String screenText() {
        return String.join("\n", visibleLines());
    }

    private boolean screenContains(String expectedText) {
        for (String line : visibleLines()) {
            if (line.contains(expectedText)) {
                return true;
            }
        }
        return false;
    }

    private void typeCharacter(char character) {
        Radio86KeyMap.KeyChord chord = Radio86KeyMap.forCharacter(character);
        setChordState(chord, true);
        runFrames(PRESS_FRAMES);
        setChordState(chord, false);
        runFrames(GAP_FRAMES);
    }

    private void setChordState(Radio86KeyMap.KeyChord chord, boolean pressed) {
        for (Radio86KeyMap.MatrixKey key : chord.keys()) {
            machine.board().keyboard().setKeyPressed(key.row(), key.column(), pressed);
        }
    }

    private void runFrames(int frames) {
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            long targetTState = machine.currentTState() + machine.frameTStates();
            while (machine.currentTState() < targetTState) {
                machine.runInstruction();
                steps++;
            }
        }
    }

    private void runUntil(ScreenPredicate predicate, long maxInstructions) {
        while (steps < maxInstructions && !predicate.matches()) {
            machine.runInstruction();
            steps++;
        }
    }

    private static char renderCharacter(int code) {
        int normalized = code & 0x7F;
        if (normalized == 0) {
            return ' ';
        }
        if (normalized >= 0x20 && normalized <= 0x7E) {
            return (char) normalized;
        }
        return '.';
    }

    @FunctionalInterface
    private interface ScreenPredicate {
        boolean matches();
    }
}
