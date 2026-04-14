package dev.z8emu.machine.spectrum48k;

import dev.z8emu.cpu.z80.Z80Cpu;
import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.machine.MachineRuntime;
import dev.z8emu.platform.time.TStateCounter;

public final class Spectrum48kMachine implements SpectrumMachine {
    private final Spectrum48kBoard board;
    private final Z80Cpu cpu;
    private final MachineRuntime runtime;

    public Spectrum48kMachine(byte[] romImage) {
        TStateCounter clock = new TStateCounter();
        this.board = new Spectrum48kBoard(romImage, clock);
        this.cpu = new Z80Cpu(board.cpuBus());
        this.runtime = new MachineRuntime(cpu, board, clock);
        this.runtime.reset();
    }

    public static Spectrum48kMachine withBlankRom() {
        return new Spectrum48kMachine(new byte[Spectrum48kMemoryMap.ROM_SIZE]);
    }

    @Override
    public void reset() {
        runtime.reset();
    }

    @Override
    public int runInstruction() {
        return runtime.runInstruction();
    }

    @Override
    public long currentTState() {
        return runtime.currentTState();
    }

    public Spectrum48kBoard board() {
        return board;
    }

    public Z80Cpu cpu() {
        return cpu;
    }
}
