package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import dev.z8emu.machine.spectrum48k.tape.TapeBlock;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumStartupTapeAutoplayTest {
    @Test
    void spectrum128WaitsForMenuAndCompletedEnterBeforeStartingTape() {
        Spectrum128Machine machine = new Spectrum128Machine(new byte[Spectrum128Machine.ROM_IMAGE_SIZE]);
        loadTestTape(machine);
        SpectrumDesktopRunner.HostKeyTyper typer = new SpectrumDesktopRunner.HostKeyTyper(machine);
        SpectrumStartupTapeAutoplay autoplay = new SpectrumStartupTapeAutoplay(machine, typer, 2, 1, 0);

        autoplay.armIfNeeded();
        for (int i = 0; i < 20; i++) {
            autoplay.tick();
        }

        assertEquals(SpectrumStartupTapeAutoplay.Phase.WAITING_FOR_BOOT_PROMPT, autoplay.phase());
        assertTrue(typer.isIdle());
        assertFalse(machine.board().tape().isPlaying());

        machine.cpu().registers().setPc(SpectrumTapeAutostartSupport.TAPE_LOADER_MENU_PC);
        autoplay.tick();

        assertEquals(SpectrumStartupTapeAutoplay.Phase.WAITING_FOR_KEYS, autoplay.phase());
        assertFalse(typer.isIdle());

        machine.board().machineState().setSelectedRomIndex(1);
        machine.board().memory().applyState();
        machine.cpu().registers().setPc(SpectrumTapeAutostartSupport.LD_EDGE_1);
        autoplay.tick();
        assertFalse(machine.board().tape().isPlaying(), "loader PC alone must not bypass the queued menu key");

        typer.tick();
        assertTrue(keyPressed(machine, 6, 0), "Enter must be held for the configured menu press");
        autoplay.tick();
        assertFalse(machine.board().tape().isPlaying());

        typer.tick();
        typer.tick();
        assertFalse(keyPressed(machine, 6, 0));
        typer.tick();
        assertTrue(typer.isIdle());

        autoplay.tick();

        assertTrue(machine.board().tape().isPlaying());
        assertFalse(autoplay.pending());
    }

    @Test
    void spectrum48TypesLoadQuotesInOrderBeforeStartingTape() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        loadTestTape(machine);
        SpectrumDesktopRunner.HostKeyTyper typer = new SpectrumDesktopRunner.HostKeyTyper(machine);
        SpectrumStartupTapeAutoplay autoplay = new SpectrumStartupTapeAutoplay(machine, typer);

        autoplay.armIfNeeded();
        machine.cpu().registers().setPc(0x1234);
        for (int i = 0; i < 20; i++) {
            autoplay.tick();
        }
        assertTrue(typer.isIdle());

        machine.cpu().registers().setPc(SpectrumTapeAutostartSupport.WAIT_KEY_1);
        autoplay.tick();
        assertEquals(SpectrumStartupTapeAutoplay.Phase.WAITING_FOR_KEYS, autoplay.phase());

        machine.cpu().registers().setPc(SpectrumTapeAutostartSupport.LD_EDGE_1);
        autoplay.tick();
        assertFalse(machine.board().tape().isPlaying());

        typer.tick();
        assertTrue(keyPressed(machine, 6, 3), "J must enter the 48K LOAD keyword first");
        assertFalse(keyPressed(machine, 6, 0));
        finishCommandChord(typer);

        typer.tick();
        assertTrue(keyPressed(machine, 7, 1), "the first quote must hold Symbol Shift");
        assertTrue(keyPressed(machine, 5, 0), "the first quote must hold P");
        finishCommandChord(typer);

        typer.tick();
        assertTrue(keyPressed(machine, 7, 1), "the second quote must hold Symbol Shift");
        assertTrue(keyPressed(machine, 5, 0), "the second quote must hold P");
        finishCommandChord(typer);

        typer.tick();
        assertTrue(keyPressed(machine, 6, 0), "Enter must be the final command key");
        autoplay.tick();
        assertFalse(machine.board().tape().isPlaying());
        finishCommandChord(typer);
        assertTrue(typer.isIdle());

        autoplay.tick();

        assertTrue(machine.board().tape().isPlaying());
        assertFalse(autoplay.pending());
    }

    @Test
    void clearingAutomationReleasesAnAlreadyPressedHostChord() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        SpectrumDesktopRunner.HostKeyTyper typer = new SpectrumDesktopRunner.HostKeyTyper(machine);
        typer.queueChord(new int[][]{{6, 0}});
        typer.tick();
        assertTrue(keyPressed(machine, 6, 0));

        typer.clear();

        assertFalse(keyPressed(machine, 6, 0));
        assertTrue(typer.isIdle());
    }

    private static void finishCommandChord(SpectrumDesktopRunner.HostKeyTyper typer) {
        for (int i = 0; i < 10; i++) {
            typer.tick();
        }
    }

    private static void loadTestTape(SpectrumMachine machine) {
        machine.board().tape().load(new TapeFile(List.of(
                TapeBlock.dataBlock(new int[]{100}, 0, 0, 0, 0, new byte[0])
        )));
    }

    private static boolean keyPressed(SpectrumMachine machine, int row, int column) {
        int highByte = 0xFF & ~(1 << row);
        int port = (highByte << 8) | 0xFE;
        return (machine.board().keyboard().readSelectedRows(port) & (1 << column)) == 0;
    }
}
