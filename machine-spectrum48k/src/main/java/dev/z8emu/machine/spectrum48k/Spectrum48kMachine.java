package dev.z8emu.machine.spectrum48k;

import dev.z8emu.cpu.z80.Z80Cpu;
import dev.z8emu.cpu.z80.Z80Registers;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.machine.Machine;
import dev.z8emu.platform.machine.MachineRuntime;
import dev.z8emu.platform.time.TStateCounter;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public final class Spectrum48kMachine implements Machine {
    private static final int LD_BYTES_ENTRY = 0x0556;
    private static final int BORDCR = 0x5C48;
    private static final int FAST_LOAD_LOG_CAPACITY = 128;

    private final Spectrum48kBoard board;
    private final Z80Cpu cpu;
    private final MachineRuntime runtime;
    private final Queue<FastLoadLogEntry> fastLoadLog = new ArrayDeque<>(FAST_LOAD_LOG_CAPACITY);
    private boolean fastLoadEnabled = true;

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
        if (fastLoadEnabled && tryFastLoadTrap()) {
            return 1;
        }
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

    public void setFastLoadEnabled(boolean fastLoadEnabled) {
        this.fastLoadEnabled = fastLoadEnabled;
    }

    public List<FastLoadLogEntry> fastLoadLog() {
        return List.copyOf(fastLoadLog);
    }

    private boolean tryFastLoadTrap() {
        if (cpu.registers().pc() != LD_BYTES_ENTRY || !board.tape().isLoaded()) {
            return false;
        }

        byte[] block = board.tape().currentBlockData();
        if (block == null) {
            cpu.registers().setFlag(Z80Registers.FLAG_C, false);
            finalizeFastLoadReturn();
            logFastLoad(-1, cpu.registers().ix(), cpu.registers().de(), cpu.registers().a(), cpu.registers().flagSet(Z80Registers.FLAG_C), false, "no-block");
            return true;
        }

        int expectedFlag = cpu.registers().a() & 0xFF;
        int requestedLength = cpu.registers().de() & 0xFFFF;
        boolean load = cpu.registers().flagSet(Z80Registers.FLAG_C);
        int blockNumber = board.tape().currentBlockIndex();
        int destination = cpu.registers().ix();

        if (block.length < requestedLength + 2 || (block[0] & 0xFF) != expectedFlag || !validChecksum(block, requestedLength + 2)) {
            cpu.registers().setFlag(Z80Registers.FLAG_C, false);
            finalizeFastLoadReturn();
            board.tape().advanceToNextBlock();
            logFastLoad(blockNumber, destination, requestedLength, expectedFlag, load, false, "header-or-checksum");
            return true;
        }

        for (int i = 0; i < requestedLength; i++) {
            int value = block[i + 1] & 0xFF;
            if (load) {
                board.memory().write(destination + i, value);
            } else if (board.memory().read(destination + i) != value) {
                cpu.registers().setFlag(Z80Registers.FLAG_C, false);
                finalizeFastLoadReturn();
                board.tape().advanceToNextBlock();
                logFastLoad(blockNumber, destination, requestedLength, expectedFlag, load, false, "verify-mismatch");
                return true;
            }
        }

        cpu.registers().setIx(destination + requestedLength);
        cpu.registers().setDe(0);
        cpu.registers().setFlag(Z80Registers.FLAG_C, true);
        finalizeFastLoadReturn();
        board.tape().advanceToNextBlock();
        logFastLoad(blockNumber, destination, requestedLength, expectedFlag, load, true, "ok");
        return true;
    }

    private void finalizeFastLoadReturn() {
        restoreBorderFromSystemVariable();
        cpu.registers().setIff1(true);
        cpu.registers().setIff2(true);

        int sp = cpu.registers().sp();
        int returnAddress = board.memory().read(sp) | (board.memory().read(sp + 1) << 8);
        cpu.registers().setSp(sp + 2);
        cpu.registers().setPc(returnAddress);
    }

    private void restoreBorderFromSystemVariable() {
        int bordcr = board.memory().read(BORDCR);
        int border = (bordcr & 0x38) >>> 3;
        board.ula().writePortFe(border, board.beeper());
    }

    private boolean validChecksum(byte[] block, int length) {
        int parity = 0;
        for (int i = 0; i < length; i++) {
            parity ^= block[i] & 0xFF;
        }
        return parity == 0;
    }

    private void logFastLoad(int blockNumber, int destination, int length, int flag, boolean load, boolean success, String reason) {
        if (fastLoadLog.size() == FAST_LOAD_LOG_CAPACITY) {
            fastLoadLog.remove();
        }
        fastLoadLog.add(new FastLoadLogEntry(blockNumber, destination & 0xFFFF, length & 0xFFFF, flag & 0xFF, load, success, reason));
    }

    public record FastLoadLogEntry(
            int blockNumber,
            int destination,
            int length,
            int flag,
            boolean load,
            boolean success,
            String reason
    ) {
    }
}
