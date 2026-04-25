package dev.z8emu.machine.spectrum128k;

import dev.z8emu.machine.spectrum48k.tape.TapeLoaders;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RobocopSideARegressionTest {
    private static final int BROKEN_SYSTEM_MENU_FRAME_HASH = 1_214_964_145;
    private static final int TAPE_LOADER_MENU_PC = 0x3685;
    private static final long MENU_TIMEOUT_TSTATES = 20_000_000L;
    private static final long POST_EOF_TSTATES = 100_000_000L;
    private static final int ENTER_ROW = 6;
    private static final int ENTER_COLUMN = 0;
    private static final int KEY_1_ROW = 3;
    private static final int KEY_1_COLUMN = 0;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void choosingKeyboardControlsAfterRobocopSideALoadDoesNotDropToSystemMenu() throws Exception {
        Path projectRoot = findProjectRoot();
        Spectrum128Machine machine = new Spectrum128Machine(Files.readAllBytes(projectRoot.resolve("128.rom")));
        machine.board().tape().load(TapeLoaders.load(projectRoot.resolve("RobocopA.tzx")));

        waitForPc(machine, TAPE_LOADER_MENU_PC, MENU_TIMEOUT_TSTATES);
        pressKey(machine, ENTER_ROW, ENTER_COLUMN, 12);
        machine.board().tape().play();

        runUntilTapeEof(machine);
        runUntilTState(machine, machine.currentTState() + POST_EOF_TSTATES);

        int beforeSelectionHash = frameHash(machine);
        assertNotEquals(
                BROKEN_SYSTEM_MENU_FRAME_HASH,
                beforeSelectionHash,
                "Scenario never reached the post-load control-selection screen"
        );

        pressKey(machine, KEY_1_ROW, KEY_1_COLUMN, 3);

        int afterSelectionHash = frameHash(machine);
        assertNotEquals(
                BROKEN_SYSTEM_MENU_FRAME_HASH,
                afterSelectionHash,
                "Selecting keyboard controls on Robocop side A currently drops to the broken system-menu screen; "
                        + "pc=0x" + hex16(machine.cpu().registers().pc())
                        + " t=" + machine.currentTState()
                        + " frameHash=" + afterSelectionHash
        );
    }

    private static Path findProjectRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("128.rom"))
                    && Files.isRegularFile(current.resolve("RobocopA.tzx"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("Could not locate project root with 128.rom and RobocopA.tzx");
    }

    private static void waitForPc(Spectrum128Machine machine, int targetPc, long maxTStates) {
        while (machine.currentTState() < maxTStates) {
            if (machine.cpu().registers().pc() == targetPc) {
                return;
            }
            machine.runInstruction();
        }
        throw new IllegalStateException("Timed out waiting for PC=0x" + hex16(targetPc));
    }

    private static void runUntilTapeEof(Spectrum128Machine machine) {
        while (!machine.board().tape().isAtEnd()) {
            machine.runInstruction();
        }
    }

    private static void runUntilTState(Spectrum128Machine machine, long targetTState) {
        while (machine.currentTState() < targetTState) {
            machine.runInstruction();
        }
    }

    private static void pressKey(Spectrum128Machine machine, int row, int column, int frames) {
        machine.board().keyboard().setKeyPressed(row, column, true);
        runFrames(machine, frames);
        machine.board().keyboard().setKeyPressed(row, column, false);
        runFrames(machine, frames);
    }

    private static void runFrames(Spectrum128Machine machine, int frames) {
        long frameTStates = machine.board().modelConfig().frameTStates();
        for (int i = 0; i < frames; i++) {
            runUntilTState(machine, machine.currentTState() + frameTStates);
        }
    }

    private static int frameHash(Spectrum128Machine machine) {
        return Arrays.hashCode(machine.board().renderVideoFrame().pixels());
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }
}
