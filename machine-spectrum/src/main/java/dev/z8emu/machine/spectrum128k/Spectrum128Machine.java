package dev.z8emu.machine.spectrum128k;

import dev.z8emu.cpu.z80.Z80Cpu;
import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.machine.MachineRuntime;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Arrays;

public final class Spectrum128Machine implements SpectrumMachine {
    public static final int ROM_BANK_SIZE = Spectrum48kMemoryMap.ROM_SIZE;
    public static final int ROM_IMAGE_SIZE = ROM_BANK_SIZE * 2;

    private final Spectrum128Board board;
    private final Z80Cpu cpu;
    private final MachineRuntime runtime;

    public Spectrum128Machine(byte[] combinedRomImage) {
        this(splitRomBank(combinedRomImage, 0), splitRomBank(combinedRomImage, 1));
    }

    public Spectrum128Machine(byte[] rom0Image, byte[] rom1Image) {
        TStateCounter clock = new TStateCounter();
        this.board = new Spectrum128Board(rom0Image, rom1Image, clock);
        this.cpu = new Z80Cpu(board.cpuBus());
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
    public Spectrum128Board board() {
        return board;
    }

    @Override
    public Z80Cpu cpu() {
        return cpu;
    }

    private static byte[] splitRomBank(byte[] combinedRomImage, int bankIndex) {
        if (combinedRomImage.length != ROM_IMAGE_SIZE) {
            throw new IllegalArgumentException("Spectrum 128 ROM image must be exactly 32 KB");
        }
        int start = bankIndex * ROM_BANK_SIZE;
        return Arrays.copyOfRange(combinedRomImage, start, start + ROM_BANK_SIZE);
    }
}
