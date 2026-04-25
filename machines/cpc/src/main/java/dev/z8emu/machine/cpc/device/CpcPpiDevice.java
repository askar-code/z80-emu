package dev.z8emu.machine.cpc.device;

import dev.z8emu.chip.ay.Ay38912Device;
import java.util.Objects;
import java.util.function.IntSupplier;

public final class CpcPpiDevice {
    public static final int PORT_A = 0;
    public static final int PORT_B = 1;
    public static final int PORT_C = 2;
    public static final int CONTROL = 3;

    private static final int DEFAULT_CONTROL_WORD = 0x82;
    private static final int DEFAULT_PORT_B_INPUT = 0xFE;

    private final Ay38912Device ay;
    private IntSupplier portBInputProvider = () -> DEFAULT_PORT_B_INPUT;

    private int portAOutput;
    private int portBOutput;
    private int portCOutput;
    private int controlWord;

    public CpcPpiDevice(Ay38912Device ay) {
        this.ay = Objects.requireNonNull(ay, "ay");
    }

    public void reset() {
        portAOutput = 0xFF;
        portBOutput = 0xFF;
        portCOutput = 0x00;
        controlWord = DEFAULT_CONTROL_WORD;
    }

    public int readRegister(int register) {
        return switch (register & 0x03) {
            case PORT_A -> readPortA();
            case PORT_B -> readPortB();
            case PORT_C -> readPortC();
            default -> 0xFF;
        };
    }

    public void writeRegister(int register, int value) {
        switch (register & 0x03) {
            case PORT_A -> portAOutput = value & 0xFF;
            case PORT_B -> portBOutput = value & 0xFF;
            case PORT_C -> writePortC(value);
            case CONTROL -> writeControl(value);
            default -> throw new IllegalStateException("Unexpected PPI register");
        }
    }

    public int portAOutput() {
        return portAOutput;
    }

    public int portCOutput() {
        return portCOutput;
    }

    public int controlWord() {
        return controlWord;
    }

    public boolean portAInput() {
        return (controlWord & 0x10) != 0;
    }

    public void setPortBInputProvider(IntSupplier provider) {
        portBInputProvider = Objects.requireNonNull(provider, "provider");
    }

    public int selectedKeyboardLine() {
        return portCOutput & 0x0F;
    }

    private int readPortA() {
        if (!portAInput()) {
            return portAOutput & 0xFF;
        }
        if (psgFunction() == 0x01) {
            return ay.readSelectedRegister();
        }
        return 0xFF;
    }

    private int readPortB() {
        if ((controlWord & 0x02) == 0) {
            return portBOutput & 0xFF;
        }
        return portBInputProvider.getAsInt() & 0xFF;
    }

    private int readPortC() {
        return portCOutput & 0xFF;
    }

    private void writePortC(int value) {
        portCOutput = value & 0xFF;
        applyPsgFunction();
    }

    private void writeControl(int value) {
        int normalized = value & 0xFF;
        if ((normalized & 0x80) != 0) {
            controlWord = normalized;
            return;
        }

        int bit = (normalized >>> 1) & 0x07;
        if ((normalized & 0x01) != 0) {
            portCOutput |= 1 << bit;
        } else {
            portCOutput &= ~(1 << bit);
        }
        applyPsgFunction();
    }

    private void applyPsgFunction() {
        switch (psgFunction()) {
            case 0x02 -> ay.writeSelectedRegister(portAOutput);
            case 0x03 -> ay.selectRegister(portAOutput);
            default -> {
            }
        }
    }

    private int psgFunction() {
        return (portCOutput >>> 6) & 0x03;
    }
}
