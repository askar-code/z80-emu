package dev.z8emu.cpu.z80;

import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.cpu.Cpu;
import java.util.Objects;

public final class Z80Cpu implements Cpu {
    private static final int ALU_ADD = 0;
    private static final int ALU_ADC = 1;
    private static final int ALU_SUB = 2;
    private static final int ALU_SBC = 3;
    private static final int ALU_AND = 4;
    private static final int ALU_XOR = 5;
    private static final int ALU_OR = 6;
    private static final int ALU_CP = 7;

    private static final int INDEX_NONE = 0;
    private static final int INDEX_IX = 1;
    private static final int INDEX_IY = 2;
    private static final int IN_IMMEDIATE_PHASE_TSTATES = Integer.getInteger("z8emu.inPortPhaseImmediate", 7);
    private static final int IN_C_PHASE_TSTATES = Integer.getInteger("z8emu.inPortPhaseC", 8);

    private final CpuBus bus;
    private final Z80Registers registers = new Z80Registers();

    private boolean halted;
    private boolean pendingInterrupt;
    private boolean pendingNmi;
    private int interruptEnableDelay;

    public Z80Cpu(CpuBus bus) {
        this.bus = Objects.requireNonNull(bus, "bus");
        reset();
    }

    public Z80Registers registers() {
        return registers;
    }

    @Override
    public void reset() {
        registers.reset();
        halted = false;
        pendingInterrupt = false;
        pendingNmi = false;
        interruptEnableDelay = 0;
    }

    @Override
    public void requestMaskableInterrupt() {
        pendingInterrupt = true;
    }

    @Override
    public void requestNonMaskableInterrupt() {
        pendingNmi = true;
    }

    public boolean isHalted() {
        return halted;
    }

    @Override
    public int runInstruction() {
        int tStates;

        if (pendingNmi) {
            tStates = serviceNonMaskableInterrupt();
        } else if (pendingInterrupt && registers.iff1() && interruptEnableDelay == 0) {
            tStates = serviceMaskableInterrupt();
        } else if (halted && pendingInterrupt) {
            tStates = releaseHaltOnIgnoredMaskableInterrupt();
        } else if (halted) {
            tStates = executeHaltCycle();
        } else {
            tStates = executeOpcode(fetchOpcode());
        }

        applyDeferredInterruptEnable();
        return tStates;
    }

    private int fetchOpcode() {
        int opcodeAddress = registers.pc();
        int opcode = bus.fetchOpcode(opcodeAddress) & 0xFF;
        registers.onInstructionFetch();
        registers.incrementPc(1);
        bus.onRefresh(registers.ir());
        return opcode;
    }

    private int executeHaltCycle() {
        bus.fetchOpcode(registers.pc());
        registers.onInstructionFetch();
        bus.onRefresh(registers.ir());
        return 4;
    }

    private int releaseHaltOnIgnoredMaskableInterrupt() {
        pendingInterrupt = false;
        halted = false;
        bus.fetchOpcode(registers.pc());
        registers.onInstructionFetch();
        bus.onRefresh(registers.ir());
        return 4;
    }

    private int serviceNonMaskableInterrupt() {
        pendingNmi = false;
        halted = false;

        int returnAddress = registers.pc();
        boolean previousIff1 = registers.iff1();

        registers.onInstructionFetch();
        bus.onRefresh(registers.ir());
        pushWord(returnAddress);
        registers.setIff1(false);
        registers.setIff2(previousIff1);
        interruptEnableDelay = 0;
        registers.setPc(0x0066);
        return 11;
    }

    private int serviceMaskableInterrupt() {
        pendingInterrupt = false;
        halted = false;

        int returnAddress = registers.pc();
        int interruptMode = registers.interruptMode();

        registers.onInstructionFetch();
        bus.onRefresh(registers.ir());
        registers.setIff1(false);
        registers.setIff2(false);
        interruptEnableDelay = 0;

        if (interruptMode == 2) {
            int vector = bus.acknowledgeInterrupt() & 0xFF;
            pushWord(returnAddress);
            int target = readMemory16(((registers.i() << 8) | vector) & 0xFFFF);
            registers.setPc(target);
            return 19;
        }

        bus.acknowledgeInterrupt();
        pushWord(returnAddress);
        registers.setPc(0x0038);
        return 13;
    }

    private void applyDeferredInterruptEnable() {
        if (interruptEnableDelay > 0) {
            interruptEnableDelay--;
        }
    }

    private int executeOpcode(int opcode) {
        if (opcode == 0xDD || opcode == 0xFD) {
            return executeIndexedPrefix(opcode == 0xDD ? INDEX_IX : INDEX_IY);
        }
        if (opcode == 0xCB) {
            return executeCbPrefix(fetchOpcode());
        }
        if (opcode == 0xED) {
            return executeEdPrefix(fetchOpcode());
        }
        if ((opcode & 0xC0) == 0x40) {
            return executeLoadRegisterGroup(opcode);
        }
        if ((opcode & 0xC0) == 0x80) {
            return executeAluRegisterGroup(opcode);
        }

        return switch (opcode & 0xFF) {
            case 0x00 -> 4;
            case 0x01 -> loadRegisterPairImmediate(0);
            case 0x02 -> loadIndirectFromAccumulator(registers.bc());
            case 0x03 -> incrementRegisterPair(0);
            case 0x04 -> increment8BitOperand(0);
            case 0x05 -> decrement8BitOperand(0);
            case 0x06 -> loadImmediateIntoOperand(0);
            case 0x07 -> rlca();
            case 0x08 -> exAfAfPrime();
            case 0x09 -> addHl(registers.bc());
            case 0x0A -> loadAccumulatorFromIndirect(registers.bc());
            case 0x0B -> decrementRegisterPair(0);
            case 0x0C -> increment8BitOperand(1);
            case 0x0D -> decrement8BitOperand(1);
            case 0x0E -> loadImmediateIntoOperand(1);
            case 0x0F -> rrca();

            case 0x10 -> djnz();
            case 0x11 -> loadRegisterPairImmediate(1);
            case 0x12 -> loadIndirectFromAccumulator(registers.de());
            case 0x13 -> incrementRegisterPair(1);
            case 0x14 -> increment8BitOperand(2);
            case 0x15 -> decrement8BitOperand(2);
            case 0x16 -> loadImmediateIntoOperand(2);
            case 0x17 -> rla();
            case 0x18 -> jr();
            case 0x19 -> addHl(registers.de());
            case 0x1A -> loadAccumulatorFromIndirect(registers.de());
            case 0x1B -> decrementRegisterPair(1);
            case 0x1C -> increment8BitOperand(3);
            case 0x1D -> decrement8BitOperand(3);
            case 0x1E -> loadImmediateIntoOperand(3);
            case 0x1F -> rra();

            case 0x20 -> jrConditional(!registers.flagSet(Z80Registers.FLAG_Z));
            case 0x21 -> loadRegisterPairImmediate(2);
            case 0x22 -> storeWordImmediateAddress(registers.hl());
            case 0x23 -> incrementRegisterPair(2);
            case 0x24 -> increment8BitOperand(4);
            case 0x25 -> decrement8BitOperand(4);
            case 0x26 -> loadImmediateIntoOperand(4);
            case 0x27 -> daa();
            case 0x28 -> jrConditional(registers.flagSet(Z80Registers.FLAG_Z));
            case 0x29 -> addHl(registers.hl());
            case 0x2A -> loadHlFromImmediateAddress();
            case 0x2B -> decrementRegisterPair(2);
            case 0x2C -> increment8BitOperand(5);
            case 0x2D -> decrement8BitOperand(5);
            case 0x2E -> loadImmediateIntoOperand(5);
            case 0x2F -> cpl();

            case 0x30 -> jrConditional(!registers.flagSet(Z80Registers.FLAG_C));
            case 0x31 -> loadRegisterPairImmediate(3);
            case 0x32 -> storeByteImmediateAddress(registers.a());
            case 0x33 -> incrementRegisterPair(3);
            case 0x34 -> increment8BitOperand(6);
            case 0x35 -> decrement8BitOperand(6);
            case 0x36 -> loadImmediateIntoOperand(6);
            case 0x37 -> scf();
            case 0x38 -> jrConditional(registers.flagSet(Z80Registers.FLAG_C));
            case 0x39 -> addHl(registers.sp());
            case 0x3A -> loadAccumulatorFromImmediateAddress();
            case 0x3B -> decrementRegisterPair(3);
            case 0x3C -> increment8BitOperand(7);
            case 0x3D -> decrement8BitOperand(7);
            case 0x3E -> loadImmediateIntoOperand(7);
            case 0x3F -> ccf();

            case 0x76 -> halt();
            case 0xC0, 0xC8, 0xD0, 0xD8, 0xE0, 0xE8, 0xF0, 0xF8 -> retConditional(conditionCode((opcode >>> 3) & 0x07));
            case 0xC1, 0xD1, 0xE1, 0xF1 -> popRegisterPair((opcode >>> 4) & 0x03);
            case 0xC2, 0xCA, 0xD2, 0xDA, 0xE2, 0xEA, 0xF2, 0xFA -> jpConditional(conditionCode((opcode >>> 3) & 0x07));
            case 0xC3 -> jpImmediate();
            case 0xC4, 0xCC, 0xD4, 0xDC, 0xE4, 0xEC, 0xF4, 0xFC -> callConditional(conditionCode((opcode >>> 3) & 0x07));
            case 0xC5, 0xD5, 0xE5, 0xF5 -> pushRegisterPair((opcode >>> 4) & 0x03);
            case 0xC6 -> executeAluImmediate(ALU_ADD);
            case 0xC7, 0xCF, 0xD7, 0xDF, 0xE7, 0xEF, 0xF7, 0xFF -> rst(opcode & 0x38);
            case 0xC9 -> ret();
            case 0xCD -> callImmediate();
            case 0xCE -> executeAluImmediate(ALU_ADC);
            case 0xD3 -> outImmediateAccumulator();
            case 0xD6 -> executeAluImmediate(ALU_SUB);
            case 0xD9 -> exx();
            case 0xDB -> inImmediateAccumulator();
            case 0xDE -> executeAluImmediate(ALU_SBC);
            case 0xE3 -> exSpHl();
            case 0xE6 -> executeAluImmediate(ALU_AND);
            case 0xE9 -> jpHl();
            case 0xEB -> exDeHl();
            case 0xEE -> executeAluImmediate(ALU_XOR);
            case 0xF3 -> di();
            case 0xF6 -> executeAluImmediate(ALU_OR);
            case 0xF9 -> ldSpHl();
            case 0xFB -> ei();
            case 0xFE -> executeAluImmediate(ALU_CP);
            default -> throw new UnsupportedOperationException(
                    "Opcode 0x%02X is not implemented yet".formatted(opcode & 0xFF)
            );
        };
    }

    private int executeIndexedPrefix(int indexMode) {
        int prefixCost = 4;
        int opcode = fetchOpcode();

        while (opcode == 0xDD || opcode == 0xFD) {
            indexMode = opcode == 0xDD ? INDEX_IX : INDEX_IY;
            prefixCost += 4;
            opcode = fetchOpcode();
        }

        if (opcode == 0xCB) {
            int displacement = relativeOffset(fetchImmediate8());
            int cbOpcode = fetchOpcode();
            return prefixCost + executeIndexedCbPrefix(indexMode, displacement, cbOpcode);
        }

        if (opcode == 0xED) {
            return prefixCost + executeEdPrefix(fetchOpcode());
        }

        if ((opcode & 0xC0) == 0x40) {
            return prefixCost + executeLoadRegisterGroupIndexed(opcode, indexMode);
        }

        if ((opcode & 0xC0) == 0x80) {
            return prefixCost + executeAluRegisterGroupIndexed(opcode, indexMode);
        }

        return prefixCost + executeIndexedOpcode(opcode, indexMode);
    }

    private int executeIndexedOpcode(int opcode, int indexMode) {
        return switch (opcode & 0xFF) {
            case 0x09 -> addIndex(indexMode, registers.bc());
            case 0x19 -> addIndex(indexMode, registers.de());
            case 0x21 -> loadIndexImmediate(indexMode);
            case 0x22 -> storeWordImmediateAddress(indexValue(indexMode));
            case 0x23 -> incrementIndex(indexMode);
            case 0x24 -> incrementIndexByte(indexMode, true);
            case 0x25 -> decrementIndexByte(indexMode, true);
            case 0x26 -> loadIndexByteImmediate(indexMode, true);
            case 0x29 -> addIndex(indexMode, indexValue(indexMode));
            case 0x2A -> loadIndexFromImmediateAddress(indexMode);
            case 0x2B -> decrementIndex(indexMode);
            case 0x2C -> incrementIndexByte(indexMode, false);
            case 0x2D -> decrementIndexByte(indexMode, false);
            case 0x2E -> loadIndexByteImmediate(indexMode, false);
            case 0x34 -> incrementIndexedMemory(indexMode);
            case 0x35 -> decrementIndexedMemory(indexMode);
            case 0x36 -> loadIndexedMemoryImmediate(indexMode);
            case 0x39 -> addIndex(indexMode, registers.sp());
            case 0xE1 -> popIndex(indexMode);
            case 0xE3 -> exSpIndex(indexMode);
            case 0xE5 -> pushIndex(indexMode);
            case 0xE9 -> jpIndex(indexMode);
            case 0xF9 -> ldSpIndex(indexMode);
            default -> executeOpcode(opcode);
        };
    }

    private int executeLoadRegisterGroup(int opcode) {
        if (opcode == 0x76) {
            return halt();
        }

        int destination = (opcode >>> 3) & 0x07;
        int source = opcode & 0x07;
        int value = read8BitOperand(source);
        write8BitOperand(destination, value);
        return (destination == 6 || source == 6) ? 7 : 4;
    }

    private int executeLoadRegisterGroupIndexed(int opcode, int indexMode) {
        if (opcode == 0x76) {
            return halt();
        }

        int destination = (opcode >>> 3) & 0x07;
        int source = opcode & 0x07;
        boolean indexedMemory = destination == 6 || source == 6;

        if (!indexedMemory && !usesIndexedSubstitution(destination) && !usesIndexedSubstitution(source)) {
            int value = read8BitOperand(source);
            write8BitOperand(destination, value);
            return (destination == 6 || source == 6) ? 7 : 4;
        }

        int displacement = indexedMemory ? relativeOffset(fetchImmediate8()) : 0;
        boolean substituteHighLow = !indexedMemory;
        int value = read8BitOperandIndexed(source, indexMode, displacement, substituteHighLow);
        write8BitOperandIndexed(destination, indexMode, displacement, value, substituteHighLow);
        return indexedMemory ? 15 : 4;
    }

    private int executeAluRegisterGroup(int opcode) {
        int operation = (opcode >>> 3) & 0x07;
        int source = opcode & 0x07;
        int value = read8BitOperand(source);
        return executeAluOperation(operation, value, source == 6 ? 7 : 4);
    }

    private int executeAluRegisterGroupIndexed(int opcode, int indexMode) {
        int operation = (opcode >>> 3) & 0x07;
        int source = opcode & 0x07;

        if (!usesIndexedSubstitution(source)) {
            return executeAluOperation(operation, read8BitOperand(source), source == 6 ? 7 : 4);
        }

        int displacement = source == 6 ? relativeOffset(fetchImmediate8()) : 0;
        int value = read8BitOperandIndexed(source, indexMode, displacement, source != 6);
        return executeAluOperation(operation, value, source == 6 ? 15 : 4);
    }

    private int executeCbPrefix(int opcode) {
        int group = (opcode >>> 6) & 0x03;
        int operation = (opcode >>> 3) & 0x07;
        int operandCode = opcode & 0x07;
        int value = read8BitOperand(operandCode);

        return switch (group) {
            case 0 -> {
                int result = rotateShiftCb(operation, value);
                write8BitOperand(operandCode, result);
                yield operandCode == 6 ? 15 : 8;
            }
            case 1 -> {
                bit(operation, value);
                yield operandCode == 6 ? 12 : 8;
            }
            case 2 -> {
                write8BitOperand(operandCode, value & ~(1 << operation));
                yield operandCode == 6 ? 15 : 8;
            }
            case 3 -> {
                write8BitOperand(operandCode, value | (1 << operation));
                yield operandCode == 6 ? 15 : 8;
            }
            default -> throw new IllegalStateException("Unexpected CB group " + group);
        };
    }

    private int executeIndexedCbPrefix(int indexMode, int displacement, int opcode) {
        int group = (opcode >>> 6) & 0x03;
        int operation = (opcode >>> 3) & 0x07;
        int destination = opcode & 0x07;
        int address = indexedAddress(indexMode, displacement);
        int value = readMemory8(address);

        return switch (group) {
            case 0 -> {
                int result = rotateShiftCb(operation, value);
                writeMemory8(address, result);
                if (destination != 6) {
                    writeRegisterOperand(destination, result);
                }
                yield 19;
            }
            case 1 -> {
                bit(operation, value);
                yield 16;
            }
            case 2 -> {
                int result = value & ~(1 << operation);
                writeMemory8(address, result);
                if (destination != 6) {
                    writeRegisterOperand(destination, result);
                }
                yield 19;
            }
            case 3 -> {
                int result = value | (1 << operation);
                writeMemory8(address, result);
                if (destination != 6) {
                    writeRegisterOperand(destination, result);
                }
                yield 19;
            }
            default -> throw new IllegalStateException("Unexpected indexed CB group " + group);
        };
    }

    private int executeEdPrefix(int opcode) {
        return switch (opcode & 0xFF) {
            case 0x40, 0x48, 0x50, 0x58, 0x60, 0x68, 0x78 -> inRegisterFromPortC((opcode >>> 3) & 0x07);
            case 0x41, 0x49, 0x51, 0x59, 0x61, 0x69, 0x79 -> outRegisterToPortC((opcode >>> 3) & 0x07);
            case 0x42, 0x52, 0x62, 0x72 -> sbcHl(getRegisterPair((opcode >>> 4) & 0x03));
            case 0x43, 0x53, 0x63, 0x73 -> storeRegisterPairImmediateAddress((opcode >>> 4) & 0x03);
            case 0x44, 0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C -> neg();
            case 0x45, 0x55, 0x5D, 0x65, 0x6D, 0x75, 0x7D, 0x4D -> retn();
            case 0x46, 0x4E, 0x66, 0x6E -> setInterruptMode(0);
            case 0x56, 0x76 -> setInterruptMode(1);
            case 0x5E, 0x7E -> setInterruptMode(2);
            case 0x47 -> ldIFromA();
            case 0x4F -> ldRFromA();
            case 0x4A, 0x5A, 0x6A, 0x7A -> adcHl(getRegisterPair((opcode >>> 4) & 0x03));
            case 0x4B, 0x5B, 0x6B, 0x7B -> loadRegisterPairFromImmediateAddress((opcode >>> 4) & 0x03);
            case 0x57 -> ldAFromI();
            case 0x5F -> ldAFromR();
            case 0x67 -> rrd();
            case 0x6F -> rld();
            case 0xA0 -> ldi();
            case 0xA1 -> cpi();
            case 0xA8 -> ldd();
            case 0xA9 -> cpd();
            case 0xB0 -> ldir();
            case 0xB1 -> cpir();
            case 0xB8 -> lddr();
            case 0xB9 -> cpdr();
            default -> throw new UnsupportedOperationException(
                    "ED opcode 0x%02X is not implemented yet".formatted(opcode & 0xFF)
            );
        };
    }

    private int loadRegisterPairImmediate(int pairCode) {
        int value = fetchImmediate16();
        setRegisterPair(pairCode, value);
        return 10;
    }

    private int loadIndexImmediate(int indexMode) {
        setIndexValue(indexMode, fetchImmediate16());
        return 10;
    }

    private int loadIndexFromImmediateAddress(int indexMode) {
        int address = fetchImmediate16();
        setIndexValue(indexMode, readMemory16(address));
        return 16;
    }

    private int incrementRegisterPair(int pairCode) {
        setRegisterPair(pairCode, getRegisterPair(pairCode) + 1);
        return 6;
    }

    private int decrementRegisterPair(int pairCode) {
        setRegisterPair(pairCode, getRegisterPair(pairCode) - 1);
        return 6;
    }

    private int incrementIndex(int indexMode) {
        setIndexValue(indexMode, indexValue(indexMode) + 1);
        return 6;
    }

    private int decrementIndex(int indexMode) {
        setIndexValue(indexMode, indexValue(indexMode) - 1);
        return 6;
    }

    private int addHl(int value) {
        registers.setHl(add16PreserveSzpv(registers.hl(), value));
        return 11;
    }

    private int addIndex(int indexMode, int value) {
        setIndexValue(indexMode, add16PreserveSzpv(indexValue(indexMode), value));
        return 11;
    }

    private int loadIndirectFromAccumulator(int address) {
        writeMemory8(address, registers.a());
        return 7;
    }

    private int loadAccumulatorFromIndirect(int address) {
        registers.setA(readMemory8(address));
        return 7;
    }

    private int loadImmediateIntoOperand(int operandCode) {
        int value = fetchImmediate8();
        write8BitOperand(operandCode, value);
        return operandCode == 6 ? 10 : 7;
    }

    private int loadIndexByteImmediate(int indexMode, boolean highByte) {
        int value = fetchImmediate8();
        if (highByte) {
            setIndexHigh(indexMode, value);
        } else {
            setIndexLow(indexMode, value);
        }
        return 7;
    }

    private int loadIndexedMemoryImmediate(int indexMode) {
        int displacement = relativeOffset(fetchImmediate8());
        int value = fetchImmediate8();
        writeMemory8(indexedAddress(indexMode, displacement), value);
        return 15;
    }

    private int increment8BitOperand(int operandCode) {
        int original = read8BitOperand(operandCode);
        int updated = (original + 1) & 0xFF;
        write8BitOperand(operandCode, updated);
        updateIncFlags(original, updated);
        return operandCode == 6 ? 11 : 4;
    }

    private int decrement8BitOperand(int operandCode) {
        int original = read8BitOperand(operandCode);
        int updated = (original - 1) & 0xFF;
        write8BitOperand(operandCode, updated);
        updateDecFlags(original, updated);
        return operandCode == 6 ? 11 : 4;
    }

    private int incrementIndexByte(int indexMode, boolean highByte) {
        int original = highByte ? indexHigh(indexMode) : indexLow(indexMode);
        int updated = (original + 1) & 0xFF;

        if (highByte) {
            setIndexHigh(indexMode, updated);
        } else {
            setIndexLow(indexMode, updated);
        }

        updateIncFlags(original, updated);
        return 4;
    }

    private int decrementIndexByte(int indexMode, boolean highByte) {
        int original = highByte ? indexHigh(indexMode) : indexLow(indexMode);
        int updated = (original - 1) & 0xFF;

        if (highByte) {
            setIndexHigh(indexMode, updated);
        } else {
            setIndexLow(indexMode, updated);
        }

        updateDecFlags(original, updated);
        return 4;
    }

    private int incrementIndexedMemory(int indexMode) {
        int displacement = relativeOffset(fetchImmediate8());
        int address = indexedAddress(indexMode, displacement);
        int original = readMemory8(address);
        int updated = (original + 1) & 0xFF;
        writeMemory8(address, updated);
        updateIncFlags(original, updated);
        return 19;
    }

    private int decrementIndexedMemory(int indexMode) {
        int displacement = relativeOffset(fetchImmediate8());
        int address = indexedAddress(indexMode, displacement);
        int original = readMemory8(address);
        int updated = (original - 1) & 0xFF;
        writeMemory8(address, updated);
        updateDecFlags(original, updated);
        return 19;
    }

    private int jr() {
        registers.incrementPc(relativeOffset(fetchImmediate8()));
        return 12;
    }

    private int jrConditional(boolean condition) {
        int offset = fetchImmediate8();
        if (condition) {
            registers.incrementPc(relativeOffset(offset));
            return 12;
        }
        return 7;
    }

    private int djnz() {
        int offset = fetchImmediate8();
        int result = (registers.b() - 1) & 0xFF;
        registers.setB(result);

        if (result != 0) {
            registers.incrementPc(relativeOffset(offset));
            return 13;
        }
        return 8;
    }

    private int storeWordImmediateAddress(int value) {
        int address = fetchImmediate16();
        writeMemory16(address, value);
        return 16;
    }

    private int loadHlFromImmediateAddress() {
        int address = fetchImmediate16();
        registers.setHl(readMemory16(address));
        return 16;
    }

    private int loadRegisterPairFromImmediateAddress(int pairCode) {
        int address = fetchImmediate16();
        setRegisterPair(pairCode, readMemory16(address));
        return 20;
    }

    private int storeByteImmediateAddress(int value) {
        int address = fetchImmediate16();
        writeMemory8(address, value);
        return 13;
    }

    private int storeRegisterPairImmediateAddress(int pairCode) {
        int address = fetchImmediate16();
        writeMemory16(address, getRegisterPair(pairCode));
        return 20;
    }

    private int loadAccumulatorFromImmediateAddress() {
        int address = fetchImmediate16();
        registers.setA(readMemory8(address));
        return 13;
    }

    private int halt() {
        halted = true;
        return 4;
    }

    private int jpConditional(boolean condition) {
        int target = fetchImmediate16();
        if (condition) {
            registers.setPc(target);
        }
        return 10;
    }

    private int jpImmediate() {
        registers.setPc(fetchImmediate16());
        return 10;
    }

    private int jpHl() {
        registers.setPc(registers.hl());
        return 4;
    }

    private int jpIndex(int indexMode) {
        registers.setPc(indexValue(indexMode));
        return 4;
    }

    private int callConditional(boolean condition) {
        int target = fetchImmediate16();
        if (condition) {
            pushWord(registers.pc());
            registers.setPc(target);
            return 17;
        }
        return 10;
    }

    private int callImmediate() {
        int target = fetchImmediate16();
        pushWord(registers.pc());
        registers.setPc(target);
        return 17;
    }

    private int retConditional(boolean condition) {
        if (condition) {
            registers.setPc(popWord());
            return 11;
        }
        return 5;
    }

    private int ret() {
        registers.setPc(popWord());
        return 10;
    }

    private int rst(int vector) {
        pushWord(registers.pc());
        registers.setPc(vector & 0x0038);
        return 11;
    }

    private int pushRegisterPair(int pairCode) {
        pushWord(getStackRegisterPair(pairCode));
        return 11;
    }

    private int pushIndex(int indexMode) {
        pushWord(indexValue(indexMode));
        return 11;
    }

    private int popRegisterPair(int pairCode) {
        setStackRegisterPair(pairCode, popWord());
        return 10;
    }

    private int popIndex(int indexMode) {
        setIndexValue(indexMode, popWord());
        return 10;
    }

    private int exAfAfPrime() {
        registers.swapAfWithAlternate();
        return 4;
    }

    private int exx() {
        registers.swapGeneralRegistersWithAlternate();
        return 4;
    }

    private int exDeHl() {
        int de = registers.de();
        registers.setDe(registers.hl());
        registers.setHl(de);
        return 4;
    }

    private int exSpHl() {
        int valueAtSp = readMemory16(registers.sp());
        writeMemory16(registers.sp(), registers.hl());
        registers.setHl(valueAtSp);
        return 19;
    }

    private int exSpIndex(int indexMode) {
        int valueAtSp = readMemory16(registers.sp());
        writeMemory16(registers.sp(), indexValue(indexMode));
        setIndexValue(indexMode, valueAtSp);
        return 19;
    }

    private int di() {
        registers.setIff1(false);
        registers.setIff2(false);
        interruptEnableDelay = 0;
        return 4;
    }

    private int ei() {
        registers.setIff1(true);
        registers.setIff2(true);
        interruptEnableDelay = 2;
        return 4;
    }

    private int ldSpHl() {
        registers.setSp(registers.hl());
        return 6;
    }

    private int ldSpIndex(int indexMode) {
        registers.setSp(indexValue(indexMode));
        return 6;
    }

    private int executeAluImmediate(int operation) {
        return executeAluOperation(operation, fetchImmediate8(), 7);
    }

    private int adcHl(int value) {
        int carryIn = registers.flagSet(Z80Registers.FLAG_C) ? 1 : 0;
        registers.setHl(add16WithCarry(registers.hl(), value, carryIn));
        return 15;
    }

    private int sbcHl(int value) {
        int carryIn = registers.flagSet(Z80Registers.FLAG_C) ? 1 : 0;
        registers.setHl(subtract16WithCarry(registers.hl(), value, carryIn));
        return 15;
    }

    private int rlca() {
        int original = registers.a();
        int carry = (original >>> 7) & 0x01;
        int result = ((original << 1) | carry) & 0xFF;
        registers.setA(result);
        setAccumulatorRotateFlags(result, carry != 0);
        return 4;
    }

    private int rrca() {
        int original = registers.a();
        int carry = original & 0x01;
        int result = ((original >>> 1) | (carry << 7)) & 0xFF;
        registers.setA(result);
        setAccumulatorRotateFlags(result, carry != 0);
        return 4;
    }

    private int rla() {
        int original = registers.a();
        int carryIn = registers.flagSet(Z80Registers.FLAG_C) ? 1 : 0;
        int carryOut = (original >>> 7) & 0x01;
        int result = ((original << 1) | carryIn) & 0xFF;
        registers.setA(result);
        setAccumulatorRotateFlags(result, carryOut != 0);
        return 4;
    }

    private int rra() {
        int original = registers.a();
        int carryIn = registers.flagSet(Z80Registers.FLAG_C) ? 0x80 : 0;
        int carryOut = original & 0x01;
        int result = ((original >>> 1) | carryIn) & 0xFF;
        registers.setA(result);
        setAccumulatorRotateFlags(result, carryOut != 0);
        return 4;
    }

    private int daa() {
        int original = registers.a();
        int flagsBefore = registers.f();
        boolean subtraction = (flagsBefore & Z80Registers.FLAG_N) != 0;
        boolean previousCarry = (flagsBefore & Z80Registers.FLAG_C) != 0;
        boolean previousHalfCarry = (flagsBefore & Z80Registers.FLAG_H) != 0;

        int correction = 0;
        boolean carryOut = previousCarry;

        if (!subtraction) {
            if (previousHalfCarry || (original & 0x0F) > 0x09) {
                correction |= 0x06;
            }
            if (previousCarry || original > 0x99) {
                correction |= 0x60;
                carryOut = true;
            }
            original = (original + correction) & 0xFF;
        } else {
            if (previousHalfCarry) {
                correction |= 0x06;
            }
            if (previousCarry) {
                correction |= 0x60;
            }
            original = (original - correction) & 0xFF;
        }

        int flags = original & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        if (original == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (parityEven(original)) {
            flags |= Z80Registers.FLAG_PV;
        }
        if (((registers.a() ^ original) & 0x10) != 0) {
            flags |= Z80Registers.FLAG_H;
        }
        if (subtraction) {
            flags |= Z80Registers.FLAG_N;
        }
        if (carryOut) {
            flags |= Z80Registers.FLAG_C;
        }

        registers.setA(original);
        registers.setF(flags);
        return 4;
    }

    private int cpl() {
        int result = registers.a() ^ 0xFF;
        registers.setA(result);
        int flags = registers.f() & (Z80Registers.FLAG_S | Z80Registers.FLAG_Z | Z80Registers.FLAG_PV | Z80Registers.FLAG_C);
        flags |= Z80Registers.FLAG_H | Z80Registers.FLAG_N;
        flags |= result & (Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        registers.setF(flags);
        return 4;
    }

    private int scf() {
        int flags = registers.f() & (Z80Registers.FLAG_S | Z80Registers.FLAG_Z | Z80Registers.FLAG_PV);
        flags |= registers.a() & (Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        flags |= Z80Registers.FLAG_C;
        registers.setF(flags);
        return 4;
    }

    private int ccf() {
        boolean previousCarry = registers.flagSet(Z80Registers.FLAG_C);
        int flags = registers.f() & (Z80Registers.FLAG_S | Z80Registers.FLAG_Z | Z80Registers.FLAG_PV);
        flags |= registers.a() & (Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        if (previousCarry) {
            flags |= Z80Registers.FLAG_H;
        } else {
            flags |= Z80Registers.FLAG_C;
        }
        registers.setF(flags);
        return 4;
    }

    private int inImmediateAccumulator() {
        int portLow = fetchImmediate8();
        int port = ((registers.a() & 0xFF) << 8) | portLow;
        registers.setA(bus.readPort(port, IN_IMMEDIATE_PHASE_TSTATES) & 0xFF);
        return 11;
    }

    private int outImmediateAccumulator() {
        int portLow = fetchImmediate8();
        int port = ((registers.a() & 0xFF) << 8) | portLow;
        bus.writePort(port, registers.a(), 7);
        return 11;
    }

    private int inRegisterFromPortC(int registerCode) {
        int value = bus.readPort(registers.bc(), IN_C_PHASE_TSTATES) & 0xFF;
        writeRegisterOperand(registerCode, value);
        setInFlags(value);
        return 12;
    }

    private int outRegisterToPortC(int registerCode) {
        int value = registerCode == 6 ? 0 : readRegisterOperand(registerCode);
        bus.writePort(registers.bc(), value, 8);
        return 12;
    }

    private int ldi() {
        blockTransfer(true);
        return 16;
    }

    private int ldir() {
        int iterations = 0;
        do {
            blockTransfer(true);
            iterations++;
        } while (registers.bc() != 0);

        return blockRepeatTiming(iterations);
    }

    private int ldd() {
        blockTransfer(false);
        return 16;
    }

    private int lddr() {
        int iterations = 0;
        do {
            blockTransfer(false);
            iterations++;
        } while (registers.bc() != 0);

        return blockRepeatTiming(iterations);
    }

    private int cpi() {
        boolean matched = blockCompare(true);
        return matched ? 16 : 16;
    }

    private int cpir() {
        int iterations = 0;
        boolean matched;
        do {
            matched = blockCompare(true);
            iterations++;
        } while (!matched && registers.bc() != 0);

        return blockRepeatTiming(iterations);
    }

    private int cpd() {
        blockCompare(false);
        return 16;
    }

    private int cpdr() {
        int iterations = 0;
        boolean matched;
        do {
            matched = blockCompare(false);
            iterations++;
        } while (!matched && registers.bc() != 0);

        return blockRepeatTiming(iterations);
    }

    private int executeAluOperation(int operation, int value, int tStates) {
        int accumulator = registers.a();
        int carryIn = registers.flagSet(Z80Registers.FLAG_C) ? 1 : 0;

        switch (operation) {
            case ALU_ADD -> registers.setA(add8(accumulator, value, 0));
            case ALU_ADC -> registers.setA(add8(accumulator, value, carryIn));
            case ALU_SUB -> registers.setA(subtract8(accumulator, value, 0));
            case ALU_SBC -> registers.setA(subtract8(accumulator, value, carryIn));
            case ALU_AND -> {
                int result = accumulator & value;
                registers.setA(result);
                setLogicFlags(result, true);
            }
            case ALU_XOR -> {
                int result = accumulator ^ value;
                registers.setA(result);
                setLogicFlags(result, false);
            }
            case ALU_OR -> {
                int result = accumulator | value;
                registers.setA(result);
                setLogicFlags(result, false);
            }
            case ALU_CP -> compare8(accumulator, value);
            default -> throw new IllegalStateException("Unexpected ALU operation " + operation);
        }

        return tStates;
    }

    private int read8BitOperand(int operandCode) {
        return switch (operandCode & 0x07) {
            case 0 -> registers.b();
            case 1 -> registers.c();
            case 2 -> registers.d();
            case 3 -> registers.e();
            case 4 -> registers.h();
            case 5 -> registers.l();
            case 6 -> readMemory8(registers.hl());
            case 7 -> registers.a();
            default -> throw new IllegalStateException("Unexpected operand code");
        };
    }

    private int read8BitOperandIndexed(int operandCode, int indexMode, int displacement, boolean substituteHighLow) {
        return switch (operandCode & 0x07) {
            case 4 -> substituteHighLow ? indexHigh(indexMode) : registers.h();
            case 5 -> substituteHighLow ? indexLow(indexMode) : registers.l();
            case 6 -> readMemory8(indexedAddress(indexMode, displacement));
            default -> read8BitOperand(operandCode);
        };
    }

    private void write8BitOperand(int operandCode, int value) {
        switch (operandCode & 0x07) {
            case 0 -> registers.setB(value);
            case 1 -> registers.setC(value);
            case 2 -> registers.setD(value);
            case 3 -> registers.setE(value);
            case 4 -> registers.setH(value);
            case 5 -> registers.setL(value);
            case 6 -> writeMemory8(registers.hl(), value);
            case 7 -> registers.setA(value);
            default -> throw new IllegalStateException("Unexpected operand code");
        }
    }

    private void write8BitOperandIndexed(int operandCode, int indexMode, int displacement, int value, boolean substituteHighLow) {
        switch (operandCode & 0x07) {
            case 4 -> {
                if (substituteHighLow) {
                    setIndexHigh(indexMode, value);
                } else {
                    registers.setH(value);
                }
            }
            case 5 -> {
                if (substituteHighLow) {
                    setIndexLow(indexMode, value);
                } else {
                    registers.setL(value);
                }
            }
            case 6 -> writeMemory8(indexedAddress(indexMode, displacement), value);
            default -> write8BitOperand(operandCode, value);
        }
    }

    private void writeRegisterOperand(int operandCode, int value) {
        switch (operandCode & 0x07) {
            case 0 -> registers.setB(value);
            case 1 -> registers.setC(value);
            case 2 -> registers.setD(value);
            case 3 -> registers.setE(value);
            case 4 -> registers.setH(value);
            case 5 -> registers.setL(value);
            case 7 -> registers.setA(value);
            default -> {
            }
        }
    }

    private int readRegisterOperand(int operandCode) {
        return switch (operandCode & 0x07) {
            case 0 -> registers.b();
            case 1 -> registers.c();
            case 2 -> registers.d();
            case 3 -> registers.e();
            case 4 -> registers.h();
            case 5 -> registers.l();
            case 7 -> registers.a();
            default -> 0;
        };
    }

    private int getRegisterPair(int pairCode) {
        return switch (pairCode & 0x03) {
            case 0 -> registers.bc();
            case 1 -> registers.de();
            case 2 -> registers.hl();
            case 3 -> registers.sp();
            default -> throw new IllegalStateException("Unexpected pair code");
        };
    }

    private void setRegisterPair(int pairCode, int value) {
        switch (pairCode & 0x03) {
            case 0 -> registers.setBc(value);
            case 1 -> registers.setDe(value);
            case 2 -> registers.setHl(value);
            case 3 -> registers.setSp(value);
            default -> throw new IllegalStateException("Unexpected pair code");
        }
    }

    private int getStackRegisterPair(int pairCode) {
        return switch (pairCode & 0x03) {
            case 0 -> registers.bc();
            case 1 -> registers.de();
            case 2 -> registers.hl();
            case 3 -> registers.af();
            default -> throw new IllegalStateException("Unexpected stack pair code");
        };
    }

    private void setStackRegisterPair(int pairCode, int value) {
        switch (pairCode & 0x03) {
            case 0 -> registers.setBc(value);
            case 1 -> registers.setDe(value);
            case 2 -> registers.setHl(value);
            case 3 -> registers.setAf(value);
            default -> throw new IllegalStateException("Unexpected stack pair code");
        }
    }

    private int fetchImmediate8() {
        int value = readMemory8(registers.pc());
        registers.incrementPc(1);
        return value;
    }

    private int fetchImmediate16() {
        int low = fetchImmediate8();
        int high = fetchImmediate8();
        return low | (high << 8);
    }

    private int readMemory8(int address) {
        return bus.readMemory(address) & 0xFF;
    }

    private int readMemory16(int address) {
        int low = readMemory8(address);
        int high = readMemory8(address + 1);
        return low | (high << 8);
    }

    private void writeMemory8(int address, int value) {
        bus.writeMemory(address, value & 0xFF);
    }

    private void writeMemory16(int address, int value) {
        writeMemory8(address, value);
        writeMemory8(address + 1, value >>> 8);
    }

    private void pushWord(int value) {
        registers.setSp(registers.sp() - 1);
        writeMemory8(registers.sp(), value >>> 8);
        registers.setSp(registers.sp() - 1);
        writeMemory8(registers.sp(), value);
    }

    private int popWord() {
        int low = readMemory8(registers.sp());
        registers.setSp(registers.sp() + 1);
        int high = readMemory8(registers.sp());
        registers.setSp(registers.sp() + 1);
        return low | (high << 8);
    }

    private void updateIncFlags(int original, int updated) {
        int flags = registers.f() & Z80Registers.FLAG_C;
        flags |= updated & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);

        if (updated == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (((original & 0x0F) + 1) > 0x0F) {
            flags |= Z80Registers.FLAG_H;
        }
        if (original == 0x7F) {
            flags |= Z80Registers.FLAG_PV;
        }

        registers.setF(flags);
    }

    private void updateDecFlags(int original, int updated) {
        int flags = registers.f() & Z80Registers.FLAG_C;
        flags |= updated & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        flags |= Z80Registers.FLAG_N;

        if (updated == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if ((original & 0x0F) == 0) {
            flags |= Z80Registers.FLAG_H;
        }
        if (original == 0x80) {
            flags |= Z80Registers.FLAG_PV;
        }

        registers.setF(flags);
    }

    private int add8(int left, int right, int carryIn) {
        int raw = left + right + carryIn;
        int result = raw & 0xFF;
        int flags = result & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);

        if (result == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (((left & 0x0F) + (right & 0x0F) + carryIn) > 0x0F) {
            flags |= Z80Registers.FLAG_H;
        }
        if (((~(left ^ right)) & (left ^ result) & 0x80) != 0) {
            flags |= Z80Registers.FLAG_PV;
        }
        if (raw > 0xFF) {
            flags |= Z80Registers.FLAG_C;
        }

        registers.setF(flags);
        return result;
    }

    private int subtract8(int left, int right, int carryIn) {
        int raw = left - right - carryIn;
        int result = raw & 0xFF;
        int flags = result & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        flags |= Z80Registers.FLAG_N;

        if (result == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (((left ^ right ^ result) & 0x10) != 0) {
            flags |= Z80Registers.FLAG_H;
        }
        if (((left ^ right) & (left ^ result) & 0x80) != 0) {
            flags |= Z80Registers.FLAG_PV;
        }
        if (raw < 0) {
            flags |= Z80Registers.FLAG_C;
        }

        registers.setF(flags);
        return result;
    }

    private void compare8(int left, int right) {
        int raw = left - right;
        int result = raw & 0xFF;
        int flags = result & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        flags |= Z80Registers.FLAG_N;

        if (result == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (((left ^ right ^ result) & 0x10) != 0) {
            flags |= Z80Registers.FLAG_H;
        }
        if (((left ^ right) & (left ^ result) & 0x80) != 0) {
            flags |= Z80Registers.FLAG_PV;
        }
        if (raw < 0) {
            flags |= Z80Registers.FLAG_C;
        }

        registers.setF(flags);
    }

    private void setLogicFlags(int result, boolean halfCarry) {
        int flags = result & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);

        if (result == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (parityEven(result)) {
            flags |= Z80Registers.FLAG_PV;
        }
        if (halfCarry) {
            flags |= Z80Registers.FLAG_H;
        }

        registers.setF(flags);
    }

    private int add16PreserveSzpv(int left, int right) {
        int raw = left + right;
        int result = raw & 0xFFFF;
        int flags = registers.f() & (Z80Registers.FLAG_S | Z80Registers.FLAG_Z | Z80Registers.FLAG_PV);
        flags |= (result >>> 8) & (Z80Registers.FLAG_5 | Z80Registers.FLAG_3);

        if (((left & 0x0FFF) + (right & 0x0FFF)) > 0x0FFF) {
            flags |= Z80Registers.FLAG_H;
        }
        if (raw > 0xFFFF) {
            flags |= Z80Registers.FLAG_C;
        }

        registers.setF(flags);
        return result;
    }

    private int add16WithCarry(int left, int right, int carryIn) {
        int raw = left + right + carryIn;
        int result = raw & 0xFFFF;
        int flags = ((result >>> 8) & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3))
                | ((result & 0xFFFF) == 0 ? Z80Registers.FLAG_Z : 0);

        if (((left & 0x0FFF) + (right & 0x0FFF) + carryIn) > 0x0FFF) {
            flags |= Z80Registers.FLAG_H;
        }
        if (((~(left ^ right)) & (left ^ result) & 0x8000) != 0) {
            flags |= Z80Registers.FLAG_PV;
        }
        if ((raw & 0x1_0000) != 0) {
            flags |= Z80Registers.FLAG_C;
        }

        registers.setF(flags);
        return result;
    }

    private int subtract16WithCarry(int left, int right, int carryIn) {
        int raw = left - right - carryIn;
        int result = raw & 0xFFFF;
        int flags = ((result >>> 8) & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3))
                | Z80Registers.FLAG_N
                | ((result & 0xFFFF) == 0 ? Z80Registers.FLAG_Z : 0);

        if (((left ^ right ^ result) & 0x1000) != 0) {
            flags |= Z80Registers.FLAG_H;
        }
        if (((left ^ right) & (left ^ result) & 0x8000) != 0) {
            flags |= Z80Registers.FLAG_PV;
        }
        if (raw < 0) {
            flags |= Z80Registers.FLAG_C;
        }

        registers.setF(flags);
        return result;
    }

    private int rotateShiftCb(int operation, int value) {
        int carryIn = registers.flagSet(Z80Registers.FLAG_C) ? 1 : 0;
        int result;
        int carryOut;

        switch (operation & 0x07) {
            case 0 -> {
                carryOut = (value >>> 7) & 0x01;
                result = ((value << 1) | carryOut) & 0xFF;
            }
            case 1 -> {
                carryOut = value & 0x01;
                result = ((value >>> 1) | (carryOut << 7)) & 0xFF;
            }
            case 2 -> {
                carryOut = (value >>> 7) & 0x01;
                result = ((value << 1) | carryIn) & 0xFF;
            }
            case 3 -> {
                carryOut = value & 0x01;
                result = ((value >>> 1) | (carryIn << 7)) & 0xFF;
            }
            case 4 -> {
                carryOut = (value >>> 7) & 0x01;
                result = (value << 1) & 0xFF;
            }
            case 5 -> {
                carryOut = value & 0x01;
                result = ((value >>> 1) | (value & 0x80)) & 0xFF;
            }
            case 6 -> {
                carryOut = (value >>> 7) & 0x01;
                result = ((value << 1) | 0x01) & 0xFF;
            }
            case 7 -> {
                carryOut = value & 0x01;
                result = (value >>> 1) & 0xFF;
            }
            default -> throw new IllegalStateException("Unexpected CB operation");
        }

        setRotateShiftFlags(result, carryOut != 0);
        return result;
    }

    private void bit(int bitIndex, int value) {
        int flags = registers.f() & Z80Registers.FLAG_C;
        int mask = 1 << (bitIndex & 0x07);
        boolean bitSet = (value & mask) != 0;

        if ((bitIndex & 0x07) == 7 && bitSet) {
            flags |= Z80Registers.FLAG_S;
        }
        if (!bitSet) {
            flags |= Z80Registers.FLAG_Z | Z80Registers.FLAG_PV;
        }
        flags |= Z80Registers.FLAG_H;
        flags |= value & (Z80Registers.FLAG_5 | Z80Registers.FLAG_3);

        registers.setF(flags);
    }

    private void setRotateShiftFlags(int result, boolean carry) {
        int flags = result & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);

        if (result == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (parityEven(result)) {
            flags |= Z80Registers.FLAG_PV;
        }
        if (carry) {
            flags |= Z80Registers.FLAG_C;
        }

        registers.setF(flags);
    }

    private void setAccumulatorRotateFlags(int result, boolean carry) {
        int flags = registers.f() & (Z80Registers.FLAG_S | Z80Registers.FLAG_Z | Z80Registers.FLAG_PV);
        flags |= result & (Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        if (carry) {
            flags |= Z80Registers.FLAG_C;
        }
        registers.setF(flags);
    }

    private int neg() {
        registers.setA(subtract8(0, registers.a(), 0));
        return 8;
    }

    private int retn() {
        registers.setPc(popWord());
        registers.setIff1(registers.iff2());
        return 14;
    }

    private int setInterruptMode(int interruptMode) {
        registers.setInterruptMode(interruptMode);
        return 8;
    }

    private int ldIFromA() {
        registers.setI(registers.a());
        return 9;
    }

    private int ldRFromA() {
        int preservedTopBit = registers.r() & 0x80;
        registers.setR((registers.a() & 0x7F) | preservedTopBit);
        return 9;
    }

    private int ldAFromI() {
        int value = registers.i();
        registers.setA(value);

        int flags = registers.f() & Z80Registers.FLAG_C;
        flags |= value & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        if (value == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (registers.iff2()) {
            flags |= Z80Registers.FLAG_PV;
        }

        registers.setF(flags);
        return 9;
    }

    private int ldAFromR() {
        int value = registers.r();
        registers.setA(value);

        int flags = registers.f() & Z80Registers.FLAG_C;
        flags |= value & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        if (value == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (registers.iff2()) {
            flags |= Z80Registers.FLAG_PV;
        }

        registers.setF(flags);
        return 9;
    }

    private int rrd() {
        int value = readMemory8(registers.hl());
        int accumulator = registers.a();
        int updatedMemory = ((accumulator & 0x0F) << 4) | ((value >>> 4) & 0x0F);
        int updatedAccumulator = (accumulator & 0xF0) | (value & 0x0F);

        writeMemory8(registers.hl(), updatedMemory);
        registers.setA(updatedAccumulator);
        setRrdRldFlags(updatedAccumulator);
        return 18;
    }

    private int rld() {
        int value = readMemory8(registers.hl());
        int accumulator = registers.a();
        int updatedMemory = ((value << 4) | (accumulator & 0x0F)) & 0xFF;
        int updatedAccumulator = (accumulator & 0xF0) | ((value >>> 4) & 0x0F);

        writeMemory8(registers.hl(), updatedMemory);
        registers.setA(updatedAccumulator);
        setRrdRldFlags(updatedAccumulator);
        return 18;
    }

    private void setRrdRldFlags(int accumulator) {
        int flags = accumulator & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        if (accumulator == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (parityEven(accumulator)) {
            flags |= Z80Registers.FLAG_PV;
        }
        flags |= registers.f() & Z80Registers.FLAG_C;
        registers.setF(flags);
    }

    private void setInFlags(int value) {
        int flags = value & (Z80Registers.FLAG_S | Z80Registers.FLAG_5 | Z80Registers.FLAG_3);
        if (value == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (parityEven(value)) {
            flags |= Z80Registers.FLAG_PV;
        }
        flags |= registers.f() & Z80Registers.FLAG_C;
        registers.setF(flags);
    }

    private void blockTransfer(boolean increment) {
        int value = readMemory8(registers.hl());
        writeMemory8(registers.de(), value);
        registers.setHl(registers.hl() + (increment ? 1 : -1));
        registers.setDe(registers.de() + (increment ? 1 : -1));
        registers.setBc(registers.bc() - 1);

        int flags = registers.f() & (Z80Registers.FLAG_S | Z80Registers.FLAG_Z | Z80Registers.FLAG_C);
        if (registers.bc() != 0) {
            flags |= Z80Registers.FLAG_PV;
        }
        registers.setF(flags);
    }

    private boolean blockCompare(boolean increment) {
        int memoryValue = readMemory8(registers.hl());
        int raw = registers.a() - memoryValue;
        int result = raw & 0xFF;

        registers.setHl(registers.hl() + (increment ? 1 : -1));
        registers.setBc(registers.bc() - 1);

        int flags = result & Z80Registers.FLAG_S;
        if (result == 0) {
            flags |= Z80Registers.FLAG_Z;
        }
        if (((registers.a() ^ memoryValue ^ result) & 0x10) != 0) {
            flags |= Z80Registers.FLAG_H;
        }
        if (registers.bc() != 0) {
            flags |= Z80Registers.FLAG_PV;
        }
        flags |= Z80Registers.FLAG_N;
        flags |= registers.f() & Z80Registers.FLAG_C;
        registers.setF(flags);
        return result == 0;
    }

    private int blockRepeatTiming(int iterations) {
        if (iterations <= 0) {
            return 16;
        }
        return 16 + ((iterations - 1) * 21);
    }

    private boolean usesIndexedSubstitution(int operandCode) {
        int normalized = operandCode & 0x07;
        return normalized == 4 || normalized == 5 || normalized == 6;
    }

    private int indexValue(int indexMode) {
        return switch (indexMode) {
            case INDEX_IX -> registers.ix();
            case INDEX_IY -> registers.iy();
            default -> throw new IllegalStateException("Unexpected index mode " + indexMode);
        };
    }

    private void setIndexValue(int indexMode, int value) {
        switch (indexMode) {
            case INDEX_IX -> registers.setIx(value);
            case INDEX_IY -> registers.setIy(value);
            default -> throw new IllegalStateException("Unexpected index mode " + indexMode);
        }
    }

    private int indexHigh(int indexMode) {
        return (indexValue(indexMode) >>> 8) & 0xFF;
    }

    private void setIndexHigh(int indexMode, int value) {
        int index = indexValue(indexMode);
        setIndexValue(indexMode, ((value & 0xFF) << 8) | (index & 0x00FF));
    }

    private int indexLow(int indexMode) {
        return indexValue(indexMode) & 0xFF;
    }

    private void setIndexLow(int indexMode, int value) {
        int index = indexValue(indexMode);
        setIndexValue(indexMode, (index & 0xFF00) | (value & 0xFF));
    }

    private int indexedAddress(int indexMode, int displacement) {
        return (indexValue(indexMode) + displacement) & 0xFFFF;
    }

    private boolean conditionCode(int code) {
        return switch (code & 0x07) {
            case 0 -> !registers.flagSet(Z80Registers.FLAG_Z);
            case 1 -> registers.flagSet(Z80Registers.FLAG_Z);
            case 2 -> !registers.flagSet(Z80Registers.FLAG_C);
            case 3 -> registers.flagSet(Z80Registers.FLAG_C);
            case 4 -> !registers.flagSet(Z80Registers.FLAG_PV);
            case 5 -> registers.flagSet(Z80Registers.FLAG_PV);
            case 6 -> !registers.flagSet(Z80Registers.FLAG_S);
            case 7 -> registers.flagSet(Z80Registers.FLAG_S);
            default -> throw new IllegalStateException("Unexpected condition code");
        };
    }

    private boolean parityEven(int value) {
        return (Integer.bitCount(value & 0xFF) & 0x01) == 0;
    }

    private int relativeOffset(int value) {
        return (byte) (value & 0xFF);
    }
}
