package dev.z8emu.machine.cpc;

import dev.z8emu.cpu.z80.Z80Cpu;
import dev.z8emu.machine.cpc.disk.CpcDskImage;
import dev.z8emu.machine.cpc.memory.CpcMemory;
import dev.z8emu.platform.machine.BoardBackedMachine;
import dev.z8emu.platform.machine.MachineRuntime;
import dev.z8emu.platform.time.TStateCounter;

public final class CpcMachine implements BoardBackedMachine<CpcBoard> {
    private final CpcBoard board;
    private final Z80Cpu cpu;
    private final MachineRuntime runtime;

    public CpcMachine(byte[] combinedRomImage) {
        this(combinedRomImage, null);
    }

    public CpcMachine(byte[] combinedRomImage, CpcDskImage disk) {
        TStateCounter clock = new TStateCounter();
        this.board = new CpcBoard(combinedRomImage, disk, clock);
        this.cpu = new Z80Cpu(board.cpuBus());
        this.runtime = new MachineRuntime(cpu, board, clock);
        this.runtime.reset();
    }

    public static CpcMachine withBlankRom() {
        return new CpcMachine(new byte[CpcMemory.ROM_SIZE]);
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

    @Override
    public CpcBoard board() {
        return board;
    }

    public Z80Cpu cpu() {
        return cpu;
    }

    public long cpuClockHz() {
        return board.modelConfig().cpuClockHz();
    }

    public int frameTStates() {
        return board.modelConfig().frameTStates();
    }

    public static boolean isSupportedCombinedRomSize(int length) {
        return CpcMemory.isSupportedCombinedRomSize(length);
    }
}
