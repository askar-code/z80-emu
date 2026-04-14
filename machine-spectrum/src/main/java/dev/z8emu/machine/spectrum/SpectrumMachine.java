package dev.z8emu.machine.spectrum;

import dev.z8emu.cpu.z80.Z80Cpu;
import dev.z8emu.platform.machine.BoardBackedMachine;

public interface SpectrumMachine extends BoardBackedMachine<SpectrumBoard> {
    SpectrumBoard board();

    Z80Cpu cpu();
}
