package dev.z8emu.machine.radio86rk;

import dev.z8emu.cpu.i8080.I8080Cpu;
import dev.z8emu.platform.machine.BoardBackedMachine;
import dev.z8emu.platform.machine.MachineRuntime;
import dev.z8emu.platform.time.TStateCounter;

public final class Radio86Machine implements BoardBackedMachine<Radio86Board> {
    private final Radio86Board board;
    private final I8080Cpu cpu;
    private final MachineRuntime runtime;

    public Radio86Machine(byte[] romImage) {
        this(romImage, null);
    }

    public Radio86Machine(byte[] romImage, Radio86Bus.AccessTraceListener traceListener) {
        TStateCounter clock = new TStateCounter();
        this.board = new Radio86Board(romImage, clock, traceListener);
        this.cpu = new I8080Cpu(board.cpuBus());
        this.cpu.setInteOutputListener(board::setCpuInteOutputHigh);
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
    public Radio86Board board() {
        return board;
    }

    public I8080Cpu cpu() {
        return cpu;
    }

    public long cpuClockHz() {
        return board.modelConfig().cpuClockHz();
    }

    public int frameTStates() {
        return board.modelConfig().frameTStates();
    }
}
