package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;

final class SpectrumTapeAutostartSupport {
    private static final int[] LOADER_PLAYBACK_PCS = {0x0556, 0x05E3, 0x05E7, 0x05ED};

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

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }
}
