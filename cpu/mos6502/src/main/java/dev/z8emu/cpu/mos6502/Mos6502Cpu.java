package dev.z8emu.cpu.mos6502;

import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.cpu.Cpu;
import java.util.Objects;

public final class Mos6502Cpu implements Cpu {
    private static final int RESET_VECTOR = 0xFFFC;
    private static final int IRQ_VECTOR = 0xFFFE;
    private static final int NMI_VECTOR = 0xFFFA;
    private static final int STACK_PAGE = 0x0100;

    private final CpuBus bus;
    private final Mos6502Variant variant;
    private final Mos6502Registers registers = new Mos6502Registers();
    private boolean irqPending;
    private boolean nmiPending;

    public Mos6502Cpu(CpuBus bus) {
        this(bus, Mos6502Variant.NMOS_6502);
    }

    public Mos6502Cpu(CpuBus bus, Mos6502Variant variant) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.variant = Objects.requireNonNull(variant, "variant");
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
            return serviceInterrupt(NMI_VECTOR);
        }
        if (irqPending && !registers.flagSet(Mos6502Registers.FLAG_I)) {
            irqPending = false;
            return serviceInterrupt(IRQ_VECTOR);
        }

        int opcodeAddress = registers.pc();
        int opcode = fetchOpcode();
        return executeOpcode(opcode, opcodeAddress);
    }

    private int executeOpcode(int opcode, int opcodeAddress) {
        return switch (opcode & 0xFF) {
            case 0x00 -> brk();
            case 0x01 -> orAccumulatorIndirectX();
            case 0x02 -> nopImmediate65C02(opcodeAddress);
            case 0x03 -> {
                requireNmos(0x03, opcodeAddress);
                yield shiftLeftOrIndirectX();
            }
            case 0x04 -> variant == Mos6502Variant.CMOS_65C02
                    ? testAndSetBitsZeroPage65C02(opcodeAddress) : nopZeroPage();
            case 0x05 -> orAccumulatorZeroPage();
            case 0x06 -> shiftLeftZeroPage();
            case 0x07 -> variant == Mos6502Variant.CMOS_65C02
                    ? resetMemoryBitZeroPage65C02(opcodeAddress, 0) : shiftLeftOrZeroPage();
            case 0x08 -> pushProcessorStatus();
            case 0x09 -> orAccumulatorImmediate();
            case 0x0A -> shiftLeftAccumulator();
            case 0x0B -> {
                requireNmos(0x0B, opcodeAddress);
                yield andAccumulatorSetCarryImmediate();
            }
            case 0x0C -> variant == Mos6502Variant.CMOS_65C02
                    ? testAndSetBitsAbsolute65C02(opcodeAddress) : nopAbsolute();
            case 0x0D -> orAccumulatorAbsolute();
            case 0x0E -> shiftLeftAbsolute();
            case 0x0F -> {
                requireNmos(0x0F, opcodeAddress);
                yield shiftLeftOrAbsolute();
            }
            case 0x10 -> branchIf(!registers.flagSet(Mos6502Registers.FLAG_N));
            case 0x11 -> orAccumulatorIndirectY();
            case 0x13 -> {
                requireNmos(0x13, opcodeAddress);
                yield shiftLeftOrIndirectY();
            }
            case 0x14 -> variant == Mos6502Variant.CMOS_65C02
                    ? testAndResetBitsZeroPage65C02(opcodeAddress) : nopZeroPageX();
            case 0x15 -> orAccumulatorZeroPageX();
            case 0x16 -> shiftLeftZeroPageX();
            case 0x17 -> variant == Mos6502Variant.CMOS_65C02
                    ? resetMemoryBitZeroPage65C02(opcodeAddress, 1) : shiftLeftOrZeroPageX();
            case 0x18 -> clearFlag(Mos6502Registers.FLAG_C);
            case 0x19 -> orAccumulatorAbsoluteY();
            case 0x1A -> variant == Mos6502Variant.CMOS_65C02
                    ? incrementAccumulator65C02(opcodeAddress) : nop();
            case 0x1B -> {
                requireNmos(0x1B, opcodeAddress);
                yield shiftLeftOrAbsoluteY();
            }
            case 0x1C -> variant == Mos6502Variant.CMOS_65C02
                    ? testAndResetBitsAbsolute65C02(opcodeAddress) : nopAbsoluteX();
            case 0x1D -> orAccumulatorAbsoluteX();
            case 0x1E -> shiftLeftAbsoluteX();
            case 0x1F -> {
                requireNmos(0x1F, opcodeAddress);
                yield shiftLeftOrAbsoluteX();
            }
            case 0x20 -> jsrAbsolute();
            case 0x21 -> andAccumulatorIndirectX();
            case 0x23 -> {
                requireNmos(0x23, opcodeAddress);
                yield rotateLeftAndIndirectX();
            }
            case 0x24 -> bitZeroPage();
            case 0x25 -> andAccumulatorZeroPage();
            case 0x26 -> rotateLeftZeroPage();
            case 0x27 -> variant == Mos6502Variant.CMOS_65C02
                    ? resetMemoryBitZeroPage65C02(opcodeAddress, 2) : rotateLeftAndZeroPage();
            case 0x28 -> pullProcessorStatus();
            case 0x29 -> andAccumulatorImmediate();
            case 0x2A -> rotateLeftAccumulator();
            case 0x2B -> {
                requireNmos(0x2B, opcodeAddress);
                yield andAccumulatorSetCarryImmediate();
            }
            case 0x2C -> bitAbsolute();
            case 0x2D -> andAccumulatorAbsolute();
            case 0x2E -> rotateLeftAbsolute();
            case 0x2F -> {
                requireNmos(0x2F, opcodeAddress);
                yield rotateLeftAndAbsolute();
            }
            case 0x30 -> branchIf(registers.flagSet(Mos6502Registers.FLAG_N));
            case 0x31 -> andAccumulatorIndirectY();
            case 0x33 -> {
                requireNmos(0x33, opcodeAddress);
                yield rotateLeftAndIndirectY();
            }
            case 0x34 -> variant == Mos6502Variant.CMOS_65C02
                    ? bitZeroPageX65C02(opcodeAddress) : nopZeroPageX();
            case 0x35 -> andAccumulatorZeroPageX();
            case 0x36 -> rotateLeftZeroPageX();
            case 0x37 -> variant == Mos6502Variant.CMOS_65C02
                    ? resetMemoryBitZeroPage65C02(opcodeAddress, 3) : rotateLeftAndZeroPageX();
            case 0x38 -> setFlag(Mos6502Registers.FLAG_C);
            case 0x39 -> andAccumulatorAbsoluteY();
            case 0x3A -> variant == Mos6502Variant.CMOS_65C02
                    ? decrementAccumulator65C02(opcodeAddress) : nop();
            case 0x3B -> {
                requireNmos(0x3B, opcodeAddress);
                yield rotateLeftAndAbsoluteY();
            }
            case 0x3C -> variant == Mos6502Variant.CMOS_65C02
                    ? bitAbsoluteX65C02(opcodeAddress) : nopAbsoluteX();
            case 0x3D -> andAccumulatorAbsoluteX();
            case 0x3E -> rotateLeftAbsoluteX();
            case 0x3F -> {
                requireNmos(0x3F, opcodeAddress);
                yield rotateLeftAndAbsoluteX();
            }
            case 0x40 -> rti();
            case 0x41 -> exclusiveOrIndirectX();
            case 0x43 -> {
                requireNmos(0x43, opcodeAddress);
                yield shiftRightExclusiveOrIndirectX();
            }
            case 0x44 -> {
                requireNmos(0x44, opcodeAddress);
                yield nopZeroPage();
            }
            case 0x45 -> exclusiveOrZeroPage();
            case 0x46 -> shiftRightZeroPage();
            case 0x47 -> variant == Mos6502Variant.CMOS_65C02
                    ? resetMemoryBitZeroPage65C02(opcodeAddress, 4) : shiftRightExclusiveOrZeroPage();
            case 0x48 -> pushAccumulator();
            case 0x49 -> exclusiveOrImmediate();
            case 0x4A -> shiftRightAccumulator();
            case 0x4B -> {
                requireNmos(0x4B, opcodeAddress);
                yield andAccumulatorShiftRightImmediate();
            }
            case 0x4C -> jumpAbsolute();
            case 0x4D -> exclusiveOrAbsolute();
            case 0x4E -> shiftRightAbsolute();
            case 0x4F -> {
                requireNmos(0x4F, opcodeAddress);
                yield shiftRightExclusiveOrAbsolute();
            }
            case 0x50 -> branchIf(!registers.flagSet(Mos6502Registers.FLAG_V));
            case 0x51 -> exclusiveOrIndirectY();
            case 0x53 -> {
                requireNmos(0x53, opcodeAddress);
                yield shiftRightExclusiveOrIndirectY();
            }
            case 0x54 -> {
                requireNmos(0x54, opcodeAddress);
                yield nopZeroPageX();
            }
            case 0x55 -> exclusiveOrZeroPageX();
            case 0x56 -> shiftRightZeroPageX();
            case 0x57 -> variant == Mos6502Variant.CMOS_65C02
                    ? resetMemoryBitZeroPage65C02(opcodeAddress, 5) : shiftRightExclusiveOrZeroPageX();
            case 0x58 -> clearFlag(Mos6502Registers.FLAG_I);
            case 0x59 -> exclusiveOrAbsoluteY();
            case 0x5A -> variant == Mos6502Variant.CMOS_65C02
                    ? pushY65C02(opcodeAddress) : nop();
            case 0x5B -> {
                requireNmos(0x5B, opcodeAddress);
                yield shiftRightExclusiveOrAbsoluteY();
            }
            case 0x5C -> {
                requireNmos(0x5C, opcodeAddress);
                yield nopAbsoluteX();
            }
            case 0x5D -> exclusiveOrAbsoluteX();
            case 0x5E -> shiftRightAbsoluteX();
            case 0x5F -> {
                requireNmos(0x5F, opcodeAddress);
                yield shiftRightExclusiveOrAbsoluteX();
            }
            case 0x60 -> rts();
            case 0x61 -> adcIndirectX();
            case 0x63 -> {
                requireNmos(0x63, opcodeAddress);
                yield rotateRightAddIndirectX();
            }
            case 0x64 -> variant == Mos6502Variant.CMOS_65C02
                    ? storeZeroZeroPage65C02(opcodeAddress) : nopZeroPage();
            case 0x65 -> adcZeroPage();
            case 0x66 -> rotateRightZeroPage();
            case 0x67 -> variant == Mos6502Variant.CMOS_65C02
                    ? resetMemoryBitZeroPage65C02(opcodeAddress, 6) : rotateRightAddZeroPage();
            case 0x68 -> pullAccumulator();
            case 0x69 -> adcImmediate();
            case 0x6A -> rotateRightAccumulator();
            case 0x6B -> {
                requireNmos(0x6B, opcodeAddress);
                yield andAccumulatorRotateRightImmediate();
            }
            case 0x6C -> jumpIndirect();
            case 0x6D -> adcAbsolute();
            case 0x6E -> rotateRightAbsolute();
            case 0x6F -> {
                requireNmos(0x6F, opcodeAddress);
                yield rotateRightAddAbsolute();
            }
            case 0x70 -> branchIf(registers.flagSet(Mos6502Registers.FLAG_V));
            case 0x71 -> adcIndirectY();
            case 0x73 -> {
                requireNmos(0x73, opcodeAddress);
                yield rotateRightAddIndirectY();
            }
            case 0x74 -> variant == Mos6502Variant.CMOS_65C02
                    ? storeZeroZeroPageX65C02(opcodeAddress) : nopZeroPageX();
            case 0x75 -> adcZeroPageX();
            case 0x76 -> rotateRightZeroPageX();
            case 0x77 -> variant == Mos6502Variant.CMOS_65C02
                    ? resetMemoryBitZeroPage65C02(opcodeAddress, 7) : rotateRightAddZeroPageX();
            case 0x78 -> setFlag(Mos6502Registers.FLAG_I);
            case 0x79 -> adcAbsoluteY();
            case 0x7A -> variant == Mos6502Variant.CMOS_65C02
                    ? pullY65C02(opcodeAddress) : nop();
            case 0x7B -> {
                requireNmos(0x7B, opcodeAddress);
                yield rotateRightAddAbsoluteY();
            }
            case 0x7C -> variant == Mos6502Variant.CMOS_65C02
                    ? jumpAbsoluteIndexedIndirect65C02(opcodeAddress) : nopAbsoluteX();
            case 0x7D -> adcAbsoluteX();
            case 0x7E -> rotateRightAbsoluteX();
            case 0x7F -> {
                requireNmos(0x7F, opcodeAddress);
                yield rotateRightAddAbsoluteX();
            }
            case 0x80 -> variant == Mos6502Variant.CMOS_65C02
                    ? branchAlways65C02(opcodeAddress) : nopImmediate();
            case 0x81 -> storeAccumulatorIndirectX();
            case 0x82 -> {
                requireNmos(0x82, opcodeAddress);
                yield nopImmediate();
            }
            case 0x83 -> {
                requireNmos(0x83, opcodeAddress);
                yield storeAccumulatorAndXIndirectX();
            }
            case 0x84 -> storeYZeroPage();
            case 0x85 -> storeAccumulatorZeroPage();
            case 0x86 -> storeXZeroPage();
            case 0x87 -> variant == Mos6502Variant.CMOS_65C02
                    ? setMemoryBitZeroPage65C02(opcodeAddress, 0) : storeAccumulatorAndXZeroPage();
            case 0x88 -> decrementY();
            case 0x89 -> variant == Mos6502Variant.CMOS_65C02
                    ? bitImmediate65C02(opcodeAddress) : nopImmediate();
            case 0x8A -> transferXToAccumulator();
            case 0x8C -> storeYAbsolute();
            case 0x8D -> storeAccumulatorAbsolute();
            case 0x8E -> storeXAbsolute();
            case 0x8F -> {
                requireNmos(0x8F, opcodeAddress);
                yield storeAccumulatorAndXAbsolute();
            }
            case 0x90 -> branchIf(!registers.flagSet(Mos6502Registers.FLAG_C));
            case 0x91 -> storeAccumulatorIndirectY();
            case 0x92 -> storeAccumulatorZeroPageIndirect65C02(opcodeAddress);
            case 0x94 -> storeYZeroPageX();
            case 0x95 -> storeAccumulatorZeroPageX();
            case 0x96 -> storeXZeroPageY();
            case 0x97 -> variant == Mos6502Variant.CMOS_65C02
                    ? setMemoryBitZeroPage65C02(opcodeAddress, 1) : storeAccumulatorAndXZeroPageY();
            case 0x98 -> transferYToAccumulator();
            case 0x99 -> storeAccumulatorAbsoluteY();
            case 0x9A -> transferXToStackPointer();
            case 0x9C -> storeZeroAbsolute65C02(opcodeAddress);
            case 0x9D -> storeAccumulatorAbsoluteX();
            case 0x9E -> storeZeroAbsoluteX65C02(opcodeAddress);
            case 0xA0 -> loadYImmediate();
            case 0xA1 -> loadAccumulatorIndirectX();
            case 0xA2 -> loadXImmediate();
            case 0xA3 -> {
                requireNmos(0xA3, opcodeAddress);
                yield loadAccumulatorAndXIndirectX();
            }
            case 0xA4 -> loadYZeroPage();
            case 0xA5 -> loadAccumulatorZeroPage();
            case 0xA6 -> loadXZeroPage();
            case 0xA7 -> variant == Mos6502Variant.CMOS_65C02
                    ? setMemoryBitZeroPage65C02(opcodeAddress, 2) : loadAccumulatorAndXZeroPage();
            case 0xA8 -> transferAccumulatorToY();
            case 0xA9 -> loadAccumulatorImmediate();
            case 0xAA -> transferAccumulatorToX();
            case 0xAC -> loadYAbsolute();
            case 0xAD -> loadAccumulatorAbsolute();
            case 0xAE -> loadXAbsolute();
            case 0xAF -> {
                requireNmos(0xAF, opcodeAddress);
                yield loadAccumulatorAndXAbsolute();
            }
            case 0xB0 -> branchIf(registers.flagSet(Mos6502Registers.FLAG_C));
            case 0xB1 -> loadAccumulatorIndirectY();
            case 0xB3 -> {
                requireNmos(0xB3, opcodeAddress);
                yield loadAccumulatorAndXIndirectY();
            }
            case 0xB4 -> loadYZeroPageX();
            case 0xB5 -> loadAccumulatorZeroPageX();
            case 0xB6 -> loadXZeroPageY();
            case 0xB7 -> variant == Mos6502Variant.CMOS_65C02
                    ? setMemoryBitZeroPage65C02(opcodeAddress, 3) : loadAccumulatorAndXZeroPageY();
            case 0xB8 -> clearFlag(Mos6502Registers.FLAG_V);
            case 0xB9 -> loadAccumulatorAbsoluteY();
            case 0xBA -> transferStackPointerToX();
            case 0xBC -> loadYAbsoluteX();
            case 0xBD -> loadAccumulatorAbsoluteX();
            case 0xBE -> loadXAbsoluteY();
            case 0xBF -> {
                requireNmos(0xBF, opcodeAddress);
                yield loadAccumulatorAndXAbsoluteY();
            }
            case 0xC0 -> compareYImmediate();
            case 0xC1 -> compareAccumulatorIndirectX();
            case 0xC2 -> {
                requireNmos(0xC2, opcodeAddress);
                yield nopImmediate();
            }
            case 0xC3 -> {
                requireNmos(0xC3, opcodeAddress);
                yield decrementCompareIndirectX();
            }
            case 0xC4 -> compareYZeroPage();
            case 0xC5 -> compareAccumulatorZeroPage();
            case 0xC6 -> decrementZeroPage();
            case 0xC7 -> variant == Mos6502Variant.CMOS_65C02
                    ? setMemoryBitZeroPage65C02(opcodeAddress, 4) : decrementCompareZeroPage();
            case 0xC8 -> incrementY();
            case 0xC9 -> compareAccumulatorImmediate();
            case 0xCA -> decrementX();
            case 0xCB -> {
                requireNmos(0xCB, opcodeAddress);
                yield subtractImmediateFromAccumulatorAndX();
            }
            case 0xCC -> compareYAbsolute();
            case 0xCD -> compareAccumulatorAbsolute();
            case 0xCE -> decrementAbsolute();
            case 0xCF -> {
                requireNmos(0xCF, opcodeAddress);
                yield decrementCompareAbsolute();
            }
            case 0xD0 -> branchIf(!registers.flagSet(Mos6502Registers.FLAG_Z));
            case 0xD1 -> compareAccumulatorIndirectY();
            case 0xD3 -> {
                requireNmos(0xD3, opcodeAddress);
                yield decrementCompareIndirectY();
            }
            case 0xD4 -> {
                requireNmos(0xD4, opcodeAddress);
                yield nopZeroPageX();
            }
            case 0xD5 -> compareAccumulatorZeroPageX();
            case 0xD6 -> decrementZeroPageX();
            case 0xD7 -> variant == Mos6502Variant.CMOS_65C02
                    ? setMemoryBitZeroPage65C02(opcodeAddress, 5) : decrementCompareZeroPageX();
            case 0xD8 -> clearFlag(Mos6502Registers.FLAG_D);
            case 0xD9 -> compareAccumulatorAbsoluteY();
            case 0xDA -> variant == Mos6502Variant.CMOS_65C02
                    ? pushX65C02(opcodeAddress) : nop();
            case 0xDB -> {
                requireNmos(0xDB, opcodeAddress);
                yield decrementCompareAbsoluteY();
            }
            case 0xDC -> {
                requireNmos(0xDC, opcodeAddress);
                yield nopAbsoluteX();
            }
            case 0xDD -> compareAccumulatorAbsoluteX();
            case 0xDE -> decrementAbsoluteX();
            case 0xDF -> {
                requireNmos(0xDF, opcodeAddress);
                yield decrementCompareAbsoluteX();
            }
            case 0xE0 -> compareXImmediate();
            case 0xE1 -> sbcIndirectX();
            case 0xE2 -> {
                requireNmos(0xE2, opcodeAddress);
                yield nopImmediate();
            }
            case 0xE3 -> {
                requireNmos(0xE3, opcodeAddress);
                yield incrementSubtractIndirectX();
            }
            case 0xE4 -> compareXZeroPage();
            case 0xE5 -> sbcZeroPage();
            case 0xE6 -> incrementZeroPage();
            case 0xE7 -> variant == Mos6502Variant.CMOS_65C02
                    ? setMemoryBitZeroPage65C02(opcodeAddress, 6) : incrementSubtractZeroPage();
            case 0xE8 -> incrementX();
            case 0xE9 -> sbcImmediate();
            case 0xEA -> 2;
            case 0xEB -> {
                requireNmos(0xEB, opcodeAddress);
                yield sbcImmediate();
            }
            case 0xEC -> compareXAbsolute();
            case 0xED -> sbcAbsolute();
            case 0xEE -> incrementAbsolute();
            case 0xEF -> {
                requireNmos(0xEF, opcodeAddress);
                yield incrementSubtractAbsolute();
            }
            case 0xF0 -> branchIf(registers.flagSet(Mos6502Registers.FLAG_Z));
            case 0xF1 -> sbcIndirectY();
            case 0xF3 -> {
                requireNmos(0xF3, opcodeAddress);
                yield incrementSubtractIndirectY();
            }
            case 0xF4 -> {
                requireNmos(0xF4, opcodeAddress);
                yield nopZeroPageX();
            }
            case 0xF5 -> sbcZeroPageX();
            case 0xF6 -> incrementZeroPageX();
            case 0xF7 -> variant == Mos6502Variant.CMOS_65C02
                    ? setMemoryBitZeroPage65C02(opcodeAddress, 7) : incrementSubtractZeroPageX();
            case 0xF8 -> setFlag(Mos6502Registers.FLAG_D);
            case 0xF9 -> sbcAbsoluteY();
            case 0xFA -> variant == Mos6502Variant.CMOS_65C02
                    ? pullX65C02(opcodeAddress) : nop();
            case 0xFB -> {
                requireNmos(0xFB, opcodeAddress);
                yield incrementSubtractAbsoluteY();
            }
            case 0xFC -> {
                requireNmos(0xFC, opcodeAddress);
                yield nopAbsoluteX();
            }
            case 0xFD -> sbcAbsoluteX();
            case 0xFE -> incrementAbsoluteX();
            case 0xFF -> {
                requireNmos(0xFF, opcodeAddress);
                yield incrementSubtractAbsoluteX();
            }
            default -> illegalOpcode(opcode, opcodeAddress);
        };
    }

    private int branchAlways65C02(int opcodeAddress) {
        require65C02(0x80, opcodeAddress);
        return branchIf(true);
    }

    private int nopImmediate65C02(int opcodeAddress) {
        require65C02(0x02, opcodeAddress);
        registers.incrementPc(1);
        return 2;
    }

    private int nop() {
        return 2;
    }

    private int nopImmediate() {
        fetchImmediate8();
        return 2;
    }

    private int nopZeroPage() {
        readZeroPageOperand();
        return 3;
    }

    private int nopZeroPageX() {
        bus.readMemory(fetchZeroPageXAddress());
        return 4;
    }

    private int nopAbsolute() {
        bus.readMemory(fetchImmediate16());
        return 4;
    }

    private int nopAbsoluteX() {
        int address = fetchAbsoluteXAddress();
        bus.readMemory(indexedValue(address));
        return 4 + (crossedPage(address) ? 1 : 0);
    }

    private int resetMemoryBitZeroPage65C02(int opcodeAddress, int bit) {
        return modifyMemoryBitZeroPage65C02(opcodeAddress, bit, false);
    }

    private int setMemoryBitZeroPage65C02(int opcodeAddress, int bit) {
        return modifyMemoryBitZeroPage65C02(opcodeAddress, bit, true);
    }

    private int modifyMemoryBitZeroPage65C02(int opcodeAddress, int bit, boolean set) {
        int opcode = (set ? 0x87 : 0x07) + (bit << 4);
        require65C02(opcode, opcodeAddress);
        int address = fetchImmediate8();
        int mask = 1 << bit;
        int value = bus.readMemory(address);
        int result = set ? (value | mask) : (value & ~mask);
        bus.writeMemory(address, result);
        return 5;
    }

    private int testAndSetBitsZeroPage65C02(int opcodeAddress) {
        require65C02(0x04, opcodeAddress);
        testAndModifyBits(fetchImmediate8(), true);
        return 5;
    }

    private int testAndSetBitsAbsolute65C02(int opcodeAddress) {
        require65C02(0x0C, opcodeAddress);
        testAndModifyBits(fetchImmediate16(), true);
        return 6;
    }

    private int testAndResetBitsZeroPage65C02(int opcodeAddress) {
        require65C02(0x14, opcodeAddress);
        testAndModifyBits(fetchImmediate8(), false);
        return 5;
    }

    private int testAndResetBitsAbsolute65C02(int opcodeAddress) {
        require65C02(0x1C, opcodeAddress);
        testAndModifyBits(fetchImmediate16(), false);
        return 6;
    }

    private void testAndModifyBits(int address, boolean set) {
        int memory = bus.readMemory(address) & 0xFF;
        registers.setFlag(Mos6502Registers.FLAG_Z, (registers.a() & memory) == 0);
        int result = set ? (memory | registers.a()) : (memory & ~registers.a());
        bus.writeMemory(address, result);
    }

    private int illegalOpcode(int opcode, int opcodeAddress) {
        registers.setPc(opcodeAddress);
        throw new IllegalStateException(
                "Illegal MOS 6502 opcode 0x%02X at 0x%04X".formatted(opcode & 0xFF, opcodeAddress & 0xFFFF)
        );
    }

    private void require65C02(int opcode, int opcodeAddress) {
        if (variant != Mos6502Variant.CMOS_65C02) {
            illegalOpcode(opcode, opcodeAddress);
        }
    }

    private void requireNmos(int opcode, int opcodeAddress) {
        if (variant != Mos6502Variant.NMOS_6502) {
            illegalOpcode(opcode, opcodeAddress);
        }
    }

    private int brk() {
        registers.incrementPc(1);
        pushWord(registers.pc());
        pushStatus(true);
        registers.setFlag(Mos6502Registers.FLAG_I, true);
        registers.setPc(readVector(IRQ_VECTOR));
        return 7;
    }

    private int serviceInterrupt(int vectorAddress) {
        pushWord(registers.pc());
        pushStatus(false);
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

    private int jumpAbsoluteIndexedIndirect65C02(int opcodeAddress) {
        require65C02(0x7C, opcodeAddress);
        int pointer = (fetchImmediate16() + registers.x()) & 0xFFFF;
        registers.setPc(readVector(pointer));
        return 6;
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
        return loadAccumulator(fetchImmediate8(), 2);
    }

    private int loadAccumulatorZeroPage() {
        return loadAccumulator(readZeroPageOperand(), 3);
    }

    private int loadAccumulatorZeroPageX() {
        return loadAccumulator(bus.readMemory(fetchZeroPageXAddress()), 4);
    }

    private int loadAccumulatorIndirectX() {
        return loadAccumulator(bus.readMemory(fetchIndirectXAddress()), 6);
    }

    private int loadAccumulatorIndirectY() {
        int address = fetchIndirectYAddress();
        return loadAccumulator(
                bus.readMemory(indexedValue(address)),
                5 + (crossedPage(address) ? 1 : 0)
        );
    }

    private int loadAccumulatorAbsolute() {
        return loadAccumulator(bus.readMemory(fetchImmediate16()), 4);
    }

    private int loadAccumulatorAbsoluteY() {
        int address = fetchAbsoluteYAddress();
        return loadAccumulator(
                bus.readMemory(indexedValue(address)),
                4 + (crossedPage(address) ? 1 : 0)
        );
    }

    private int loadAccumulatorAbsoluteX() {
        int address = fetchAbsoluteXAddress();
        return loadAccumulator(
                bus.readMemory(indexedValue(address)),
                4 + (crossedPage(address) ? 1 : 0)
        );
    }

    private int loadAccumulator(int value, int cycles) {
        registers.setA(value);
        registers.updateZeroAndNegative(registers.a());
        return cycles;
    }

    private int loadAccumulatorAndXZeroPage() {
        return loadAccumulatorAndX(readZeroPageOperand(), 3);
    }

    private int loadAccumulatorAndXZeroPageY() {
        return loadAccumulatorAndX(bus.readMemory(fetchZeroPageYAddress()), 4);
    }

    private int loadAccumulatorAndXAbsolute() {
        return loadAccumulatorAndX(bus.readMemory(fetchImmediate16()), 4);
    }

    private int loadAccumulatorAndXAbsoluteY() {
        int address = fetchAbsoluteYAddress();
        return loadAccumulatorAndX(
                bus.readMemory(indexedValue(address)),
                4 + (crossedPage(address) ? 1 : 0)
        );
    }

    private int loadAccumulatorAndXIndirectX() {
        return loadAccumulatorAndX(bus.readMemory(fetchIndirectXAddress()), 6);
    }

    private int loadAccumulatorAndXIndirectY() {
        int address = fetchIndirectYAddress();
        return loadAccumulatorAndX(
                bus.readMemory(indexedValue(address)),
                5 + (crossedPage(address) ? 1 : 0)
        );
    }

    private int loadAccumulatorAndX(int value, int cycles) {
        registers.setA(value);
        registers.setX(value);
        registers.updateZeroAndNegative(value);
        return cycles;
    }

    private int loadXImmediate() {
        return loadX(fetchImmediate8(), 2);
    }

    private int loadXZeroPage() {
        return loadX(readZeroPageOperand(), 3);
    }

    private int loadXZeroPageY() {
        return loadX(bus.readMemory(fetchZeroPageYAddress()), 4);
    }

    private int loadXAbsolute() {
        return loadX(bus.readMemory(fetchImmediate16()), 4);
    }

    private int loadXAbsoluteY() {
        int address = fetchAbsoluteYAddress();
        return loadX(bus.readMemory(indexedValue(address)), 4 + (crossedPage(address) ? 1 : 0));
    }

    private int loadX(int value, int cycles) {
        registers.setX(value);
        registers.updateZeroAndNegative(registers.x());
        return cycles;
    }

    private int loadYImmediate() {
        return loadY(fetchImmediate8(), 2);
    }

    private int loadYZeroPage() {
        return loadY(readZeroPageOperand(), 3);
    }

    private int loadYZeroPageX() {
        return loadY(bus.readMemory(fetchZeroPageXAddress()), 4);
    }

    private int loadYAbsolute() {
        return loadY(bus.readMemory(fetchImmediate16()), 4);
    }

    private int loadYAbsoluteX() {
        int address = fetchAbsoluteXAddress();
        return loadY(bus.readMemory(indexedValue(address)), 4 + (crossedPage(address) ? 1 : 0));
    }

    private int loadY(int value, int cycles) {
        registers.setY(value);
        registers.updateZeroAndNegative(registers.y());
        return cycles;
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
        bus.writeMemory(indexedValue(fetchIndirectYAddress()), registers.a());
        return 6;
    }

    private int storeAccumulatorZeroPageIndirect65C02(int opcodeAddress) {
        require65C02(0x92, opcodeAddress);
        bus.writeMemory(fetchZeroPageIndirectAddress(), registers.a());
        return 5;
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
        bus.writeMemory(indexedValue(fetchAbsoluteXAddress()), registers.a());
        return 5;
    }

    private int storeAccumulatorAbsoluteY() {
        bus.writeMemory(indexedValue(fetchAbsoluteYAddress()), registers.a());
        return 5;
    }

    private int storeAccumulatorAndXZeroPage() {
        bus.writeMemory(fetchImmediate8(), registers.a() & registers.x());
        return 3;
    }

    private int storeAccumulatorAndXZeroPageY() {
        bus.writeMemory(fetchZeroPageYAddress(), registers.a() & registers.x());
        return 4;
    }

    private int storeAccumulatorAndXAbsolute() {
        bus.writeMemory(fetchImmediate16(), registers.a() & registers.x());
        return 4;
    }

    private int storeAccumulatorAndXIndirectX() {
        bus.writeMemory(fetchIndirectXAddress(), registers.a() & registers.x());
        return 6;
    }

    private int storeZeroZeroPage65C02(int opcodeAddress) {
        require65C02(0x64, opcodeAddress);
        bus.writeMemory(fetchImmediate8(), 0x00);
        return 3;
    }

    private int storeZeroZeroPageX65C02(int opcodeAddress) {
        require65C02(0x74, opcodeAddress);
        bus.writeMemory(fetchZeroPageXAddress(), 0x00);
        return 4;
    }

    private int storeZeroAbsolute65C02(int opcodeAddress) {
        require65C02(0x9C, opcodeAddress);
        bus.writeMemory(fetchImmediate16(), 0x00);
        return 4;
    }

    private int storeZeroAbsoluteX65C02(int opcodeAddress) {
        require65C02(0x9E, opcodeAddress);
        bus.writeMemory(indexedValue(fetchAbsoluteXAddress()), 0x00);
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

    private int pushX65C02(int opcodeAddress) {
        require65C02(0xDA, opcodeAddress);
        push8(registers.x());
        return 3;
    }

    private int pushY65C02(int opcodeAddress) {
        require65C02(0x5A, opcodeAddress);
        push8(registers.y());
        return 3;
    }

    private int pullAccumulator() {
        registers.setA(pop8());
        registers.updateZeroAndNegative(registers.a());
        return 4;
    }

    private int pullX65C02(int opcodeAddress) {
        require65C02(0xFA, opcodeAddress);
        registers.setX(pop8());
        registers.updateZeroAndNegative(registers.x());
        return 4;
    }

    private int pullY65C02(int opcodeAddress) {
        require65C02(0x7A, opcodeAddress);
        registers.setY(pop8());
        registers.updateZeroAndNegative(registers.y());
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
        bus.writeMemory(address, shiftRight(bus.readMemory(address)));
        return 5;
    }

    private int shiftRightZeroPageX() {
        int address = fetchZeroPageXAddress();
        bus.writeMemory(address, shiftRight(bus.readMemory(address)));
        return 6;
    }

    private int shiftRightAbsolute() {
        int address = fetchImmediate16();
        bus.writeMemory(address, shiftRight(bus.readMemory(address) & 0xFF) & 0xFF);
        return 6;
    }

    private int shiftRightAbsoluteX() {
        int address = indexedValue(fetchAbsoluteXAddress());
        bus.writeMemory(address, shiftRight(bus.readMemory(address) & 0xFF) & 0xFF);
        return 7;
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
        int address = fetchImmediate16();
        bus.writeMemory(address, shiftLeft(bus.readMemory(address) & 0xFF) & 0xFF);
        return 6;
    }

    private int shiftLeftAbsoluteX() {
        int address = indexedValue(fetchAbsoluteXAddress());
        bus.writeMemory(address, shiftLeft(bus.readMemory(address) & 0xFF) & 0xFF);
        return 7;
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
        int address = fetchImmediate16();
        bus.writeMemory(address, rotateLeft(bus.readMemory(address) & 0xFF) & 0xFF);
        return 6;
    }

    private int rotateLeftAbsoluteX() {
        int address = indexedValue(fetchAbsoluteXAddress());
        bus.writeMemory(address, rotateLeft(bus.readMemory(address) & 0xFF) & 0xFF);
        return 7;
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
        int address = fetchImmediate16();
        bus.writeMemory(address, rotateRight(bus.readMemory(address) & 0xFF) & 0xFF);
        return 6;
    }

    private int rotateRightAbsoluteX() {
        int address = indexedValue(fetchAbsoluteXAddress());
        bus.writeMemory(address, rotateRight(bus.readMemory(address) & 0xFF) & 0xFF);
        return 7;
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

    private int shiftLeftOrZeroPage() {
        return shiftLeftOrMemory(fetchImmediate8(), 5);
    }

    private int shiftLeftOrZeroPageX() {
        return shiftLeftOrMemory(fetchZeroPageXAddress(), 6);
    }

    private int shiftLeftOrAbsolute() {
        return shiftLeftOrMemory(fetchImmediate16(), 6);
    }

    private int shiftLeftOrAbsoluteX() {
        return shiftLeftOrMemory(indexedValue(fetchAbsoluteXAddress()), 7);
    }

    private int shiftLeftOrAbsoluteY() {
        return shiftLeftOrMemory(indexedValue(fetchAbsoluteYAddress()), 7);
    }

    private int shiftLeftOrIndirectX() {
        return shiftLeftOrMemory(fetchIndirectXAddress(), 8);
    }

    private int shiftLeftOrIndirectY() {
        return shiftLeftOrMemory(indexedValue(fetchIndirectYAddress()), 8);
    }

    private int shiftLeftOrMemory(int address, int cycles) {
        int result = shiftLeft(bus.readMemory(address));
        bus.writeMemory(address, result);
        return orAccumulator(result, cycles);
    }

    private int rotateLeftAndZeroPage() {
        return rotateLeftAndMemory(fetchImmediate8(), 5);
    }

    private int rotateLeftAndZeroPageX() {
        return rotateLeftAndMemory(fetchZeroPageXAddress(), 6);
    }

    private int rotateLeftAndAbsolute() {
        return rotateLeftAndMemory(fetchImmediate16(), 6);
    }

    private int rotateLeftAndAbsoluteX() {
        return rotateLeftAndMemory(indexedValue(fetchAbsoluteXAddress()), 7);
    }

    private int rotateLeftAndAbsoluteY() {
        return rotateLeftAndMemory(indexedValue(fetchAbsoluteYAddress()), 7);
    }

    private int rotateLeftAndIndirectX() {
        return rotateLeftAndMemory(fetchIndirectXAddress(), 8);
    }

    private int rotateLeftAndIndirectY() {
        return rotateLeftAndMemory(indexedValue(fetchIndirectYAddress()), 8);
    }

    private int rotateLeftAndMemory(int address, int cycles) {
        int result = rotateLeft(bus.readMemory(address));
        bus.writeMemory(address, result);
        return andAccumulator(result, cycles);
    }

    private int shiftRightExclusiveOrZeroPage() {
        return shiftRightExclusiveOrMemory(fetchImmediate8(), 5);
    }

    private int shiftRightExclusiveOrZeroPageX() {
        return shiftRightExclusiveOrMemory(fetchZeroPageXAddress(), 6);
    }

    private int shiftRightExclusiveOrAbsolute() {
        return shiftRightExclusiveOrMemory(fetchImmediate16(), 6);
    }

    private int shiftRightExclusiveOrAbsoluteX() {
        return shiftRightExclusiveOrMemory(indexedValue(fetchAbsoluteXAddress()), 7);
    }

    private int shiftRightExclusiveOrAbsoluteY() {
        return shiftRightExclusiveOrMemory(indexedValue(fetchAbsoluteYAddress()), 7);
    }

    private int shiftRightExclusiveOrIndirectX() {
        return shiftRightExclusiveOrMemory(fetchIndirectXAddress(), 8);
    }

    private int shiftRightExclusiveOrIndirectY() {
        return shiftRightExclusiveOrMemory(indexedValue(fetchIndirectYAddress()), 8);
    }

    private int shiftRightExclusiveOrMemory(int address, int cycles) {
        int result = shiftRight(bus.readMemory(address));
        bus.writeMemory(address, result);
        return exclusiveOr(result, cycles);
    }

    private int rotateRightAddZeroPage() {
        return rotateRightAddMemory(fetchImmediate8(), 5);
    }

    private int rotateRightAddZeroPageX() {
        return rotateRightAddMemory(fetchZeroPageXAddress(), 6);
    }

    private int rotateRightAddAbsolute() {
        return rotateRightAddMemory(fetchImmediate16(), 6);
    }

    private int rotateRightAddAbsoluteX() {
        return rotateRightAddMemory(indexedValue(fetchAbsoluteXAddress()), 7);
    }

    private int rotateRightAddAbsoluteY() {
        return rotateRightAddMemory(indexedValue(fetchAbsoluteYAddress()), 7);
    }

    private int rotateRightAddIndirectX() {
        return rotateRightAddMemory(fetchIndirectXAddress(), 8);
    }

    private int rotateRightAddIndirectY() {
        return rotateRightAddMemory(indexedValue(fetchIndirectYAddress()), 8);
    }

    private int rotateRightAddMemory(int address, int cycles) {
        int result = rotateRight(bus.readMemory(address));
        bus.writeMemory(address, result);
        addWithCarry(result);
        return cycles;
    }

    private int decrementCompareZeroPage() {
        return decrementCompareMemory(fetchImmediate8(), 5);
    }

    private int decrementCompareZeroPageX() {
        return decrementCompareMemory(fetchZeroPageXAddress(), 6);
    }

    private int decrementCompareAbsolute() {
        return decrementCompareMemory(fetchImmediate16(), 6);
    }

    private int decrementCompareAbsoluteX() {
        return decrementCompareMemory(indexedValue(fetchAbsoluteXAddress()), 7);
    }

    private int decrementCompareAbsoluteY() {
        return decrementCompareMemory(indexedValue(fetchAbsoluteYAddress()), 7);
    }

    private int decrementCompareIndirectX() {
        return decrementCompareMemory(fetchIndirectXAddress(), 8);
    }

    private int decrementCompareIndirectY() {
        return decrementCompareMemory(indexedValue(fetchIndirectYAddress()), 8);
    }

    private int decrementCompareMemory(int address, int cycles) {
        int value = (bus.readMemory(address) - 1) & 0xFF;
        bus.writeMemory(address, value);
        compare(registers.a(), value);
        return cycles;
    }

    private int incrementSubtractZeroPage() {
        return incrementSubtractMemory(fetchImmediate8(), 5);
    }

    private int incrementSubtractZeroPageX() {
        return incrementSubtractMemory(fetchZeroPageXAddress(), 6);
    }

    private int incrementSubtractAbsolute() {
        return incrementSubtractMemory(fetchImmediate16(), 6);
    }

    private int incrementSubtractAbsoluteX() {
        return incrementSubtractMemory(indexedValue(fetchAbsoluteXAddress()), 7);
    }

    private int incrementSubtractAbsoluteY() {
        return incrementSubtractMemory(indexedValue(fetchAbsoluteYAddress()), 7);
    }

    private int incrementSubtractIndirectX() {
        return incrementSubtractMemory(fetchIndirectXAddress(), 8);
    }

    private int incrementSubtractIndirectY() {
        return incrementSubtractMemory(indexedValue(fetchIndirectYAddress()), 8);
    }

    private int incrementSubtractMemory(int address, int cycles) {
        int value = (bus.readMemory(address) + 1) & 0xFF;
        bus.writeMemory(address, value);
        subtractWithCarry(value);
        return cycles;
    }

    private int decrementZeroPage() {
        return decrementMemory(fetchImmediate8(), 5);
    }

    private int decrementZeroPageX() {
        return decrementMemory(fetchZeroPageXAddress(), 6);
    }

    private int decrementAbsolute() {
        return decrementMemory(fetchImmediate16(), 6);
    }

    private int decrementAbsoluteX() {
        return decrementMemory(indexedValue(fetchAbsoluteXAddress()), 7);
    }

    private int decrementMemory(int address, int cycles) {
        int value = (bus.readMemory(address) - 1) & 0xFF;
        bus.writeMemory(address, value);
        registers.updateZeroAndNegative(value);
        return cycles;
    }

    private int decrementAccumulator65C02(int opcodeAddress) {
        require65C02(0x3A, opcodeAddress);
        registers.setA((registers.a() - 1) & 0xFF);
        registers.updateZeroAndNegative(registers.a());
        return 2;
    }

    private int incrementZeroPage() {
        return incrementMemory(fetchImmediate8(), 5);
    }

    private int incrementZeroPageX() {
        return incrementMemory(fetchZeroPageXAddress(), 6);
    }

    private int incrementAbsolute() {
        return incrementMemory(fetchImmediate16(), 6);
    }

    private int incrementAbsoluteX() {
        return incrementMemory(indexedValue(fetchAbsoluteXAddress()), 7);
    }

    private int incrementMemory(int address, int cycles) {
        int value = (bus.readMemory(address) + 1) & 0xFF;
        bus.writeMemory(address, value);
        registers.updateZeroAndNegative(value);
        return cycles;
    }

    private int incrementAccumulator65C02(int opcodeAddress) {
        require65C02(0x1A, opcodeAddress);
        registers.setA((registers.a() + 1) & 0xFF);
        registers.updateZeroAndNegative(registers.a());
        return 2;
    }

    private int andAccumulatorImmediate() {
        return andAccumulator(fetchImmediate8(), 2);
    }

    private int andAccumulatorZeroPage() {
        return andAccumulator(readZeroPageOperand(), 3);
    }

    private int andAccumulatorZeroPageX() {
        return andAccumulator(bus.readMemory(fetchZeroPageXAddress()), 4);
    }

    private int andAccumulatorAbsolute() {
        return andAccumulator(bus.readMemory(fetchImmediate16()), 4);
    }

    private int andAccumulatorAbsoluteX() {
        int address = fetchAbsoluteXAddress();
        return andAccumulator(bus.readMemory(indexedValue(address)), 4 + (crossedPage(address) ? 1 : 0));
    }

    private int andAccumulatorAbsoluteY() {
        int address = fetchAbsoluteYAddress();
        return andAccumulator(bus.readMemory(indexedValue(address)), 4 + (crossedPage(address) ? 1 : 0));
    }

    private int andAccumulatorIndirectX() {
        return andAccumulator(bus.readMemory(fetchIndirectXAddress()), 6);
    }

    private int andAccumulatorIndirectY() {
        int address = fetchIndirectYAddress();
        return andAccumulator(bus.readMemory(indexedValue(address)), 5 + (crossedPage(address) ? 1 : 0));
    }

    private int andAccumulator(int value, int cycles) {
        registers.setA(registers.a() & value);
        registers.updateZeroAndNegative(registers.a());
        return cycles;
    }

    private int andAccumulatorSetCarryImmediate() {
        andAccumulator(fetchImmediate8(), 2);
        registers.setFlag(
                Mos6502Registers.FLAG_C,
                registers.flagSet(Mos6502Registers.FLAG_N)
        );
        return 2;
    }

    private int andAccumulatorShiftRightImmediate() {
        registers.setA(registers.a() & fetchImmediate8());
        return shiftRightAccumulator();
    }

    private int andAccumulatorRotateRightImmediate() {
        int result = rotateRight(registers.a() & fetchImmediate8());
        registers.setA(result);
        registers.setFlag(Mos6502Registers.FLAG_C, (result & 0x40) != 0);
        registers.setFlag(Mos6502Registers.FLAG_V, (((result >>> 6) ^ (result >>> 5)) & 0x01) != 0);
        return 2;
    }

    private int orAccumulatorImmediate() {
        return orAccumulator(fetchImmediate8(), 2);
    }

    private int orAccumulatorZeroPage() {
        return orAccumulator(readZeroPageOperand(), 3);
    }

    private int orAccumulatorZeroPageX() {
        return orAccumulator(bus.readMemory(fetchZeroPageXAddress()), 4);
    }

    private int orAccumulatorAbsolute() {
        return orAccumulator(bus.readMemory(fetchImmediate16()), 4);
    }

    private int orAccumulatorAbsoluteX() {
        int address = fetchAbsoluteXAddress();
        return orAccumulator(bus.readMemory(indexedValue(address)), 4 + (crossedPage(address) ? 1 : 0));
    }

    private int orAccumulatorAbsoluteY() {
        int address = fetchAbsoluteYAddress();
        return orAccumulator(bus.readMemory(indexedValue(address)), 4 + (crossedPage(address) ? 1 : 0));
    }

    private int orAccumulatorIndirectX() {
        return orAccumulator(bus.readMemory(fetchIndirectXAddress()), 6);
    }

    private int orAccumulatorIndirectY() {
        int address = fetchIndirectYAddress();
        return orAccumulator(bus.readMemory(indexedValue(address)), 5 + (crossedPage(address) ? 1 : 0));
    }

    private int orAccumulator(int value, int cycles) {
        registers.setA(registers.a() | value);
        registers.updateZeroAndNegative(registers.a());
        return cycles;
    }

    private int exclusiveOrImmediate() {
        return exclusiveOr(fetchImmediate8(), 2);
    }

    private int exclusiveOrZeroPage() {
        return exclusiveOr(readZeroPageOperand(), 3);
    }

    private int exclusiveOrZeroPageX() {
        return exclusiveOr(bus.readMemory(fetchZeroPageXAddress()), 4);
    }

    private int exclusiveOrAbsolute() {
        return exclusiveOr(bus.readMemory(fetchImmediate16()), 4);
    }

    private int exclusiveOrAbsoluteX() {
        int address = fetchAbsoluteXAddress();
        return exclusiveOr(bus.readMemory(indexedValue(address)), 4 + (crossedPage(address) ? 1 : 0));
    }

    private int exclusiveOrAbsoluteY() {
        int address = fetchAbsoluteYAddress();
        return exclusiveOr(bus.readMemory(indexedValue(address)), 4 + (crossedPage(address) ? 1 : 0));
    }

    private int exclusiveOrIndirectX() {
        return exclusiveOr(bus.readMemory(fetchIndirectXAddress()), 6);
    }

    private int exclusiveOrIndirectY() {
        int address = fetchIndirectYAddress();
        return exclusiveOr(bus.readMemory(indexedValue(address)), 5 + (crossedPage(address) ? 1 : 0));
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
        int address = fetchAbsoluteXAddress();
        addWithCarry(bus.readMemory(indexedValue(address)));
        return 4 + (crossedPage(address) ? 1 : 0);
    }

    private int adcAbsoluteY() {
        int address = fetchAbsoluteYAddress();
        addWithCarry(bus.readMemory(indexedValue(address)));
        return 4 + (crossedPage(address) ? 1 : 0);
    }

    private int adcIndirectY() {
        int address = fetchIndirectYAddress();
        addWithCarry(bus.readMemory(indexedValue(address)));
        return 5 + (crossedPage(address) ? 1 : 0);
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
        int address = fetchAbsoluteXAddress();
        subtractWithCarry(bus.readMemory(indexedValue(address)));
        return 4 + (crossedPage(address) ? 1 : 0);
    }

    private int sbcAbsoluteY() {
        int address = fetchAbsoluteYAddress();
        subtractWithCarry(bus.readMemory(indexedValue(address)));
        return 4 + (crossedPage(address) ? 1 : 0);
    }

    private int sbcIndirectY() {
        int address = fetchIndirectYAddress();
        subtractWithCarry(bus.readMemory(indexedValue(address)));
        return 5 + (crossedPage(address) ? 1 : 0);
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
        int address = fetchAbsoluteXAddress();
        compare(registers.a(), bus.readMemory(indexedValue(address)));
        return 4 + (crossedPage(address) ? 1 : 0);
    }

    private int compareAccumulatorAbsoluteY() {
        int address = fetchAbsoluteYAddress();
        compare(registers.a(), bus.readMemory(indexedValue(address)));
        return 4 + (crossedPage(address) ? 1 : 0);
    }

    private int compareAccumulatorIndirectX() {
        compare(registers.a(), bus.readMemory(fetchIndirectXAddress()));
        return 6;
    }

    private int compareAccumulatorIndirectY() {
        int address = fetchIndirectYAddress();
        compare(registers.a(), bus.readMemory(indexedValue(address)));
        return 5 + (crossedPage(address) ? 1 : 0);
    }

    private int compareAccumulatorImmediate() {
        compare(registers.a(), fetchImmediate8());
        return 2;
    }

    private int subtractImmediateFromAccumulatorAndX() {
        int value = registers.a() & registers.x();
        int operand = fetchImmediate8();
        registers.setX(value - operand);
        compare(value, operand);
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

    private int bitImmediate65C02(int opcodeAddress) {
        require65C02(0x89, opcodeAddress);
        registers.setFlag(Mos6502Registers.FLAG_Z, (registers.a() & fetchImmediate8()) == 0);
        return 2;
    }

    private int bitZeroPageX65C02(int opcodeAddress) {
        require65C02(0x34, opcodeAddress);
        bit(bus.readMemory(fetchZeroPageXAddress()) & 0xFF);
        return 4;
    }

    private int bitAbsoluteX65C02(int opcodeAddress) {
        require65C02(0x3C, opcodeAddress);
        bit(bus.readMemory(indexedValue(fetchAbsoluteXAddress())) & 0xFF);
        return 4;
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

    private int fetchIndirectYAddress() {
        int zeroPageAddress = fetchImmediate8();
        int base = readZeroPageWord(zeroPageAddress);
        int value = (base + registers.y()) & 0xFFFF;
        return value | (((base ^ value) & 0xFF00) != 0 ? 0x10000 : 0);
    }

    private int fetchZeroPageIndirectAddress() {
        return readZeroPageWord(fetchImmediate8());
    }

    private int fetchAbsoluteYAddress() {
        int base = fetchImmediate16();
        int value = (base + registers.y()) & 0xFFFF;
        return value | (((base ^ value) & 0xFF00) != 0 ? 0x10000 : 0);
    }

    private int fetchAbsoluteXAddress() {
        int base = fetchImmediate16();
        int value = (base + registers.x()) & 0xFFFF;
        return value | (((base ^ value) & 0xFF00) != 0 ? 0x10000 : 0);
    }

    private static int indexedValue(int encoded) {
        return encoded & 0xFFFF;
    }

    private static boolean crossedPage(int encoded) {
        return (encoded & 0x10000) != 0;
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
            int packed = decimalAdd(accumulator, value8, carryIn);
            boolean carry = (packed & 0x100) != 0;
            registers.setFlag(Mos6502Registers.FLAG_C, carry);
            result8 = packed & 0xFF;
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

    private static int decimalAdd(int accumulator, int value, int carryIn) {
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
        int result = ((high << 4) | (low & 0x0F)) & 0xFF;
        return result | (carry ? 0x100 : 0);
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
