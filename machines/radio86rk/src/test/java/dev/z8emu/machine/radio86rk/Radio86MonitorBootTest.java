package dev.z8emu.machine.radio86rk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Radio86MonitorBootTest {
    @Test
    void monitorRomBootsToPrompt() throws IOException {
        Path romPath = Radio86MonitorDriver.locateMonitorRom();
        Assumptions.assumeTrue(romPath != null, () -> "Skipping: mon32.bin not found");

        Radio86MonitorDriver driver = Radio86MonitorDriver.boot(Files.readAllBytes(romPath));
        Radio86Machine machine = driver.machine();
        String[] screen = driver.visibleLines();

        assertFalse(machine.board().memory().bootShadowEnabled(), failureContext(driver));
        assertTrue(driver.monitorReady(), failureContext(driver));
        assertTrue(screen[1].stripTrailing().endsWith("-->"), failureContext(driver));
    }

    @Test
    void monitorAcceptsKeyboardInputAtPrompt() throws IOException {
        Path romPath = Radio86MonitorDriver.locateMonitorRom();
        Assumptions.assumeTrue(romPath != null, () -> "Skipping: mon32.bin not found");

        Radio86MonitorDriver driver = Radio86MonitorDriver.boot(Files.readAllBytes(romPath));
        driver.typeText("A");

        assertTrue(driver.visibleLines()[1].contains("-->A"), failureContext(driver));
    }

    @Test
    void dumpCommandDisplaysMemoryLineAndReturnsPrompt() throws IOException {
        Path romPath = Radio86MonitorDriver.locateMonitorRom();
        Assumptions.assumeTrue(romPath != null, () -> "Skipping: mon32.bin not found");

        Radio86MonitorDriver driver = Radio86MonitorDriver.boot(Files.readAllBytes(romPath));
        driver.typeText("D\r");
        driver.runUntilScreenContains("0000 00", 120_000);

        String[] screen = driver.visibleLines();
        assertTrue(screen[2].contains("0000 00"), failureContext(driver));
        assertTrue(screen[3].stripTrailing().endsWith("-->"), failureContext(driver));
    }

    private static String failureContext(Radio86MonitorDriver driver) {
        Radio86Machine machine = driver.machine();
        StringBuilder message = new StringBuilder();
        message.append("Monitor interaction failed")
                .append(" steps=").append(driver.steps())
                .append(" pc=0x").append(hex16(machine.cpu().registers().pc()))
                .append(" sp=0x").append(hex16(machine.cpu().registers().sp()))
                .append(" af=0x").append(hex16(machine.cpu().registers().af()))
                .append(" bc=0x").append(hex16(machine.cpu().registers().bc()))
                .append(" de=0x").append(hex16(machine.cpu().registers().de()))
                .append(" hl=0x").append(hex16(machine.cpu().registers().hl()))
                .append(" inte=").append(machine.board().cpuInteOutputHigh())
                .append(" shadow=").append(machine.board().memory().bootShadowEnabled())
                .append('\n');
        for (int row = 0; row < driver.visibleLines().length; row++) {
            message.append("%02d|%s%n".formatted(row, driver.visibleLines()[row]));
        }
        return message.toString();
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }
}
