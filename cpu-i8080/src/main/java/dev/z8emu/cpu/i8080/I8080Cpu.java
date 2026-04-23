package dev.z8emu.cpu.i8080;

import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.cpu.Cpu;
import java.util.Objects;

public final class I8080Cpu implements Cpu {
    private static final int ALU_ADD = 0;
    private static final int ALU_ADC = 1;
    private static final int ALU_SUB = 2;
    private static final int ALU_SBB = 3;
    private static final int ALU_ANA = 4;
    private static final int ALU_XRA = 5;
    private static final int ALU_ORA = 6;
    private static final int ALU_CMP = 7;

    private final CpuBus bus;
    private final I8080Registers registers = new I8080Registers();

    private boolean halted;
    private boolean pendingInterrupt;
    private boolean interruptsEnabled;
    private boolean pendingEnableInterrupts;
    private int interruptEnableDelay;
    private InteOutputListener inteOutputListener;

    public I8080Cpu(CpuBus bus) {
        this.bus = Objects.requireNonNull(bus, "bus");
        reset();
    }

    public I8080Registers registers() {
        return registers;
    }

    public boolean interruptsEnabled() {
        return interruptsEnabled;
    }

    public boolean inteOutputHigh() {
        return interruptsEnabled;
    }

    public void setInteOutputListener(InteOutputListener inteOutputListener) {
        this.inteOutputListener = inteOutputListener;
        notifyInteOutputChanged();
    }

    public boolean isHalted() {
        return halted;
    }

    @Override
    public void reset() {
        registers.reset();
        halted = false;
        pendingInterrupt = false;
        interruptsEnabled = false;
        pendingEnableInterrupts = false;
        interruptEnableDelay = 0;
        notifyInteOutputChanged();
    }

    @Override
    public void requestMaskableInterrupt() {
        pendingInterrupt = true;
    }

    @Override
    public void clearMaskableInterrupt() {
        pendingInterrupt = false;
    }

    @Override
    public void requestNonMaskableInterrupt() {
        throw new UnsupportedOperationException("Intel 8080 has no non-maskable interrupt input");
    }

    @Override
    public int runInstruction() {
        int tStates;

        if (pendingInterrupt && interruptsEnabled && interruptEnableDelay == 0) {
            tStates = serviceMaskableInterrupt();
        } else if (halted) {
            tStates = 7;
        } else {
            int opcodeAddress = registers.pc();
            int opcode = fetchOpcode();
            tStates = executeOpcode(opcode, opcodeAddress);
        }

        applyDeferredInterruptEnable();
        return tStates;
    }

    private int serviceMaskableInterrupt() {
        pendingInterrupt = false;
        halted = false;
        setInterruptsEnabled(false);
        pendingEnableInterrupts = false;
        interruptEnableDelay = 0;

        int interruptOpcode = bus.acknowledgeInterrupt() & 0xFF;
        if ((interruptOpcode & 0xC7) != 0xC7) {
            throw new IllegalStateException(
                    "Intel 8080 interrupt supplied unsupported opcode 0x%02X".formatted(interruptOpcode)
            );
        }

        pushWord(registers.pc());
        registers.setPc(interruptOpcode & 0x38);
        return 11;
    }

    private void applyDeferredInterruptEnable() {
        if (!pendingEnableInterrupts) {
            return;
        }

        if (interruptEnableDelay == 0) {
            setInterruptsEnabled(true);
            pendingEnableInterrupts = false;
        } else {
            interruptEnableDelay--;
        }
    }

    private int executeOpcode(int opcode, int opcodeAddress) {
        if ((opcode & 0xC0) == 0x40) {
            return executeLoadRegisterGroup(opcode);
        }
        if ((opcode & 0xC0) == 0x80) {
            return executeAluRegisterGroup(opcode);
        }

        return switch (opcode & 0xFF) {
            case 0x00 -> 4;
            case 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38 -> 4;
            case 0x01 -> loadRegisterPairImmediate(0);
            case 0x02 -> storeAccumulatorIndirect(registers.bc());
            case 0x03 -> incrementRegisterPair(0);
            case 0x04 -> increment8BitOperand(0);
            case 0x05 -> decrement8BitOperand(0);
            case 0x06 -> loadImmediateIntoOperand(0);
            case 0x07 -> rotateLeft();
            case 0x09 -> addHl(registers.bc());
            case 0x0A -> loadAccumulatorIndirect(registers.bc());
            case 0x0B -> decrementRegisterPair(0);
            case 0x0C -> increment8BitOperand(1);
            case 0x0D -> decrement8BitOperand(1);
            case 0x0E -> loadImmediateIntoOperand(1);
            case 0x0F -> rotateRight();

            case 0x11 -> loadRegisterPairImmediate(1);
            case 0x12 -> storeAccumulatorIndirect(registers.de());
            case 0x13 -> incrementRegisterPair(1);
            case 0x14 -> increment8BitOperand(2);
            case 0x15 -> decrement8BitOperand(2);
            case 0x16 -> loadImmediateIntoOperand(2);
            case 0x17 -> rotateLeftThroughCarry();
            case 0x19 -> addHl(registers.de());
            case 0x1A -> loadAccumulatorIndirect(registers.de());
            case 0x1B -> decrementRegisterPair(1);
            case 0x1C -> increment8BitOperand(3);
            case 0x1D -> decrement8BitOperand(3);
            case 0x1E -> loadImmediateIntoOperand(3);
            case 0x1F -> rotateRightThroughCarry();

            case 0x21 -> loadRegisterPairImmediate(2);
            case 0x22 -> storeHlImmediateAddress();
            case 0x23 -> incrementRegisterPair(2);
            case 0x24 -> increment8BitOperand(4);
            case 0x25 -> decrement8BitOperand(4);
            case 0x26 -> loadImmediateIntoOperand(4);
            case 0x27 -> decimalAdjustAccumulator();
            case 0x29 -> addHl(registers.hl());
            case 0x2A -> loadHlImmediateAddress();
            case 0x2B -> decrementRegisterPair(2);
            case 0x2C -> increment8BitOperand(5);
            case 0x2D -> decrement8BitOperand(5);
            case 0x2E -> loadImmediateIntoOperand(5);
            case 0x2F -> complementAccumulator();

            case 0x31 -> loadRegisterPairImmediate(3);
            case 0x32 -> storeAccumulatorImmediateAddress();
            case 0x33 -> incrementRegisterPair(3);
            case 0x34 -> increment8BitOperand(6);
            case 0x35 -> decrement8BitOperand(6);
            case 0x36 -> loadImmediateIntoOperand(6);
            case 0x37 -> setCarry();
            case 0x39 -> addHl(registers.sp());
            case 0x3A -> loadAccumulatorImmediateAddress();
            case 0x3B -> decrementRegisterPair(3);
            case 0x3C -> increment8BitOperand(7);
            case 0x3D -> decrement8BitOperand(7);
            case 0x3E -> loadImmediateIntoOperand(7);
            case 0x3F -> complementCarry();

            case 0x76 -> halt();

            case 0xC0, 0xC8, 0xD0, 0xD8, 0xE0, 0xE8, 0xF0, 0xF8 -> retConditional(conditionCode((opcode >>> 3) & 0x07));
            case 0xC1, 0xD1, 0xE1, 0xF1 -> popRegisterPair((opcode >>> 4) & 0x03);
            case 0xC2, 0xCA, 0xD2, 0xDA, 0xE2, 0xEA, 0xF2, 0xFA -> jumpConditional(conditionCode((opcode >>> 3) & 0x07));
            case 0xC3 -> jumpImmediate();
            case 0xC4, 0xCC, 0xD4, 0xDC, 0xE4, 0xEC, 0xF4, 0xFC -> callConditional(conditionCode((opcode >>> 3) & 0x07));
            case 0xC5, 0xD5, 0xE5, 0xF5 -> pushRegisterPair((opcode >>> 4) & 0x03);
            case 0xC6 -> executeAluImmediate(ALU_ADD);
            case 0xC7, 0xCF, 0xD7, 0xDF, 0xE7, 0xEF, 0xF7, 0xFF -> rst(opcode & 0x38);
            case 0xC9 -> ret();
            case 0xCD -> callImmediate();
            case 0xCE -> executeAluImmediate(ALU_ADC);

            case 0xD3 -> outImmediateAccumulator();
            case 0xD6 -> executeAluImmediate(ALU_SUB);
            case 0xDB -> inImmediateAccumulator();
            case 0xDE -> executeAluImmediate(ALU_SBB);

            case 0xE3 -> exchangeTopOfStackWithHl();
            case 0xE6 -> executeAluImmediate(ALU_ANA);
            case 0xE9 -> jumpHl();
            case 0xEB -> exchangeDeHl();
            case 0xEE -> executeAluImmediate(ALU_XRA);

            case 0xF3 -> disableInterrupts();
            case 0xF6 -> executeAluImmediate(ALU_ORA);
            case 0xF9 -> loadSpFromHl();
            case 0xFB -> enableInterrupts();
            case 0xFE -> executeAluImmediate(ALU_CMP);

            default -> {
                registers.setPc(opcodeAddress);
                throw new IllegalStateException(
                        "Illegal Intel 8080 opcode 0x%02X at 0x%04X".formatted(opcode & 0xFF, opcodeAddress & 0xFFFF)
                );
            }
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
        return (destination == 6 || source == 6) ? 7 : 5;
    }

    private int executeAluRegisterGroup(int opcode) {
        int operation = (opcode >>> 3) & 0x07;
        int source = opcode & 0x07;
        int value = read8BitOperand(source);
        return executeAluOperation(operation, value, source == 6 ? 7 : 4);
    }

    private int loadRegisterPairImmediate(int pairCode) {
        int value = fetchImmediate16();
        setRegisterPair(pairCode, value);
        return 10;
    }

    private int incrementRegisterPair(int pairCode) {
        setRegisterPair(pairCode, getRegisterPair(pairCode) + 1);
        return 5;
    }

    private int decrementRegisterPair(int pairCode) {
        setRegisterPair(pairCode, getRegisterPair(pairCode) - 1);
        return 5;
    }

    private int loadImmediateIntoOperand(int operandCode) {
        int value = fetchImmediate8();
        write8BitOperand(operandCode, value);
        return operandCode == 6 ? 10 : 7;
    }

    private int increment8BitOperand(int operandCode) {
        int value = read8BitOperand(operandCode);
        int result = (value + 1) & 0xFF;
        int flags = registers.f() & I8080Registers.FLAG_C;
        if ((value & 0x0F) == 0x0F) {
            flags |= I8080Registers.FLAG_AC;
        }
        flags |= signZeroParityFlags(result);
        registers.setF(flags);
        write8BitOperand(operandCode, result);
        return operandCode == 6 ? 10 : 5;
    }

    private int decrement8BitOperand(int operandCode) {
        int value = read8BitOperand(operandCode);
        int result = (value - 1) & 0xFF;
        int flags = registers.f() & I8080Registers.FLAG_C;
        if ((value & 0x0F) == 0x00) {
            flags |= I8080Registers.FLAG_AC;
        }
        flags |= signZeroParityFlags(result);
        registers.setF(flags);
        write8BitOperand(operandCode, result);
        return operandCode == 6 ? 10 : 5;
    }

    private int storeAccumulatorIndirect(int address) {
        writeMemory8(address, registers.a());
        return 7;
    }

    private int loadAccumulatorIndirect(int address) {
        registers.setA(readMemory8(address));
        return 7;
    }

    private int storeHlImmediateAddress() {
        int address = fetchImmediate16();
        writeMemory16(address, registers.hl());
        return 16;
    }

    private int loadHlImmediateAddress() {
        int address = fetchImmediate16();
        registers.setHl(readMemory16(address));
        return 16;
    }

    private int storeAccumulatorImmediateAddress() {
        int address = fetchImmediate16();
        writeMemory8(address, registers.a());
        return 13;
    }

    private int loadAccumulatorImmediateAddress() {
        int address = fetchImmediate16();
        registers.setA(readMemory8(address));
        return 13;
    }

    private int addHl(int value) {
        int hl = registers.hl();
        int result = hl + (value & 0xFFFF);
        registers.setHl(result);
        registers.setFlag(I8080Registers.FLAG_C, result > 0xFFFF);
        return 10;
    }

    private int rotateLeft() {
        int accumulator = registers.a();
        int carry = (accumulator >>> 7) & 0x01;
        registers.setA((accumulator << 1) | carry);
        registers.setFlag(I8080Registers.FLAG_C, carry != 0);
        return 4;
    }

    private int rotateRight() {
        int accumulator = registers.a();
        int carry = accumulator & 0x01;
        registers.setA((accumulator >>> 1) | (carry << 7));
        registers.setFlag(I8080Registers.FLAG_C, carry != 0);
        return 4;
    }

    private int rotateLeftThroughCarry() {
        int accumulator = registers.a();
        int carryIn = registers.flagSet(I8080Registers.FLAG_C) ? 1 : 0;
        int carryOut = (accumulator >>> 7) & 0x01;
        registers.setA((accumulator << 1) | carryIn);
        registers.setFlag(I8080Registers.FLAG_C, carryOut != 0);
        return 4;
    }

    private int rotateRightThroughCarry() {
        int accumulator = registers.a();
        int carryIn = registers.flagSet(I8080Registers.FLAG_C) ? 0x80 : 0;
        int carryOut = accumulator & 0x01;
        registers.setA((accumulator >>> 1) | carryIn);
        registers.setFlag(I8080Registers.FLAG_C, carryOut != 0);
        return 4;
    }

    private int decimalAdjustAccumulator() {
        int accumulator = registers.a();
        int correction = 0;
        boolean auxiliaryCarry = registers.flagSet(I8080Registers.FLAG_AC);
        boolean carry = registers.flagSet(I8080Registers.FLAG_C);

        if ((accumulator & 0x0F) > 9 || auxiliaryCarry) {
            correction |= 0x06;
        }

        int adjustedLow = accumulator + correction;
        if ((adjustedLow & 0x1F0) > 0x90 || carry) {
            correction |= 0x60;
        }

        int result = (accumulator + correction) & 0xFF;
        int flags = signZeroParityFlags(result);
        if (((accumulator & 0x0F) + (correction & 0x0F)) > 0x0F) {
            flags |= I8080Registers.FLAG_AC;
        }
        if ((correction & 0x60) != 0) {
            flags |= I8080Registers.FLAG_C;
        }
        registers.setA(result);
        registers.setF(flags);
        return 4;
    }

    private int complementAccumulator() {
        registers.setA(~registers.a());
        return 4;
    }

    private int setCarry() {
        registers.setFlag(I8080Registers.FLAG_C, true);
        return 4;
    }

    private int complementCarry() {
        registers.setFlag(I8080Registers.FLAG_C, !registers.flagSet(I8080Registers.FLAG_C));
        return 4;
    }

    private int popRegisterPair(int pairCode) {
        int value = popWord();
        setStackRegisterPair(pairCode, value);
        return 10;
    }

    private int pushRegisterPair(int pairCode) {
        pushWord(getStackRegisterPair(pairCode));
        return 11;
    }

    private int jumpImmediate() {
        registers.setPc(fetchImmediate16());
        return 10;
    }

    private int jumpConditional(boolean condition) {
        int address = fetchImmediate16();
        if (condition) {
            registers.setPc(address);
        }
        return 10;
    }

    private int jumpHl() {
        registers.setPc(registers.hl());
        return 5;
    }

    private int callImmediate() {
        int address = fetchImmediate16();
        pushWord(registers.pc());
        registers.setPc(address);
        return 17;
    }

    private int callConditional(boolean condition) {
        int address = fetchImmediate16();
        if (!condition) {
            return 11;
        }

        pushWord(registers.pc());
        registers.setPc(address);
        return 17;
    }

    private int ret() {
        registers.setPc(popWord());
        return 10;
    }

    private int retConditional(boolean condition) {
        if (!condition) {
            return 5;
        }
        registers.setPc(popWord());
        return 11;
    }

    private int rst(int vector) {
        pushWord(registers.pc());
        registers.setPc(vector & 0x38);
        return 11;
    }

    private int exchangeTopOfStackWithHl() {
        int stackValue = readMemory16(registers.sp());
        writeMemory16(registers.sp(), registers.hl());
        registers.setHl(stackValue);
        return 18;
    }

    private int exchangeDeHl() {
        int de = registers.de();
        registers.setDe(registers.hl());
        registers.setHl(de);
        return 4;
    }

    private int loadSpFromHl() {
        registers.setSp(registers.hl());
        return 5;
    }

    private int inImmediateAccumulator() {
        registers.setA(bus.readPort(fetchImmediate8()) & 0xFF);
        return 10;
    }

    private int outImmediateAccumulator() {
        int port = fetchImmediate8();
        bus.writePort(port, registers.a());
        return 10;
    }

    private int disableInterrupts() {
        setInterruptsEnabled(false);
        pendingEnableInterrupts = false;
        interruptEnableDelay = 0;
        return 4;
    }

    private int enableInterrupts() {
        pendingEnableInterrupts = true;
        interruptEnableDelay = 1;
        return 4;
    }

    private void setInterruptsEnabled(boolean interruptsEnabled) {
        if (this.interruptsEnabled == interruptsEnabled) {
            return;
        }
        this.interruptsEnabled = interruptsEnabled;
        notifyInteOutputChanged();
    }

    private void notifyInteOutputChanged() {
        if (inteOutputListener != null) {
            inteOutputListener.onInteOutputChanged(interruptsEnabled);
        }
    }

    private int halt() {
        halted = true;
        return 7;
    }

    private int executeAluImmediate(int operation) {
        return executeAluOperation(operation, fetchImmediate8(), 7);
    }

    private int executeAluOperation(int operation, int value, int tStates) {
        int accumulator = registers.a();
        int carryIn = registers.flagSet(I8080Registers.FLAG_C) ? 1 : 0;

        switch (operation) {
            case ALU_ADD -> registers.setA(add8(accumulator, value, 0));
            case ALU_ADC -> registers.setA(add8(accumulator, value, carryIn));
            case ALU_SUB -> registers.setA(subtract8(accumulator, value, 0));
            case ALU_SBB -> registers.setA(subtract8(accumulator, value, carryIn));
            case ALU_ANA -> {
                int result = accumulator & value;
                registers.setA(result);
                setLogicFlags(result, ((accumulator | value) & 0x08) != 0, false);
            }
            case ALU_XRA -> {
                int result = accumulator ^ value;
                registers.setA(result);
                setLogicFlags(result, false, false);
            }
            case ALU_ORA -> {
                int result = accumulator | value;
                registers.setA(result);
                setLogicFlags(result, false, false);
            }
            case ALU_CMP -> compare8(accumulator, value);
            default -> throw new IllegalStateException("Unexpected ALU operation " + operation);
        }

        return tStates;
    }

    private int add8(int left, int right, int carryIn) {
        int result = left + right + carryIn;
        int flags = signZeroParityFlags(result);
        if (((left & 0x0F) + (right & 0x0F) + carryIn) > 0x0F) {
            flags |= I8080Registers.FLAG_AC;
        }
        if (result > 0xFF) {
            flags |= I8080Registers.FLAG_C;
        }
        registers.setF(flags);
        return result;
    }

    private int subtract8(int left, int right, int carryIn) {
        int result = (left - right - carryIn) & 0x1FF;
        int flags = signZeroParityFlags(result);
        if ((left & 0x0F) < ((right & 0x0F) + carryIn)) {
            flags |= I8080Registers.FLAG_AC;
        }
        if ((left & 0x1FF) < ((right & 0x1FF) + carryIn)) {
            flags |= I8080Registers.FLAG_C;
        }
        registers.setF(flags);
        return result;
    }

    private void compare8(int accumulator, int value) {
        subtract8(accumulator, value, 0);
    }

    private void setLogicFlags(int result, boolean auxiliaryCarry, boolean carry) {
        int flags = signZeroParityFlags(result);
        if (auxiliaryCarry) {
            flags |= I8080Registers.FLAG_AC;
        }
        if (carry) {
            flags |= I8080Registers.FLAG_C;
        }
        registers.setF(flags);
    }

    private int signZeroParityFlags(int value) {
        int normalized = value & 0xFF;
        int flags = 0;
        if ((normalized & 0x80) != 0) {
            flags |= I8080Registers.FLAG_S;
        }
        if (normalized == 0) {
            flags |= I8080Registers.FLAG_Z;
        }
        if ((Integer.bitCount(normalized) & 0x01) == 0) {
            flags |= I8080Registers.FLAG_P;
        }
        return flags;
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

    private int getRegisterPair(int pairCode) {
        return switch (pairCode & 0x03) {
            case 0 -> registers.bc();
            case 1 -> registers.de();
            case 2 -> registers.hl();
            case 3 -> registers.sp();
            default -> throw new IllegalStateException("Unexpected register pair");
        };
    }

    private void setRegisterPair(int pairCode, int value) {
        switch (pairCode & 0x03) {
            case 0 -> registers.setBc(value);
            case 1 -> registers.setDe(value);
            case 2 -> registers.setHl(value);
            case 3 -> registers.setSp(value);
            default -> throw new IllegalStateException("Unexpected register pair");
        }
    }

    private int getStackRegisterPair(int pairCode) {
        return switch (pairCode & 0x03) {
            case 0 -> registers.bc();
            case 1 -> registers.de();
            case 2 -> registers.hl();
            case 3 -> registers.af();
            default -> throw new IllegalStateException("Unexpected stack register pair");
        };
    }

    private void setStackRegisterPair(int pairCode, int value) {
        switch (pairCode & 0x03) {
            case 0 -> registers.setBc(value);
            case 1 -> registers.setDe(value);
            case 2 -> registers.setHl(value);
            case 3 -> registers.setAf(value);
            default -> throw new IllegalStateException("Unexpected stack register pair");
        }
    }

    private boolean conditionCode(int conditionCode) {
        return switch (conditionCode & 0x07) {
            case 0 -> !registers.flagSet(I8080Registers.FLAG_Z);
            case 1 -> registers.flagSet(I8080Registers.FLAG_Z);
            case 2 -> !registers.flagSet(I8080Registers.FLAG_C);
            case 3 -> registers.flagSet(I8080Registers.FLAG_C);
            case 4 -> !registers.flagSet(I8080Registers.FLAG_P);
            case 5 -> registers.flagSet(I8080Registers.FLAG_P);
            case 6 -> !registers.flagSet(I8080Registers.FLAG_S);
            case 7 -> registers.flagSet(I8080Registers.FLAG_S);
            default -> throw new IllegalStateException("Unexpected condition code");
        };
    }

    private int fetchOpcode() {
        int address = registers.pc();
        int opcode = bus.fetchOpcode(address) & 0xFF;
        registers.incrementPc(1);
        return opcode;
    }

    private int fetchImmediate8() {
        int address = registers.pc();
        int value = bus.readMemory(address) & 0xFF;
        registers.incrementPc(1);
        return value;
    }

    private int fetchImmediate16() {
        int low = fetchImmediate8();
        int high = fetchImmediate8();
        return (high << 8) | low;
    }

    private int readMemory8(int address) {
        return bus.readMemory(address) & 0xFF;
    }

    private int readMemory16(int address) {
        int low = readMemory8(address);
        int high = readMemory8(address + 1);
        return (high << 8) | low;
    }

    private void writeMemory8(int address, int value) {
        bus.writeMemory(address, value & 0xFF);
    }

    private void writeMemory16(int address, int value) {
        writeMemory8(address, value & 0xFF);
        writeMemory8(address + 1, (value >>> 8) & 0xFF);
    }

    private void pushWord(int value) {
        registers.setSp(registers.sp() - 1);
        writeMemory8(registers.sp(), (value >>> 8) & 0xFF);
        registers.setSp(registers.sp() - 1);
        writeMemory8(registers.sp(), value & 0xFF);
    }

    private int popWord() {
        int low = readMemory8(registers.sp());
        registers.setSp(registers.sp() + 1);
        int high = readMemory8(registers.sp());
        registers.setSp(registers.sp() + 1);
        return (high << 8) | low;
    }

    @FunctionalInterface
    public interface InteOutputListener {
        void onInteOutputChanged(boolean high);
    }
}
