package dev.z8emu.platform.machine;

import dev.z8emu.platform.cpu.Cpu;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class MachineRuntime implements Machine {
    private final Cpu cpu;
    private final MachineBoard board;
    private final TStateCounter clock;

    public MachineRuntime(Cpu cpu, MachineBoard board, TStateCounter clock) {
        this.cpu = Objects.requireNonNull(cpu, "cpu");
        this.board = Objects.requireNonNull(board, "board");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void reset() {
        clock.reset();
        board.reset();
        cpu.reset();
    }

    @Override
    public int runInstruction() {
        int tStates = cpu.runInstruction();
        if (tStates < 0) {
            throw new IllegalStateException("Instruction returned a negative t-state count");
        }

        clock.advance(tStates);
        board.onTStatesElapsed(tStates, clock.value());
        if (board.consumeMaskableInterrupt()) {
            cpu.requestMaskableInterrupt();
        }
        return tStates;
    }

    @Override
    public long currentTState() {
        return clock.value();
    }
}
