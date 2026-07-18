package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum48k.tape.TapeLoaders;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("external-media")
class GothikMultipassRegressionTest {
    private static final long BOOT_TIMEOUT_TSTATES = 20_000_000L;
    private static final long TAPE_PASS_TIMEOUT_TSTATES = 3_200_000_000L;
    private static final int TOTAL_BLOCKS = 154;
    private static final int POST_PASS_FRAMES = 120;
    private static final int REWIND_PROMPT_PC = 0xCE0E;
    private static final long REWIND_PROMPT_FRAME_CRC = 0x9C77B8C9L;
    private static final int TITLE_SCREEN_PC = 0xB436;
    private static final long TITLE_SCREEN_FRAME_CRC = 0x97CEB405L;
    private static final int TZX_TITLE_SCREEN_PC = 0xB3EA;
    private static final int GAMEPLAY_PC = 0xA0D1;
    private static final long GAMEPLAY_FRAME_CRC = 0xB64B9264L;
    private static final int TZX_GAMEPLAY_PC = 0xA489;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void rewindAndSecondTapePassReachTitleAndGameplay() throws Exception {
        Path projectRoot = findProjectRoot();
        Path romPath = projectRoot.resolve("media/48.rom");
        Path tapePath = projectRoot.resolve("media/GOTHIK.TAP");
        assumeTrue(
                Files.isRegularFile(romPath) && Files.isRegularFile(tapePath),
                "Optional GOTHIK regression requires media/48.rom and media/GOTHIK.TAP"
        );

        Spectrum48kMachine machine = new Spectrum48kMachine(Files.readAllBytes(romPath));
        machine.board().tape().load(TapeLoaders.load(tapePath));
        enterLoadCommand(machine);
        waitForRomLoader(machine, BOOT_TIMEOUT_TSTATES);
        machine.board().tape().play();

        runTapePass(machine, "initial pass");
        assertStage(machine, "rewind prompt", REWIND_PROMPT_PC, REWIND_PROMPT_FRAME_CRC);
        assertTrue(machine.board().tape().isAtEnd());
        assertEquals(TOTAL_BLOCKS, machine.board().tape().currentBlockIndex());

        machine.board().tape().rewind();
        machine.board().tape().play();
        runTapePass(machine, "replayed pass");
        assertStage(machine, "title screen", TITLE_SCREEN_PC, TITLE_SCREEN_FRAME_CRC);

        press(machine, new int[][]{{6, 0}}, 12, 12);
        runFrames(machine, POST_PASS_FRAMES);
        assertStage(machine, "gameplay", GAMEPLAY_PC, GAMEPLAY_FRAME_CRC);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void tzxPreservesTheInterStageGapAndReachesTheTitleInOnePass() throws Exception {
        Path projectRoot = findProjectRoot();
        Path romPath = projectRoot.resolve("media/48.rom");
        Path tapePath = projectRoot.resolve("media/GOTHIK.TZX");
        assumeTrue(
                Files.isRegularFile(romPath) && Files.isRegularFile(tapePath),
                "Optional GOTHIK regression requires media/48.rom and media/GOTHIK.TZX"
        );

        Spectrum48kMachine machine = new Spectrum48kMachine(Files.readAllBytes(romPath));
        machine.board().tape().load(TapeLoaders.load(tapePath));
        enterLoadCommand(machine);
        waitForRomLoader(machine, BOOT_TIMEOUT_TSTATES);
        machine.board().tape().play();

        runTapePass(machine, "TZX pass");
        assertStage(machine, "TZX title screen", TZX_TITLE_SCREEN_PC, TITLE_SCREEN_FRAME_CRC);

        press(machine, new int[][]{{6, 0}}, 12, 12);
        runFrames(machine, POST_PASS_FRAMES);
        assertStage(machine, "TZX gameplay", TZX_GAMEPLAY_PC, GAMEPLAY_FRAME_CRC);
    }

    private static void runTapePass(Spectrum48kMachine machine, String label) {
        long deadline = machine.currentTState() + TAPE_PASS_TIMEOUT_TSTATES;
        while (!machine.board().tape().isAtEnd() && machine.currentTState() < deadline) {
            machine.runInstruction();
        }
        if (!machine.board().tape().isAtEnd()) {
            throw new IllegalStateException("Timed out during GOTHIK " + label + ": " + status(machine));
        }
        runFrames(machine, POST_PASS_FRAMES);
    }

    private static void enterLoadCommand(Spectrum48kMachine machine) {
        waitForPc(machine, new int[]{0x15DE, 0x15E6}, BOOT_TIMEOUT_TSTATES, "48K BASIC prompt");
        press(machine, new int[][]{{6, 3}}, 4, 6);
        press(machine, new int[][]{{7, 1}, {5, 0}}, 4, 6);
        press(machine, new int[][]{{7, 1}, {5, 0}}, 4, 6);
        press(machine, new int[][]{{6, 0}}, 4, 6);
    }

    private static void waitForRomLoader(Spectrum48kMachine machine, long timeoutTStates) {
        waitForPc(machine, new int[]{0x0556, 0x05E3, 0x05E7, 0x05ED}, timeoutTStates, "48K ROM tape loader");
    }

    private static void waitForPc(
            Spectrum48kMachine machine,
            int[] targetPcs,
            long timeoutTStates,
            String label
    ) {
        long deadline = machine.currentTState() + timeoutTStates;
        while (machine.currentTState() < deadline) {
            int pc = machine.cpu().registers().pc();
            for (int targetPc : targetPcs) {
                if (pc == targetPc) {
                    return;
                }
            }
            machine.runInstruction();
        }
        throw new IllegalStateException(
                "Timed out waiting for " + label + "; pc=0x"
                        + Integer.toHexString(machine.cpu().registers().pc()).toUpperCase()
        );
    }

    private static void press(
            Spectrum48kMachine machine,
            int[][] chord,
            int pressedFrames,
            int releasedFrames
    ) {
        for (int[] key : chord) {
            machine.board().keyboard().setKeyPressed(key[0], key[1], true);
        }
        runFrames(machine, pressedFrames);
        for (int[] key : chord) {
            machine.board().keyboard().setKeyPressed(key[0], key[1], false);
        }
        runFrames(machine, releasedFrames);
    }

    private static void runFrames(Spectrum48kMachine machine, int frameCount) {
        long target = machine.currentTState()
                + (frameCount * (long) machine.board().modelConfig().frameTStates());
        while (machine.currentTState() < target) {
            machine.runInstruction();
        }
    }

    private static void assertStage(
            Spectrum48kMachine machine,
            String label,
            int expectedPc,
            long expectedFrameCrc
    ) {
        assertEquals(expectedPc, machine.cpu().registers().pc(), label + " PC; " + status(machine));
        assertEquals(expectedFrameCrc, frameCrc32(machine), label + " frame; " + status(machine));
    }

    private static String status(Spectrum48kMachine machine) {
        return "pc=0x" + Integer.toHexString(machine.cpu().registers().pc()).toUpperCase()
                + " t=" + machine.currentTState()
                + " block=" + machine.board().tape().currentBlockIndex()
                + "/" + machine.board().tape().totalBlocks()
                + " frameCrc32=0x" + Long.toHexString(frameCrc32(machine)).toUpperCase();
    }

    private static long frameCrc32(Spectrum48kMachine machine) {
        CRC32 crc32 = new CRC32();
        for (int pixel : machine.board().renderVideoFrame().pixels()) {
            crc32.update((pixel >>> 24) & 0xFF);
            crc32.update((pixel >>> 16) & 0xFF);
            crc32.update((pixel >>> 8) & 0xFF);
            crc32.update(pixel & 0xFF);
        }
        return crc32.getValue();
    }

    private static Path findProjectRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("Could not locate project root containing settings.gradle.kts");
    }
}
