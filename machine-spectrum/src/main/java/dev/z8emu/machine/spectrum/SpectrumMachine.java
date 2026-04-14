package dev.z8emu.machine.spectrum;

import dev.z8emu.cpu.z80.Z80Cpu;
import dev.z8emu.platform.machine.Machine;

public interface SpectrumMachine extends Machine {
    SpectrumBoard board();

    Z80Cpu cpu();
}
