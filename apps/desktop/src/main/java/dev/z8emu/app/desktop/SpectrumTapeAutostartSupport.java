package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;

import static dev.z8emu.app.desktop.ProbeOutput.hex16;

final class SpectrumTapeAutostartSupport {
    static final int LD_BYTES = 0x0556;
    static final int LD_EDGE_2 = 0x05E3;
    static final int LD_EDGE_1 = 0x05E7;
    static final int LD_SAMPLE = 0x05ED;

    private static final int[] LOADER_PLAYBACK_PCS = {LD_BYTES, LD_EDGE_2, LD_EDGE_1, LD_SAMPLE};

    private SpectrumTapeAutostartSupport() {
    }

    static boolean isLoaderReadyForPlayback(SpectrumMachine machine) {
        if (machine.board().modelConfig().pagingSupported()
                && machine.board().machineState().selectedRomIndex() != 1) {
            return false;
        }

        int pc = machine.cpu().registers().pc();
        for (int loaderPc : LOADER_PLAYBACK_PCS) {
            if (pc == loaderPc) {
                return true;
            }
        }
        return false;
    }

    static void waitForLoaderReadyForPlayback(SpectrumMachine machine, long deadlineTState) {
        while (machine.currentTState() < deadlineTState) {
            if (isLoaderReadyForPlayback(machine)) {
                return;
            }
            machine.runInstruction();
        }

        throw new IllegalStateException(
                "Timed out waiting for 128K tape loader playback window; "
                        + "pc=0x" + hex16(machine.cpu().registers().pc())
                        + " rom=" + machine.board().machineState().selectedRomIndex()
                        + " t=" + machine.currentTState()
        );
    }
}
