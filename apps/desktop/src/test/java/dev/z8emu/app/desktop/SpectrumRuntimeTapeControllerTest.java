package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.tape.TapeBlock;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumRuntimeTapeControllerTest {
    private static final long CPU_CLOCK_HZ = 3_500_000L;

    @Test
    void runtimeReplacementIsQueuedAndRunsOnTheDrainingThread() throws Exception {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(tapeWithBlocks(1));
        tape.play();
        AtomicReference<Thread> mutationThread = new AtomicReference<>();
        List<String> errors = new ArrayList<>();
        SpectrumRuntimeTapeController controller = new SpectrumRuntimeTapeController(
                tape,
                "/old/side-a.tap",
                () -> mutationThread.set(Thread.currentThread()),
                errors::add
        );

        controller.replace(new SpectrumTapeFiles.LoadedTape(
                Path.of("side-b.tzx"),
                tapeWithBlocks(2)
        ));

        assertEquals(1, tape.totalBlocks(), "AWT-facing action must not mutate the tape eagerly");
        Thread emulationThread = new Thread(controller::processPending, "test-spectrum-emulation");
        emulationThread.start();
        emulationThread.join();

        assertSame(emulationThread, mutationThread.get());
        assertEquals(2, tape.totalBlocks());
        assertTrue(tape.isPlaying(), "replacing a playing side should resume the new side");
        assertTrue(controller.statusText().startsWith("side-b.tzx:play:1/2:"));
        assertTrue(errors.isEmpty());
    }

    @Test
    void ejectCancelsAutomationAndClearsLiveMediaStatus() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(tapeWithBlocks(1));
        tape.play();
        int[] cancelCount = {0};
        SpectrumRuntimeTapeController controller = new SpectrumRuntimeTapeController(
                tape,
                "game.tap",
                () -> cancelCount[0]++,
                failure -> {
                    throw new AssertionError(failure);
                }
        );

        controller.eject();
        assertTrue(tape.isLoaded());

        controller.processPending();

        assertEquals(1, cancelCount[0]);
        assertFalse(tape.isLoaded());
        assertFalse(tape.isPlaying());
        assertFalse(tape.earHigh());
        assertEquals("none", controller.statusText());
    }

    @Test
    void previousAndNextBlockRespectBoundariesAndStopPlayback() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(tapeWithBlocks(3));
        SpectrumRuntimeTapeController controller = controller(tape);

        controller.previousBlock();
        controller.processPending();
        assertEquals(0, tape.blockPosition());

        tape.play();
        controller.nextBlock();
        controller.processPending();
        assertEquals(1, tape.blockPosition());
        assertFalse(tape.isPlaying());
        assertTrue(controller.statusText().contains(":stop:2/3:"));

        controller.nextBlock();
        controller.nextBlock();
        controller.processPending();
        assertEquals(2, tape.blockPosition());

        tape.play();
        tape.syncToTState(10);
        assertTrue(tape.isAtEnd());
        controller.previousBlock();
        controller.processPending();
        assertEquals(2, tape.blockPosition(), "previous from EOF should select the last block");
        assertFalse(tape.isAtEnd());
    }

    @Test
    void playPauseAtEofRestartsTheTapeWithoutResettingMachineTime() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(tapeWithBlocks(1));
        tape.play();
        tape.syncToTState(10);
        assertTrue(tape.isAtEnd());

        SpectrumRuntimeTapeController controller = controller(tape);
        controller.togglePlayback();

        assertTrue(tape.isAtEnd(), "AWT-facing action must remain queued");
        controller.processPending();

        assertTrue(tape.isPlaying());
        assertEquals(0, tape.blockPosition());
        assertTrue(controller.statusText().contains(":play:1/1:"));

        tape.syncToTState(19);
        assertTrue(tape.isPlaying(), "replay must start at the current absolute machine time");
        tape.syncToTState(20);
        assertTrue(tape.isAtEnd());
    }

    @Test
    void explicitBadBlockSelectionReportsAnErrorWithoutAUiDialog() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(tapeWithBlocks(2));
        List<String> errors = new ArrayList<>();
        SpectrumRuntimeTapeController controller = new SpectrumRuntimeTapeController(
                tape,
                "game.tap",
                () -> {
                },
                errors::add
        );

        controller.selectBlock(2);
        controller.processPending();

        assertEquals(0, tape.blockPosition());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("out of range"));
    }

    private static SpectrumRuntimeTapeController controller(TapeDevice tape) {
        return new SpectrumRuntimeTapeController(
                tape,
                "game.tap",
                () -> {
                },
                failure -> {
                    throw new AssertionError(failure);
                }
        );
    }

    private static TapeFile tapeWithBlocks(int count) {
        List<TapeBlock> blocks = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            blocks.add(TapeBlock.dataBlock(new int[]{10}, 0, 0, 0, 0, new byte[0]));
        }
        return new TapeFile(List.copyOf(blocks));
    }
}
