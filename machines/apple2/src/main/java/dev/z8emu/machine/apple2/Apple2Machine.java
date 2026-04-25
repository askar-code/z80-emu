package dev.z8emu.machine.apple2;

import dev.z8emu.cpu.mos6502.Mos6502Cpu;
import dev.z8emu.platform.machine.BoardBackedMachine;
import dev.z8emu.platform.machine.MachineRuntime;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Apple2Machine implements BoardBackedMachine<Apple2Board> {
    private final Apple2Board board;
    private final Mos6502Cpu cpu;
    private final MachineRuntime runtime;

    public Apple2Machine() {
        this(new byte[0]);
    }

    public Apple2Machine(byte[] initialMemoryImage) {
        this(initialMemoryImage, new byte[0]);
    }

    public Apple2Machine(byte[] initialMemoryImage, byte[] systemRomImage) {
        TStateCounter clock = new TStateCounter();
        this.board = new Apple2Board(
                Apple2ModelConfig.appleIIPlus(),
                new Apple2Memory(initialMemoryImage, systemRomImage),
                clock
        );
        this.cpu = new Mos6502Cpu(board.cpuBus());
        this.runtime = new MachineRuntime(cpu, board, clock);
        this.runtime.reset();
    }

    public static Apple2Machine withBlankMemory() {
        return new Apple2Machine();
    }

    public static Apple2Machine fromLaunchImage(byte[] image) {
        Objects.requireNonNull(image, "image");
        if (image.length == Apple2Memory.ADDRESS_SPACE_SIZE) {
            return new Apple2Machine(image);
        }
        if (Apple2Memory.isSupportedSystemRomSize(image.length)) {
            return new Apple2Machine(new byte[0], image);
        }
        throw new IllegalArgumentException("Apple II launch image must be 4 KB, 8 KB, 12 KB, or 64 KB");
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
    public Apple2Board board() {
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
