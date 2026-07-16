package dev.z8emu.machine.c64;

import dev.z8emu.cpu.mos6502.Mos6502Cpu;
import dev.z8emu.cpu.mos6502.Mos6502Variant;
import dev.z8emu.machine.c64.device.C64EasyFlashCartridge;
import dev.z8emu.platform.machine.BoardBackedMachine;
import dev.z8emu.platform.machine.MachineRuntime;
import dev.z8emu.platform.time.TStateCounter;

public final class C64Machine implements BoardBackedMachine<C64Board> {
    private final C64Board board;
    private final Mos6502Cpu cpu;
    private final MachineRuntime runtime;

    public C64Machine(byte[] basicRom, byte[] kernalRom, byte[] chargenRom) {
        this(C64ModelConfig.pal(), basicRom, kernalRom, chargenRom, null);
    }

    public C64Machine(
            byte[] basicRom,
            byte[] kernalRom,
            byte[] chargenRom,
            C64EasyFlashCartridge cartridge
    ) {
        this(C64ModelConfig.pal(), basicRom, kernalRom, chargenRom, cartridge);
    }

    public C64Machine(
            C64ModelConfig modelConfig,
            byte[] basicRom,
            byte[] kernalRom,
            byte[] chargenRom
    ) {
        this(modelConfig, basicRom, kernalRom, chargenRom, null);
    }

    public C64Machine(
            C64ModelConfig modelConfig,
            byte[] basicRom,
            byte[] kernalRom,
            byte[] chargenRom,
            C64EasyFlashCartridge cartridge
    ) {
        TStateCounter clock = new TStateCounter();
        this.board = new C64Board(
                modelConfig,
                new C64Memory(basicRom, kernalRom, chargenRom),
                clock,
                cartridge
        );
        this.cpu = new Mos6502Cpu(board.cpuBus(), Mos6502Variant.NMOS_6502);
        this.runtime = new MachineRuntime(cpu, board, clock);
        this.runtime.reset();
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
    public C64Board board() {
        return board;
    }

    public Mos6502Cpu cpu() {
        return cpu;
    }

    public long cpuClockHz() {
        return board.modelConfig().cpuClockHz();
    }

    public int frameTStates() {
        return board.modelConfig().frameTStates();
    }
}
