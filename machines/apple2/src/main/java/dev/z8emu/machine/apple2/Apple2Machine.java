package dev.z8emu.machine.apple2;

import dev.z8emu.cpu.mos6502.Mos6502Cpu;
import dev.z8emu.machine.apple2.disk.Apple2Disk2Controller;
import dev.z8emu.machine.apple2.disk.Apple2BlockDevice;
import dev.z8emu.machine.apple2.disk.Apple2DosDiskImage;
import dev.z8emu.machine.apple2.disk.Apple2ProDosBlockShimController;
import dev.z8emu.machine.apple2.disk.Apple2SuperDriveController;
import dev.z8emu.machine.apple2.disk.Apple2WozDiskImage;
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
        this(Apple2ModelConfig.appleIIPlus(), initialMemoryImage, systemRomImage);
    }

    public Apple2Machine(Apple2ModelConfig modelConfig, byte[] initialMemoryImage, byte[] systemRomImage) {
        TStateCounter clock = new TStateCounter();
        this.board = new Apple2Board(
                Objects.requireNonNull(modelConfig, "modelConfig"),
                new Apple2Memory(initialMemoryImage, systemRomImage),
                clock
        );
        this.cpu = new Mos6502Cpu(board.cpuBus(), modelConfig.cpuVariant());
        this.runtime = new MachineRuntime(cpu, board, clock);
        this.runtime.reset();
    }

    public static Apple2Machine withBlankMemory() {
        return new Apple2Machine();
    }

    public static Apple2Machine fromLaunchImage(byte[] image) {
        return fromLaunchImage(Apple2ModelConfig.appleIIPlus(), image);
    }

    public static Apple2Machine fromLaunchImage(Apple2ModelConfig modelConfig, byte[] image) {
        Objects.requireNonNull(modelConfig, "modelConfig");
        Objects.requireNonNull(image, "image");
        if (image.length == Apple2Memory.ADDRESS_SPACE_SIZE) {
            return new Apple2Machine(modelConfig, image, new byte[0]);
        }
        if (modelConfig.supportsSystemRomSize(image.length)) {
            return new Apple2Machine(modelConfig, new byte[0], image);
        }
        throw new IllegalArgumentException("Apple II launch image is not supported by %s: %d bytes"
                .formatted(modelConfig.modelName(), image.length));
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

    public void insertDisk(Apple2WozDiskImage diskImage) {
        board.disk2Controller().insertDisk(diskImage);
    }

    public void installProDosBlockShim(Apple2BlockDevice blockDevice) {
        board.installProDosBlockShim(blockDevice);
    }

    public Apple2SuperDriveController installSuperDrive35Controller(int slot, byte[] controllerRom) {
        return board.installSuperDrive35Controller(slot, controllerRom);
    }

    public void bootProDosBlockShimFromSlot6(Apple2BlockDevice blockDevice) {
        installProDosBlockShim(blockDevice);
        byte[] block0 = blockDevice.readBlock(0);
        byte[] block1 = blockDevice.readBlock(1);
        byte[] bootProgram = new byte[block0.length + block1.length];
        System.arraycopy(block0, 0, bootProgram, 0, block0.length);
        System.arraycopy(block1, 0, bootProgram, block0.length, block1.length);
        loadProgram(bootProgram, 0x0800);
        cpu.registers().setPc(0x0801);
        cpu.registers().setX(Apple2ProDosBlockShimController.SLOT_INDEX);
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
