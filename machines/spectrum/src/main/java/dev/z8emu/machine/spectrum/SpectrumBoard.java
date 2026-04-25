package dev.z8emu.machine.spectrum;

import dev.z8emu.machine.spectrum.model.SpectrumMachineState;
import dev.z8emu.machine.spectrum.model.SpectrumModelConfig;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.machine.VideoMachineBoard;

public interface SpectrumBoard extends VideoMachineBoard {
    KeyboardMatrixDevice keyboard();

    BeeperDevice beeper();

    PcmMonoSource audio();

    SpectrumUlaDevice ula();

    TapeDevice tape();

    Spectrum48kMemoryMap memory();

    SpectrumModelConfig modelConfig();

    SpectrumMachineState machineState();
}
