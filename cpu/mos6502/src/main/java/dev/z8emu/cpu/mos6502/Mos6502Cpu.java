package dev.z8emu.cpu.mos6502;

import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.cpu.Cpu;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

public final class Mos6502Cpu implements Cpu {
    private static final int RESET_VECTOR = 0xFFFC;
    private static final int IRQ_VECTOR = 0xFFFE;
    private static final int NMI_VECTOR = 0xFFFA;
    private static final int STACK_PAGE = 0x0100;

    private final CpuBus bus;
    private final Mos6502Registers registers = new Mos6502Registers();
    private boolean irqPending;
    private boolean nmiPending;

    public Mos6502Cpu(CpuBus bus) {
        this.bus = Objects.requireNonNull(bus, "bus");
        reset();
    }

    public Mos6502Registers registers() {
        return registers;
    }

    @Override
    public void reset() {
        registers.reset();
        irqPending = false;
        nmiPending = false;
        registers.setPc(readVector(RESET_VECTOR));
    }

    @Override
    public void requestMaskableInterrupt() {
        irqPending = true;
    }

    @Override
    public void clearMaskableInterrupt() {
        irqPending = false;
    }

    @Override
    public void requestNonMaskableInterrupt() {
        nmiPending = true;
    }

    @Override
    public int runInstruction() {
        if (nmiPending) {
            nmiPending = false;
            return serviceInterrupt(NMI_VECTOR, false);
        }
        if (irqPending && !registers.flagSet(Mos6502Registers.FLAG_I)) {
            irqPending = false;
            return serviceInterrupt(IRQ_VECTOR, false);
        }

        int opcodeAddress = registers.pc();
        int opcode = fetchOpcode();
        return executeOpcode(opcode, opcodeAddress);
    }

    private int executeOpcode(int opcode, int opcodeAddress) {
        return switch (opcode & 0xFF) {
            case 0x00 -> brk();
            case 0x01 -> orAccumulatorIndirectX();
            case 0x05 -> orAccumulatorZeroPage();
            case 0x06 -> shiftLeftZeroPage();
            case 0x08 -> pushProcessorStatus();
            case 0x09 -> orAccumulatorImmediate();
            case 0x0A -> shiftLeftAccumulator();
            case 0x0D -> orAccumulatorAbsolute();
            case 0x0E -> shiftLeftAbsolute();
            case 0x10 -> branchIf(!registers.flagSet(Mos6502Registers.FLAG_N));
            case 0x11 -> orAccumulatorIndirectY();
            case 0x15 -> orAccumulatorZeroPageX();
            case 0x16 -> shiftLeftZeroPageX();
            case 0x18 -> clearFlag(Mos6502Registers.FLAG_C);
            case 0x19 -> orAccumulatorAbsoluteY();
            case 0x1D -> orAccumulatorAbsoluteX();
            case 0x1E -> shiftLeftAbsoluteX();
            case 0x20 -> jsrAbsolute();
            case 0x21 -> andAccumulatorIndirectX();
            case 0x24 -> bitZeroPage();
            case 0x25 -> andAccumulatorZeroPage();
            case 0x26 -> rotateLeftZeroPage();
            case 0x28 -> pullProcessorStatus();
            case 0x29 -> andAccumulatorImmediate();
            case 0x2A -> rotateLeftAccumulator();
            case 0x2C -> bitAbsolute();
            case 0x2D -> andAccumulatorAbsolute();
            case 0x2E -> rotateLeftAbsolute();
            case 0x30 -> branchIf(registers.flagSet(Mos6502Registers.FLAG_N));
            case 0x31 -> andAccumulatorIndirectY();
            case 0x35 -> andAccumulatorZeroPageX();
            case 0x36 -> rotateLeftZeroPageX();
            case 0x38 -> setFlag(Mos6502Registers.FLAG_C);
            case 0x39 -> andAccumulatorAbsoluteY();
            case 0x3D -> andAccumulatorAbsoluteX();
            case 0x3E -> rotateLeftAbsoluteX();
            case 0x40 -> rti();
            case 0x41 -> exclusiveOrIndirectX();
            case 0x45 -> exclusiveOrZeroPage();
            case 0x48 -> pushAccumulator();
            case 0x49 -> exclusiveOrImmediate();
            case 0x46 -> shiftRightZeroPage();
            case 0x4A -> shiftRightAccumulator();
            case 0x4C -> jumpAbsolute();
            case 0x4D -> exclusiveOrAbsolute();
            case 0x4E -> shiftRightAbsolute();
            case 0x50 -> branchIf(!registers.flagSet(Mos6502Registers.FLAG_V));
            case 0x51 -> exclusiveOrIndirectY();
            case 0x55 -> exclusiveOrZeroPageX();
            case 0x56 -> shiftRightZeroPageX();
            case 0x58 -> clearFlag(Mos6502Registers.FLAG_I);
            case 0x59 -> exclusiveOrAbsoluteY();
            case 0x5D -> exclusiveOrAbsoluteX();
            case 0x5E -> shiftRightAbsoluteX();
            case 0x60 -> rts();
            case 0x61 -> adcIndirectX();
            case 0x65 -> adcZeroPage();
            case 0x66 -> rotateRightZeroPage();
            case 0x68 -> pullAccumulator();
            case 0x69 -> adcImmediate();
            case 0x6A -> rotateRightAccumulator();
            case 0x6C -> jumpIndirect();
            case 0x6D -> adcAbsolute();
            case 0x6E -> rotateRightAbsolute();
            case 0x70 -> branchIf(registers.flagSet(Mos6502Registers.FLAG_V));
            case 0x71 -> adcIndirectY();
            case 0x75 -> adcZeroPageX();
            case 0x76 -> rotateRightZeroPageX();
            case 0x78 -> setFlag(Mos6502Registers.FLAG_I);
            case 0x79 -> adcAbsoluteY();
            case 0x7D -> adcAbsoluteX();
            case 0x7E -> rotateRightAbsoluteX();
            case 0x81 -> storeAccumulatorIndirectX();
            case 0x84 -> storeYZeroPage();
            case 0x85 -> storeAccumulatorZeroPage();
            case 0x86 -> storeXZeroPage();
            case 0x88 -> decrementY();
            case 0x8A -> transferXToAccumulator();
            case 0x8C -> storeYAbsolute();
            case 0x8D -> storeAccumulatorAbsolute();
            case 0x8E -> storeXAbsolute();
            case 0x90 -> branchIf(!registers.flagSet(Mos6502Registers.FLAG_C));
            case 0x91 -> storeAccumulatorIndirectY();
            case 0x94 -> storeYZeroPageX();
            case 0x95 -> storeAccumulatorZeroPageX();
            case 0x96 -> storeXZeroPageY();
            case 0x98 -> transferYToAccumulator();
            case 0x99 -> storeAccumulatorAbsoluteY();
            case 0x9A -> transferXToStackPointer();
            case 0x9D -> storeAccumulatorAbsoluteX();
            case 0xA0 -> loadYImmediate();
            case 0xA1 -> loadAccumulatorIndirectX();
            case 0xA2 -> loadXImmediate();
            case 0xA4 -> loadYZeroPage();
            case 0xA5 -> loadAccumulatorZeroPage();
            case 0xA6 -> loadXZeroPage();
            case 0xA8 -> transferAccumulatorToY();
            case 0xA9 -> loadAccumulatorImmediate();
            case 0xAA -> transferAccumulatorToX();
            case 0xAC -> loadYAbsolute();
            case 0xAD -> loadAccumulatorAbsolute();
            case 0xAE -> loadXAbsolute();
            case 0xB0 -> branchIf(registers.flagSet(Mos6502Registers.FLAG_C));
            case 0xB1 -> loadAccumulatorIndirectY();
            case 0xB4 -> loadYZeroPageX();
            case 0xB5 -> loadAccumulatorZeroPageX();
            case 0xB6 -> loadXZeroPageY();
            case 0xB8 -> clearFlag(Mos6502Registers.FLAG_V);
            case 0xB9 -> loadAccumulatorAbsoluteY();
            case 0xBA -> transferStackPointerToX();
            case 0xBC -> loadYAbsoluteX();
            case 0xBD -> loadAccumulatorAbsoluteX();
            case 0xBE -> loadXAbsoluteY();
            case 0xC0 -> compareYImmediate();
            case 0xC1 -> compareAccumulatorIndirectX();
            case 0xC4 -> compareYZeroPage();
            case 0xC5 -> compareAccumulatorZeroPage();
            case 0xC6 -> decrementZeroPage();
            case 0xC8 -> incrementY();
            case 0xC9 -> compareAccumulatorImmediate();
            case 0xCA -> decrementX();
            case 0xCC -> compareYAbsolute();
            case 0xCD -> compareAccumulatorAbsolute();
            case 0xCE -> decrementAbsolute();
            case 0xD0 -> branchIf(!registers.flagSet(Mos6502Registers.FLAG_Z));
            case 0xD1 -> compareAccumulatorIndirectY();
            case 0xD5 -> compareAccumulatorZeroPageX();
            case 0xD6 -> decrementZeroPageX();
            case 0xD8 -> clearFlag(Mos6502Registers.FLAG_D);
            case 0xD9 -> compareAccumulatorAbsoluteY();
            case 0xDD -> compareAccumulatorAbsoluteX();
            case 0xDE -> decrementAbsoluteX();
            case 0xE0 -> compareXImmediate();
            case 0xE1 -> sbcIndirectX();
            case 0xE4 -> compareXZeroPage();
            case 0xE5 -> sbcZeroPage();
            case 0xE6 -> incrementZeroPage();
            case 0xE9 -> sbcImmediate();
            case 0xEA -> 2;
            case 0xE8 -> incrementX();
            case 0xEC -> compareXAbsolute();
            case 0xED -> sbcAbsolute();
            case 0xEE -> incrementAbsolute();
            case 0xF6 -> incrementZeroPageX();
            case 0xF1 -> sbcIndirectY();
            case 0xF0 -> branchIf(registers.flagSet(Mos6502Registers.FLAG_Z));
            case 0xF5 -> sbcZeroPageX();
            case 0xF8 -> setFlag(Mos6502Registers.FLAG_D);
            case 0xF9 -> sbcAbsoluteY();
            case 0xFD -> sbcAbsoluteX();
            case 0xFE -> incrementAbsoluteX();
            default -> {
                registers.setPc(opcodeAddress);
                throw new IllegalStateException(
                        "Illegal MOS 6502 opcode 0x%02X at 0x%04X".formatted(opcode & 0xFF, opcodeAddress & 0xFFFF)
                );
            }
        };
    }

    private int brk() {
        registers.incrementPc(1);
        pushWord(registers.pc());
        pushStatus(true);
        registers.setFlag(Mos6502Registers.FLAG_I, true);
        registers.setPc(readVector(IRQ_VECTOR));
        return 7;
    }

    private int serviceInterrupt(int vectorAddress, boolean breakFlag) {
        pushWord(registers.pc());
        pushStatus(breakFlag);
        registers.setFlag(Mos6502Registers.FLAG_I, true);
        registers.setPc(readVector(vectorAddress));
        return 7;
    }

    private int jumpAbsolute() {
        registers.setPc(fetchImmediate16());
        return 3;
    }

    private int jumpIndirect() {
        int pointer = fetchImmediate16();
        int low = bus.readMemory(pointer);
        int highAddress = (pointer & 0xFF00) | ((pointer + 1) & 0x00FF);
        int high = bus.readMemory(highAddress);
        registers.setPc(low | (high << 8));
        return 5;
    }

    private int jsrAbsolute() {
        int target = fetchImmediate16();
        pushWord((registers.pc() - 1) & 0xFFFF);
        registers.setPc(target);
        return 6;
    }

    private int rts() {
        registers.setPc((popWord() + 1) & 0xFFFF);
        return 6;
    }

    private int rti() {
        registers.setP(pop8());
        registers.setPc(popWord());
        return 6;
    }

    private int loadAccumulatorImmediate() {
        registers.setA(fetchImmediate8());
        registers.updateZeroAndNegative(registers.a());
        return 2;
    }

    private int loadAccumulatorZeroPage() {
        registers.setA(readZeroPageOperand());
        registers.updateZeroAndNegative(registers.a());
        return 3;
    }

    private int loadAccumulatorZeroPageX() {
        registers.setA(bus.readMemory(fetchZeroPageXAddress()));
        registers.updateZeroAndNegative(registers.a());
        return 4;
    }

    private int loadAccumulatorIndirectX() {
        return loadAccumulator(bus.readMemory(fetchIndirectXAddress()), 6);
    }

    private int loadAccumulatorIndirectY() {
        IndexedAddress address = fetchIndirectYAddress();
        registers.setA(bus.readMemory(address.value()));
        registers.updateZeroAndNegative(registers.a());
        return 5 + (address.crossedPage() ? 1 : 0);
    }

    private int loadAccumulatorAbsolute() {
        registers.setA(bus.readMemory(fetchImmediate16()));
        registers.updateZeroAndNegative(registers.a());
        return 4;
    }

    private int loadAccumulatorAbsoluteY() {
        IndexedAddress address = fetchAbsoluteYAddress();
        registers.setA(bus.readMemory(address.value()));
        registers.updateZeroAndNegative(registers.a());
        return 4 + (address.crossedPage() ? 1 : 0);
    }

    private int loadAccumulatorAbsoluteX() {
        IndexedAddress address = fetchAbsoluteXAddress();
        registers.setA(bus.readMemory(address.value()));
        registers.updateZeroAndNegative(registers.a());
        return 4 + (address.crossedPage() ? 1 : 0);
    }

    private int loadAccumulator(int value, int cycles) {
        registers.setA(value);
        registers.updateZeroAndNegative(registers.a());
        return cycles;
    }

    private int loadXImmediate() {
        registers.setX(fetchImmediate8());
        registers.updateZeroAndNegative(registers.x());
        return 2;
    }

    private int loadXZeroPage() {
        registers.setX(readZeroPageOperand());
        registers.updateZeroAndNegative(registers.x());
        return 3;
    }

    private int loadXZeroPageY() {
        return loadX(bus.readMemory(fetchZeroPageYAddress()), 4);
    }

    private int loadXAbsolute() {
        return loadX(bus.readMemory(fetchImmediate16()), 4);
    }

    private int loadXAbsoluteY() {
        IndexedAddress address = fetchAbsoluteYAddress();
        registers.setX(bus.readMemory(address.value()));
        registers.updateZeroAndNegative(registers.x());
        return 4 + (address.crossedPage() ? 1 : 0);
    }

    private int loadX(int value, int cycles) {
        registers.setX(value);
        registers.updateZeroAndNegative(registers.x());
        return cycles;
    }

    private int loadYImmediate() {
        registers.setY(fetchImmediate8());
        registers.updateZeroAndNegative(registers.y());
        return 2;
    }

    private int loadYZeroPage() {
        registers.setY(readZeroPageOperand());
        registers.updateZeroAndNegative(registers.y());
        return 3;
    }

    private int loadYZeroPageX() {
        registers.setY(bus.readMemory(fetchZeroPageXAddress()));
        registers.updateZeroAndNegative(registers.y());
        return 4;
    }

    private int loadYAbsolute() {
        registers.setY(bus.readMemory(fetchImmediate16()));
        registers.updateZeroAndNegative(registers.y());
        return 4;
    }

    private int loadYAbsoluteX() {
        IndexedAddress address = fetchAbsoluteXAddress();
        registers.setY(bus.readMemory(address.value()));
        registers.updateZeroAndNegative(registers.y());
        return 4 + (address.crossedPage() ? 1 : 0);
    }

    private int storeYZeroPage() {
        bus.writeMemory(fetchImmediate8(), registers.y());
        return 3;
    }

    private int storeXZeroPage() {
        bus.writeMemory(fetchImmediate8(), registers.x());
        return 3;
    }

    private int storeXZeroPageY() {
        bus.writeMemory(fetchZeroPageYAddress(), registers.x());
        return 4;
    }

    private int storeYZeroPageX() {
        bus.writeMemory(fetchZeroPageXAddress(), registers.y());
        return 4;
    }

    private int storeAccumulatorZeroPage() {
        bus.writeMemory(fetchImmediate8(), registers.a());
        return 3;
    }

    private int storeAccumulatorZeroPageX() {
        bus.writeMemory(fetchZeroPageXAddress(), registers.a());
        return 4;
    }

    private int storeAccumulatorIndirectX() {
        bus.writeMemory(fetchIndirectXAddress(), registers.a());
        return 6;
    }

    private int storeAccumulatorIndirectY() {
        bus.writeMemory(fetchIndirectYAddress().value(), registers.a());
        return 6;
    }

    private int storeAccumulatorAbsolute() {
        bus.writeMemory(fetchImmediate16(), registers.a());
        return 4;
    }

    private int storeYAbsolute() {
        bus.writeMemory(fetchImmediate16(), registers.y());
        return 4;
    }

    private int storeXAbsolute() {
        bus.writeMemory(fetchImmediate16(), registers.x());
        return 4;
    }

    private int storeAccumulatorAbsoluteX() {
        bus.writeMemory(fetchAbsoluteXAddress().value(), registers.a());
        return 5;
    }

    private int storeAccumulatorAbsoluteY() {
        bus.writeMemory(fetchAbsoluteYAddress().value(), registers.a());
        return 5;
    }

    private int transferAccumulatorToX() {
        registers.setX(registers.a());
        registers.updateZeroAndNegative(registers.x());
        return 2;
    }

    private int transferAccumulatorToY() {
        registers.setY(registers.a());
        registers.updateZeroAndNegative(registers.y());
        return 2;
    }

    private int transferXToStackPointer() {
        registers.setSp(registers.x());
        return 2;
    }

    private int transferXToAccumulator() {
        registers.setA(registers.x());
        registers.updateZeroAndNegative(registers.a());
        return 2;
    }

    private int transferStackPointerToX() {
        registers.setX(registers.sp());
        registers.updateZeroAndNegative(registers.x());
        return 2;
    }

    private int transferYToAccumulator() {
        registers.setA(registers.y());
        registers.updateZeroAndNegative(registers.a());
        return 2;
    }

    private int incrementX() {
        registers.setX(registers.x() + 1);
        registers.updateZeroAndNegative(registers.x());
        return 2;
    }

    private int decrementX() {
        registers.setX(registers.x() - 1);
        registers.updateZeroAndNegative(registers.x());
        return 2;
    }

    private int incrementY() {
        registers.setY(registers.y() + 1);
        registers.updateZeroAndNegative(registers.y());
        return 2;
    }

    private int decrementY() {
        registers.setY(registers.y() - 1);
        registers.updateZeroAndNegative(registers.y());
        return 2;
    }

    private int pushAccumulator() {
        push8(registers.a());
        return 3;
    }

    private int pushProcessorStatus() {
        pushStatus(true);
        return 3;
    }

    private int pullAccumulator() {
        registers.setA(pop8());
        registers.updateZeroAndNegative(registers.a());
        return 4;
    }

    private int pullProcessorStatus() {
        registers.setP(pop8());
        return 4;
    }

    private int shiftRightAccumulator() {
        int value = registers.a();
        registers.setFlag(Mos6502Registers.FLAG_C, (value & 0x01) != 0);
        registers.setA(value >>> 1);
        registers.updateZeroAndNegative(registers.a());
        return 2;
    }

    private int shiftRightZeroPage() {
        int address = fetchImmediate8();
        int value = bus.readMemory(address);
        int result = (value >>> 1) & 0xFF;
        registers.setFlag(Mos6502Registers.FLAG_C, (value & 0x01) != 0);
        bus.writeMemory(address, result);
        registers.updateZeroAndNegative(result);
        return 5;
    }

    private int shiftRightZeroPageX() {
        int address = fetchZeroPageXAddress();
        int value = bus.readMemory(address);
        int result = (value >>> 1) & 0xFF;
        registers.setFlag(Mos6502Registers.FLAG_C, (value & 0x01) != 0);
        bus.writeMemory(address, result);
        registers.updateZeroAndNegative(result);
        return 6;
    }

    private int shiftRightAbsolute() {
        return readModifyWrite(fetchImmediate16(), this::shiftRight, 6);
    }

    private int shiftRightAbsoluteX() {
        return readModifyWrite(fetchAbsoluteXAddress().value(), this::shiftRight, 7);
    }

    private int shiftLeftAccumulator() {
        int value = registers.a();
        registers.setFlag(Mos6502Registers.FLAG_C, (value & 0x80) != 0);
        registers.setA(value << 1);
        registers.updateZeroAndNegative(registers.a());
        return 2;
    }

    private int shiftLeftZeroPage() {
        int address = fetchImmediate8();
        int result = shiftLeft(bus.readMemory(address));
        bus.writeMemory(address, result);
        return 5;
    }

    private int shiftLeftZeroPageX() {
        int address = fetchZeroPageXAddress();
        int result = shiftLeft(bus.readMemory(address));
        bus.writeMemory(address, result);
        return 6;
    }

    private int shiftLeftAbsolute() {
        return readModifyWrite(fetchImmediate16(), this::shiftLeft, 6);
    }

    private int shiftLeftAbsoluteX() {
        return readModifyWrite(fetchAbsoluteXAddress().value(), this::shiftLeft, 7);
    }

    private int rotateLeftAccumulator() {
        registers.setA(rotateLeft(registers.a()));
        return 2;
    }

    private int rotateLeftZeroPage() {
        int address = fetchImmediate8();
        int result = rotateLeft(bus.readMemory(address));
        bus.writeMemory(address, result);
        return 5;
    }

    private int rotateLeftZeroPageX() {
        int address = fetchZeroPageXAddress();
        int result = rotateLeft(bus.readMemory(address));
        bus.writeMemory(address, result);
        return 6;
    }

    private int rotateLeftAbsolute() {
        return readModifyWrite(fetchImmediate16(), this::rotateLeft, 6);
    }

    private int rotateLeftAbsoluteX() {
        return readModifyWrite(fetchAbsoluteXAddress().value(), this::rotateLeft, 7);
    }

    private int rotateRightAccumulator() {
        registers.setA(rotateRight(registers.a()));
        return 2;
    }

    private int rotateRightZeroPage() {
        int address = fetchImmediate8();
        int result = rotateRight(bus.readMemory(address));
        bus.writeMemory(address, result);
        return 5;
    }

    private int rotateRightZeroPageX() {
        int address = fetchZeroPageXAddress();
        int result = rotateRight(bus.readMemory(address));
        bus.writeMemory(address, result);
        return 6;
    }

    private int rotateRightAbsolute() {
        return readModifyWrite(fetchImmediate16(), this::rotateRight, 6);
    }

    private int rotateRightAbsoluteX() {
        return readModifyWrite(fetchAbsoluteXAddress().value(), this::rotateRight, 7);
    }

    private int shiftRight(int value) {
        registers.setFlag(Mos6502Registers.FLAG_C, (value & 0x01) != 0);
        int result = (value >>> 1) & 0xFF;
        registers.updateZeroAndNegative(result);
        return result;
    }

    private int shiftLeft(int value) {
        registers.setFlag(Mos6502Registers.FLAG_C, (value & 0x80) != 0);
        int result = (value << 1) & 0xFF;
        registers.updateZeroAndNegative(result);
        return result;
    }

    private int rotateLeft(int value) {
        int carryIn = registers.flagSet(Mos6502Registers.FLAG_C) ? 1 : 0;
        registers.setFlag(Mos6502Registers.FLAG_C, (value & 0x80) != 0);
        int result = ((value << 1) | carryIn) & 0xFF;
        registers.updateZeroAndNegative(result);
        return result;
    }

    private int rotateRight(int value) {
        int carryIn = registers.flagSet(Mos6502Registers.FLAG_C) ? 0x80 : 0;
        registers.setFlag(Mos6502Registers.FLAG_C, (value & 0x01) != 0);
        int result = ((value >>> 1) | carryIn) & 0xFF;
        registers.updateZeroAndNegative(result);
        return result;
    }

    private int readModifyWrite(int address, IntUnaryOperator operation, int cycles) {
        int result = operation.applyAsInt(bus.readMemory(address) & 0xFF) & 0xFF;
        bus.writeMemory(address, result);
        return cycles;
    }

    private int decrementZeroPage() {
        int address = fetchImmediate8();
        int value = (bus.readMemory(address) - 1) & 0xFF;
        bus.writeMemory(address, value);
        registers.updateZeroAndNegative(value);
        return 5;
    }

    private int decrementZeroPageX() {
        int address = fetchZeroPageXAddress();
        int value = (bus.readMemory(address) - 1) & 0xFF;
        bus.writeMemory(address, value);
        registers.updateZeroAndNegative(value);
        return 6;
    }

    private int decrementAbsolute() {
        int address = fetchImmediate16();
        int value = (bus.readMemory(address) - 1) & 0xFF;
        bus.writeMemory(address, value);
        registers.updateZeroAndNegative(value);
        return 6;
    }

    private int decrementAbsoluteX() {
        int address = fetchAbsoluteXAddress().value();
        int value = (bus.readMemory(address) - 1) & 0xFF;
        bus.writeMemory(address, value);
        registers.updateZeroAndNegative(value);
        return 7;
    }

    private int incrementZeroPage() {
        int address = fetchImmediate8();
        int value = (bus.readMemory(address) + 1) & 0xFF;
        bus.writeMemory(address, value);
        registers.updateZeroAndNegative(value);
        return 5;
    }

    private int incrementZeroPageX() {
        int address = fetchZeroPageXAddress();
        int value = (bus.readMemory(address) + 1) & 0xFF;
        bus.writeMemory(address, value);
        registers.updateZeroAndNegative(value);
        return 6;
    }

    private int incrementAbsolute() {
        int address = fetchImmediate16();
        int value = (bus.readMemory(address) + 1) & 0xFF;
        bus.writeMemory(address, value);
        registers.updateZeroAndNegative(value);
        return 6;
    }

    private int incrementAbsoluteX() {
        int address = fetchAbsoluteXAddress().value();
        int value = (bus.readMemory(address) + 1) & 0xFF;
        bus.writeMemory(address, value);
        registers.updateZeroAndNegative(value);
        return 7;
    }

    private int andAccumulatorImmediate() {
        registers.setA(registers.a() & fetchImmediate8());
        registers.updateZeroAndNegative(registers.a());
        return 2;
    }

    private int andAccumulatorZeroPage() {
        registers.setA(registers.a() & readZeroPageOperand());
        registers.updateZeroAndNegative(registers.a());
        return 3;
    }

    private int andAccumulatorZeroPageX() {
        return andAccumulator(bus.readMemory(fetchZeroPageXAddress()), 4);
    }

    private int andAccumulatorAbsolute() {
        return andAccumulator(bus.readMemory(fetchImmediate16()), 4);
    }

    private int andAccumulatorAbsoluteX() {
        IndexedAddress address = fetchAbsoluteXAddress();
        return andAccumulator(bus.readMemory(address.value()), 4 + (address.crossedPage() ? 1 : 0));
    }

    private int andAccumulatorAbsoluteY() {
        IndexedAddress address = fetchAbsoluteYAddress();
        return andAccumulator(bus.readMemory(address.value()), 4 + (address.crossedPage() ? 1 : 0));
    }

    private int andAccumulatorIndirectX() {
        return andAccumulator(bus.readMemory(fetchIndirectXAddress()), 6);
    }

    private int andAccumulatorIndirectY() {
        IndexedAddress address = fetchIndirectYAddress();
        return andAccumulator(bus.readMemory(address.value()), 5 + (address.crossedPage() ? 1 : 0));
    }

    private int andAccumulator(int value, int cycles) {
        registers.setA(registers.a() & value);
        registers.updateZeroAndNegative(registers.a());
        return cycles;
    }

    private int orAccumulatorImmediate() {
        registers.setA(registers.a() | fetchImmediate8());
        registers.updateZeroAndNegative(registers.a());
        return 2;
    }

    private int orAccumulatorZeroPage() {
        registers.setA(registers.a() | readZeroPageOperand());
        registers.updateZeroAndNegative(registers.a());
        return 3;
    }

    private int orAccumulatorZeroPageX() {
        return orAccumulator(bus.readMemory(fetchZeroPageXAddress()), 4);
    }

    private int orAccumulatorAbsolute() {
        return orAccumulator(bus.readMemory(fetchImmediate16()), 4);
    }

    private int orAccumulatorAbsoluteX() {
        IndexedAddress address = fetchAbsoluteXAddress();
        return orAccumulator(bus.readMemory(address.value()), 4 + (address.crossedPage() ? 1 : 0));
    }

    private int orAccumulatorAbsoluteY() {
        IndexedAddress address = fetchAbsoluteYAddress();
        return orAccumulator(bus.readMemory(address.value()), 4 + (address.crossedPage() ? 1 : 0));
    }

    private int orAccumulatorIndirectX() {
        return orAccumulator(bus.readMemory(fetchIndirectXAddress()), 6);
    }

    private int orAccumulatorIndirectY() {
        IndexedAddress address = fetchIndirectYAddress();
        return orAccumulator(bus.readMemory(address.value()), 5 + (address.crossedPage() ? 1 : 0));
    }

    private int orAccumulator(int value, int cycles) {
        registers.setA(registers.a() | value);
        registers.updateZeroAndNegative(registers.a());
        return cycles;
    }

    private int exclusiveOrImmediate() {
        registers.setA(registers.a() ^ fetchImmediate8());
        registers.updateZeroAndNegative(registers.a());
        return 2;
    }

    private int exclusiveOrZeroPage() {
        registers.setA(registers.a() ^ readZeroPageOperand());
        registers.updateZeroAndNegative(registers.a());
        return 3;
    }

    private int exclusiveOrZeroPageX() {
        return exclusiveOr(bus.readMemory(fetchZeroPageXAddress()), 4);
    }

    private int exclusiveOrAbsolute() {
        return exclusiveOr(bus.readMemory(fetchImmediate16()), 4);
    }

    private int exclusiveOrAbsoluteX() {
        IndexedAddress address = fetchAbsoluteXAddress();
        return exclusiveOr(bus.readMemory(address.value()), 4 + (address.crossedPage() ? 1 : 0));
    }

    private int exclusiveOrAbsoluteY() {
        IndexedAddress address = fetchAbsoluteYAddress();
        return exclusiveOr(bus.readMemory(address.value()), 4 + (address.crossedPage() ? 1 : 0));
    }

    private int exclusiveOrIndirectX() {
        return exclusiveOr(bus.readMemory(fetchIndirectXAddress()), 6);
    }

    private int exclusiveOrIndirectY() {
        IndexedAddress address = fetchIndirectYAddress();
        return exclusiveOr(bus.readMemory(address.value()), 5 + (address.crossedPage() ? 1 : 0));
    }

    private int exclusiveOr(int value, int cycles) {
        registers.setA(registers.a() ^ value);
        registers.updateZeroAndNegative(registers.a());
        return cycles;
    }

    private int adcIndirectX() {
        addWithCarry(bus.readMemory(fetchIndirectXAddress()));
        return 6;
    }

    private int adcZeroPage() {
        addWithCarry(readZeroPageOperand());
        return 3;
    }

    private int adcZeroPageX() {
        addWithCarry(bus.readMemory(fetchZeroPageXAddress()));
        return 4;
    }

    private int adcImmediate() {
        addWithCarry(fetchImmediate8());
        return 2;
    }

    private int adcAbsolute() {
        addWithCarry(bus.readMemory(fetchImmediate16()));
        return 4;
    }

    private int adcAbsoluteX() {
        IndexedAddress address = fetchAbsoluteXAddress();
        addWithCarry(bus.readMemory(address.value()));
        return 4 + (address.crossedPage() ? 1 : 0);
    }

    private int adcAbsoluteY() {
        IndexedAddress address = fetchAbsoluteYAddress();
        addWithCarry(bus.readMemory(address.value()));
        return 4 + (address.crossedPage() ? 1 : 0);
    }

    private int adcIndirectY() {
        IndexedAddress address = fetchIndirectYAddress();
        addWithCarry(bus.readMemory(address.value()));
        return 5 + (address.crossedPage() ? 1 : 0);
    }

    private int sbcIndirectX() {
        subtractWithCarry(bus.readMemory(fetchIndirectXAddress()));
        return 6;
    }

    private int sbcImmediate() {
        subtractWithCarry(fetchImmediate8());
        return 2;
    }

    private int sbcZeroPage() {
        subtractWithCarry(readZeroPageOperand());
        return 3;
    }

    private int sbcZeroPageX() {
        subtractWithCarry(bus.readMemory(fetchZeroPageXAddress()));
        return 4;
    }

    private int sbcAbsolute() {
        subtractWithCarry(bus.readMemory(fetchImmediate16()));
        return 4;
    }

    private int sbcAbsoluteX() {
        IndexedAddress address = fetchAbsoluteXAddress();
        subtractWithCarry(bus.readMemory(address.value()));
        return 4 + (address.crossedPage() ? 1 : 0);
    }

    private int sbcAbsoluteY() {
        IndexedAddress address = fetchAbsoluteYAddress();
        subtractWithCarry(bus.readMemory(address.value()));
        return 4 + (address.crossedPage() ? 1 : 0);
    }

    private int sbcIndirectY() {
        IndexedAddress address = fetchIndirectYAddress();
        subtractWithCarry(bus.readMemory(address.value()));
        return 5 + (address.crossedPage() ? 1 : 0);
    }

    private int compareAccumulatorZeroPage() {
        compare(registers.a(), readZeroPageOperand());
        return 3;
    }

    private int compareAccumulatorZeroPageX() {
        compare(registers.a(), bus.readMemory(fetchZeroPageXAddress()));
        return 4;
    }

    private int compareYZeroPage() {
        compare(registers.y(), readZeroPageOperand());
        return 3;
    }

    private int compareYImmediate() {
        compare(registers.y(), fetchImmediate8());
        return 2;
    }

    private int compareYAbsolute() {
        compare(registers.y(), bus.readMemory(fetchImmediate16()));
        return 4;
    }

    private int compareXImmediate() {
        compare(registers.x(), fetchImmediate8());
        return 2;
    }

    private int compareXZeroPage() {
        compare(registers.x(), readZeroPageOperand());
        return 3;
    }

    private int compareXAbsolute() {
        compare(registers.x(), bus.readMemory(fetchImmediate16()));
        return 4;
    }

    private int compareAccumulatorAbsolute() {
        compare(registers.a(), bus.readMemory(fetchImmediate16()));
        return 4;
    }

    private int compareAccumulatorAbsoluteX() {
        IndexedAddress address = fetchAbsoluteXAddress();
        compare(registers.a(), bus.readMemory(address.value()));
        return 4 + (address.crossedPage() ? 1 : 0);
    }

    private int compareAccumulatorAbsoluteY() {
        IndexedAddress address = fetchAbsoluteYAddress();
        compare(registers.a(), bus.readMemory(address.value()));
        return 4 + (address.crossedPage() ? 1 : 0);
    }

    private int compareAccumulatorIndirectX() {
        compare(registers.a(), bus.readMemory(fetchIndirectXAddress()));
        return 6;
    }

    private int compareAccumulatorIndirectY() {
        IndexedAddress address = fetchIndirectYAddress();
        compare(registers.a(), bus.readMemory(address.value()));
        return 5 + (address.crossedPage() ? 1 : 0);
    }

    private int compareAccumulatorImmediate() {
        compare(registers.a(), fetchImmediate8());
        return 2;
    }

    private int bitAbsolute() {
        int value = bus.readMemory(fetchImmediate16()) & 0xFF;
        bit(value);
        return 4;
    }

    private int bitZeroPage() {
        bit(readZeroPageOperand());
        return 3;
    }

    private void bit(int value) {
        registers.setFlag(Mos6502Registers.FLAG_Z, (registers.a() & value) == 0);
        registers.setFlag(Mos6502Registers.FLAG_V, (value & 0x40) != 0);
        registers.setFlag(Mos6502Registers.FLAG_N, (value & 0x80) != 0);
    }

    private void compare(int registerValue, int value) {
        int result = (registerValue - (value & 0xFF)) & 0xFF;
        registers.setFlag(Mos6502Registers.FLAG_C, (registerValue & 0xFF) >= (value & 0xFF));
        registers.setFlag(Mos6502Registers.FLAG_Z, (registerValue & 0xFF) == (value & 0xFF));
        registers.setFlag(Mos6502Registers.FLAG_N, (result & 0x80) != 0);
    }

    private int branchIf(boolean condition) {
        int offset = (byte) fetchImmediate8();
        if (!condition) {
            return 2;
        }

        int source = registers.pc();
        int target = (source + offset) & 0xFFFF;
        registers.setPc(target);
        return 3 + (((source ^ target) & 0xFF00) != 0 ? 1 : 0);
    }

    private int readZeroPageOperand() {
        return bus.readMemory(fetchImmediate8()) & 0xFF;
    }

    private int fetchZeroPageXAddress() {
        return (fetchImmediate8() + registers.x()) & 0xFF;
    }

    private int fetchZeroPageYAddress() {
        return (fetchImmediate8() + registers.y()) & 0xFF;
    }

    private int fetchIndirectXAddress() {
        return readZeroPageWord((fetchImmediate8() + registers.x()) & 0xFF);
    }

    private IndexedAddress fetchIndirectYAddress() {
        int zeroPageAddress = fetchImmediate8();
        int base = readZeroPageWord(zeroPageAddress);
        int value = (base + registers.y()) & 0xFFFF;
        return new IndexedAddress(value, ((base ^ value) & 0xFF00) != 0);
    }

    private IndexedAddress fetchAbsoluteYAddress() {
        int base = fetchImmediate16();
        int value = (base + registers.y()) & 0xFFFF;
        return new IndexedAddress(value, ((base ^ value) & 0xFF00) != 0);
    }

    private IndexedAddress fetchAbsoluteXAddress() {
        int base = fetchImmediate16();
        int value = (base + registers.x()) & 0xFFFF;
        return new IndexedAddress(value, ((base ^ value) & 0xFF00) != 0);
    }

    private int readZeroPageWord(int zeroPageAddress) {
        int low = bus.readMemory(zeroPageAddress & 0xFF);
        int high = bus.readMemory((zeroPageAddress + 1) & 0xFF);
        return low | (high << 8);
    }

    private void addWithCarry(int value) {
        int accumulator = registers.a();
        int value8 = value & 0xFF;
        int carryIn = registers.flagSet(Mos6502Registers.FLAG_C) ? 1 : 0;
        int result = accumulator + value8 + carryIn;
        int result8 = result & 0xFF;

        registers.setFlag(Mos6502Registers.FLAG_V, ((accumulator ^ result8) & (value8 ^ result8) & 0x80) != 0);
        if (registers.flagSet(Mos6502Registers.FLAG_D)) {
            DecimalResult decimalResult = decimalAdd(accumulator, value8, carryIn);
            registers.setFlag(Mos6502Registers.FLAG_C, decimalResult.carry());
            result8 = decimalResult.value();
        } else {
            registers.setFlag(Mos6502Registers.FLAG_C, result > 0xFF);
        }
        registers.setA(result8);
        registers.updateZeroAndNegative(result8);
    }

    private void subtractWithCarry(int value) {
        int accumulator = registers.a();
        int value8 = value & 0xFF;
        int borrow = registers.flagSet(Mos6502Registers.FLAG_C) ? 0 : 1;
        int result = accumulator - value8 - borrow;
        int result8 = result & 0xFF;

        registers.setFlag(Mos6502Registers.FLAG_C, result >= 0);
        registers.setFlag(Mos6502Registers.FLAG_V, ((accumulator ^ result8) & (accumulator ^ value8) & 0x80) != 0);
        if (registers.flagSet(Mos6502Registers.FLAG_D)) {
            result8 = decimalSubtract(accumulator, value8, borrow);
        }
        registers.setA(result8);
        registers.updateZeroAndNegative(result8);
    }

    private static DecimalResult decimalAdd(int accumulator, int value, int carryIn) {
        int low = (accumulator & 0x0F) + (value & 0x0F) + carryIn;
        int high = (accumulator >>> 4) + (value >>> 4);
        if (low > 9) {
            low += 6;
            high++;
        }
        boolean carry = high > 9;
        if (high > 9) {
            high += 6;
        }
        return new DecimalResult(((high << 4) | (low & 0x0F)) & 0xFF, carry);
    }

    private static int decimalSubtract(int accumulator, int value, int borrow) {
        int low = (accumulator & 0x0F) - (value & 0x0F) - borrow;
        int high = (accumulator >>> 4) - (value >>> 4);
        if (low < 0) {
            low -= 6;
            high--;
        }
        if (high < 0) {
            high -= 6;
        }
        return ((high << 4) | (low & 0x0F)) & 0xFF;
    }

    private record IndexedAddress(int value, boolean crossedPage) {
    }

    private record DecimalResult(int value, boolean carry) {
    }

    private int clearFlag(int flagMask) {
        registers.setFlag(flagMask, false);
        return 2;
    }

    private int setFlag(int flagMask) {
        registers.setFlag(flagMask, true);
        return 2;
    }

    private int fetchOpcode() {
        int pc = registers.pc();
        int opcode = bus.fetchOpcode(pc);
        registers.incrementPc(1);
        return opcode & 0xFF;
    }

    private int fetchImmediate8() {
        int pc = registers.pc();
        int value = bus.readMemory(pc);
        registers.incrementPc(1);
        return value & 0xFF;
    }

    private int fetchImmediate16() {
        int low = fetchImmediate8();
        int high = fetchImmediate8();
        return low | (high << 8);
    }

    private int readVector(int address) {
        int low = bus.readMemory(address & 0xFFFF);
        int high = bus.readMemory((address + 1) & 0xFFFF);
        return (low & 0xFF) | ((high & 0xFF) << 8);
    }

    private void pushWord(int value) {
        push8((value >>> 8) & 0xFF);
        push8(value & 0xFF);
    }

    private int popWord() {
        int low = pop8();
        int high = pop8();
        return low | (high << 8);
    }

    private void pushStatus(boolean breakFlag) {
        int status = registers.p();
        if (breakFlag) {
            status |= Mos6502Registers.FLAG_B;
        } else {
            status &= ~Mos6502Registers.FLAG_B;
        }
        push8(status);
    }

    private void push8(int value) {
        bus.writeMemory(STACK_PAGE | registers.sp(), value);
        registers.setSp(registers.sp() - 1);
    }

    private int pop8() {
        registers.setSp(registers.sp() + 1);
        return bus.readMemory(STACK_PAGE | registers.sp()) & 0xFF;
    }
}
