package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumTapeAutostartSupportTest {
    @Test
    void recognizes48kLoaderPlaybackWindow() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();

        machine.cpu().registers().setPc(0x05E7);

        assertTrue(SpectrumTapeAutostartSupport.isLoaderReadyForPlayback(machine));
    }

    @Test
    void rejectsUnrelated48kProgramCounter() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();

        machine.cpu().registers().setPc(0x1234);

        assertFalse(SpectrumTapeAutostartSupport.isLoaderReadyForPlayback(machine));
    }

    @Test
    void requires128kTapeRomBeforeAutoplayWindowIsActive() {
        Spectrum128Machine machine = new Spectrum128Machine(new byte[Spectrum128Machine.ROM_IMAGE_SIZE]);

        machine.cpu().registers().setPc(0x05E7);
        assertFalse(SpectrumTapeAutostartSupport.isLoaderReadyForPlayback(machine));

        machine.board().machineState().setSelectedRomIndex(1);
        machine.board().memory().applyState();

        assertTrue(SpectrumTapeAutostartSupport.isLoaderReadyForPlayback(machine));
    }
}
