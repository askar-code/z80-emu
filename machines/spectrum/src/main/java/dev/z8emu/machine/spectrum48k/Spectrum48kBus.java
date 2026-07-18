package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum.SpectrumBusBase;
import dev.z8emu.machine.spectrum.model.SpectrumModelConfig;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.KempstonJoystickDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.time.TStateCounter;

public final class Spectrum48kBus extends SpectrumBusBase {
    public Spectrum48kBus(
            TStateCounter clock,
            Spectrum48kMemoryMap memory,
            SpectrumUlaDevice ula,
            KeyboardMatrixDevice keyboard,
            KempstonJoystickDevice kempstonJoystick,
            BeeperDevice beeper,
            TapeDevice tape,
            SpectrumModelConfig modelConfig
    ) {
        super(
                clock,
                memory,
                ula,
                keyboard,
                kempstonJoystick,
                beeper,
                tape,
                modelConfig.frameTStates(),
                modelConfig.contentionStartTState(),
                modelConfig.tStatesPerScanline()
        );
    }
}
