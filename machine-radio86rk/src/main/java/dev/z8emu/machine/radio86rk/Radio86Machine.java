package dev.z8emu.machine.radio86rk;

import dev.z8emu.cpu.i8080.I8080Cpu;
import dev.z8emu.machine.radio86rk.device.Radio86VideoDevice;
import dev.z8emu.platform.machine.BoardBackedMachine;
import dev.z8emu.platform.machine.MachineRuntime;
import dev.z8emu.platform.time.TStateCounter;

public final class Radio86Machine implements BoardBackedMachine<Radio86Board> {
    public static final long CPU_CLOCK_HZ = 16_000_000L / 9L;
    public static final int FRAME_T_STATES = Radio86VideoDevice.T_STATES_PER_FRAME;

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
}
