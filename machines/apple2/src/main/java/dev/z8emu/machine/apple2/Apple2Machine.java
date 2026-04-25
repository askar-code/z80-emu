package dev.z8emu.machine.apple2;

import dev.z8emu.cpu.mos6502.Mos6502Cpu;
import dev.z8emu.machine.apple2.disk.Apple2Disk2Controller;
import dev.z8emu.machine.apple2.disk.Apple2DosDiskImage;
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

    public void loadProgram(byte[] programImage, int loadAddress) {
        Objects.requireNonNull(programImage, "programImage");
        if (loadAddress < 0 || loadAddress > 0xFFFF) {
            throw new IllegalArgumentException("Apple II program load address out of range: 0x%X".formatted(loadAddress));
        }
        int normalizedAddress = loadAddress;
        if (programImage.length == 0) {
            return;
        }
        if (programImage.length > Apple2Memory.ADDRESS_SPACE_SIZE - normalizedAddress) {
            throw new IllegalArgumentException("Apple II program does not fit in memory at 0x%04X".formatted(normalizedAddress));
        }
        int endExclusive = normalizedAddress + programImage.length;
        if (board.memory().hasSystemRom() && endExclusive > board.memory().systemRomStart()) {
            throw new IllegalArgumentException(
                    "Apple II program at 0x%04X overlaps system ROM at 0x%04X"
                            .formatted(normalizedAddress, board.memory().systemRomStart())
            );
        }

        for (int i = 0; i < programImage.length; i++) {
            board.memory().write(normalizedAddress + i, Byte.toUnsignedInt(programImage[i]));
        }
    }

    public void setProgramCounter(int address) {
        cpu.registers().setPc(address);
    }

    public void insertDisk(Apple2DosDiskImage diskImage) {
        board.disk2Controller().insertDisk(diskImage);
    }

    public void loadDisk2SlotRom(byte[] slotRom) {
        board.loadDisk2SlotRom(slotRom);
    }

    public void bootDiskFromSlot6() {
        cpu.registers().setPc(Apple2Disk2Controller.SLOT_ROM_START);
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
