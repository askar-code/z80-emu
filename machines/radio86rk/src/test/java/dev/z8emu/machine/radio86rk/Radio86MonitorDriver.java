package dev.z8emu.machine.radio86rk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

final class Radio86MonitorDriver {
    private static final long DEFAULT_MAX_BOOT_INSTRUCTIONS = 200_000L;
    private static final int PREDICATE_POLL_INTERVAL = 2_000;

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
        return Radio86RomLocator.locate("radio86.monitorRom", "mon32.bin");
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
            steps += Radio86MonitorConsole.typeCharacter(machine, text.charAt(i));
        }
    }

    String[] visibleLines() {
        return Radio86MonitorConsole.visibleLines(machine);
    }

    boolean monitorReady() {
        return Radio86MonitorConsole.monitorReady(machine);
    }

    void runUntilPrompt(long maxInstructions) {
        runUntil(this::monitorReady, maxInstructions);
    }

    void runUntilScreenContains(String expectedText, long maxInstructions) {
        runUntil(() -> screenContains(expectedText), maxInstructions);
    }

    String screenText() {
        return Radio86MonitorConsole.screenText(machine);
    }

    private boolean screenContains(String expectedText) {
        for (String line : visibleLines()) {
            if (line.contains(expectedText)) {
                return true;
            }
        }
        return false;
    }

    private void runUntil(BooleanSupplier predicate, long maxInstructions) {
        while (steps < maxInstructions) {
            if (predicate.getAsBoolean()) {
                return;
            }
            long batch = Math.min(PREDICATE_POLL_INTERVAL, maxInstructions - steps);
            for (long i = 0; i < batch; i++) {
                machine.runInstruction();
                steps++;
            }
        }
    }
}
