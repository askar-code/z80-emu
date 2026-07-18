package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;

final class SpectrumTapeAutostartSupport {
    static final int TAPE_LOADER_MENU_PC = 0x3685;
    static final int WAIT_KEY_1 = 0x15DE;
    static final int WAIT_KEY_2 = 0x15E6;
    static final int LD_BYTES = 0x0556;
    static final int LD_EDGE_2 = 0x05E3;
    static final int LD_EDGE_1 = 0x05E7;
    static final int LD_SAMPLE = 0x05ED;

    private static final int[] LOADER_PLAYBACK_PCS = {LD_BYTES, LD_EDGE_2, LD_EDGE_1, LD_SAMPLE};

    private SpectrumTapeAutostartSupport() {
    }

    static boolean isBootPromptReadyForAutostart(SpectrumMachine machine) {
        int pc = machine.cpu().registers().pc();
        if (machine.board().modelConfig().pagingSupported()) {
            return machine.board().machineState().selectedRomIndex() == 0
                    && pc == TAPE_LOADER_MENU_PC;
        }
        return pc == WAIT_KEY_1 || pc == WAIT_KEY_2;
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

}
