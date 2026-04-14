package dev.z8emu.app.desktop;

import dev.z8emu.machine.radio86rk.tape.Radio86TapeFile;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;

record DesktopLaunchConfig(
        String sourceLabel,
        byte[] romImage,
        boolean demoMode,
        LoadedSpectrumTape loadedTape,
        LoadedRadioTape loadedRadioTape,
        DesktopMachineKind machineKind
) {
    record LoadedSpectrumTape(String sourceLabel, TapeFile tapeFile) {
    }

    record LoadedRadioTape(String sourceLabel, Radio86TapeFile tapeFile) {
    }
}
