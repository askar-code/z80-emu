package dev.z8emu.machine.c64.device;

import dev.z8emu.platform.device.TimedDevice;

public final class C64CiaDevice implements TimedDevice {
    public interface PortInputs {
        int portA(int drivenPortA, int drivenPortB);

        int portB(int drivenPortA, int drivenPortB);
    }

    private static final int TIMER_A_INTERRUPT = 0x01;
    private static final int TIMER_B_INTERRUPT = 0x02;
    private static final int START = 0x01;
    private static final int RUNMODE = 0x08;
    private static final int FORCE_LOAD = 0x10;
    private static final int TOD_TENTH_T_STATES = 98_525;

    private int portALatch;
    private int portBLatch;
    private int dataDirectionA;
    private int dataDirectionB;
    private int timerALatch;
    private int timerACounter;
    private int timerBLatch;
    private int timerBCounter;
    private int serialData;
    private int interruptFlags;
    private int interruptMask;
    private boolean interruptRaised;
    private int controlA;
    private int controlB;
    private int todTenths;
    private int todSeconds;
    private int todMinutes;
    private int todHours;
    private int latchedTodTenths;
    private int latchedTodSeconds;
    private int latchedTodMinutes;
    private int latchedTodHours;
    private int todTickAccumulator;
    private boolean todHalted;
    private boolean todLatched;
    private PortInputs portInputs;

    public int readRegister(int registerIndex) {
        return switch (registerIndex & 0x0F) {
            case 0x00 -> readPortA();
            case 0x01 -> readPortB();
            case 0x02 -> dataDirectionA;
            case 0x03 -> dataDirectionB;
            case 0x04 -> timerACounter & 0xFF;
            case 0x05 -> (timerACounter >>> 8) & 0xFF;
            case 0x06 -> timerBCounter & 0xFF;
            case 0x07 -> (timerBCounter >>> 8) & 0xFF;
            case 0x08 -> readTodTenths();
            case 0x09 -> todLatched ? latchedTodSeconds : todSeconds;
            case 0x0A -> todLatched ? latchedTodMinutes : todMinutes;
            case 0x0B -> readTodHours();
            case 0x0C -> serialData;
            case 0x0D -> readInterruptControl();
            case 0x0E -> controlA;
            case 0x0F -> controlB;
            default -> 0xFF;
        };
    }

    public void writeRegister(int registerIndex, int value) {
        int byteValue = value & 0xFF;
        switch (registerIndex & 0x0F) {
            case 0x00 -> portALatch = byteValue;
            case 0x01 -> portBLatch = byteValue;
            case 0x02 -> dataDirectionA = byteValue;
            case 0x03 -> dataDirectionB = byteValue;
            case 0x04 -> timerALatch = (timerALatch & 0xFF00) | byteValue;
            case 0x05 -> writeTimerAHigh(byteValue);
            case 0x06 -> timerBLatch = (timerBLatch & 0xFF00) | byteValue;
            case 0x07 -> writeTimerBHigh(byteValue);
            case 0x08 -> writeTodTenths(byteValue);
            case 0x09 -> todSeconds = byteValue;
            case 0x0A -> todMinutes = byteValue;
            case 0x0B -> writeTodHours(byteValue);
            case 0x0C -> serialData = byteValue;
            case 0x0D -> writeInterruptControl(byteValue);
            case 0x0E -> writeControlA(byteValue);
            case 0x0F -> writeControlB(byteValue);
            default -> {
            }
        }
    }

    public boolean interruptLineActive() {
        return interruptRaised;
    }

    public void setPortInputs(PortInputs portInputs) {
        this.portInputs = portInputs;
    }

    @Override
    public void reset() {
        portALatch = 0;
        portBLatch = 0;
        dataDirectionA = 0;
        dataDirectionB = 0;
        timerALatch = 0xFFFF;
        timerACounter = 0xFFFF;
        timerBLatch = 0xFFFF;
        timerBCounter = 0xFFFF;
        serialData = 0;
        interruptFlags = 0;
        interruptMask = 0;
        interruptRaised = false;
        controlA = 0;
        controlB = 0;
        todTenths = 0;
        todSeconds = 0;
        todMinutes = 0;
        todHours = 0x01;
        latchedTodTenths = 0;
        latchedTodSeconds = 0;
        latchedTodMinutes = 0;
        latchedTodHours = 0;
        todTickAccumulator = 0;
        todHalted = true;
        todLatched = false;
    }

    @Override
    public void onTStatesElapsed(int tStates) {
        for (int cycle = 0; cycle < tStates; cycle++) {
            boolean timerAUnderflow = tickTimerA();
            tickTimerB(timerAUnderflow);
            tickTod();
        }
    }

    private int readPortA() {
        int drivenA = drivenPort(portALatch, dataDirectionA);
        int drivenB = drivenPort(portBLatch, dataDirectionB);
        int externalA = portInputs == null ? 0xFF : portInputs.portA(drivenA, drivenB);
        return drivenA & externalA;
    }

    private int readPortB() {
        int drivenA = drivenPort(portALatch, dataDirectionA);
        int drivenB = drivenPort(portBLatch, dataDirectionB);
        int externalB = portInputs == null ? 0xFF : portInputs.portB(drivenA, drivenB);
        return drivenB & externalB;
    }

    private static int drivenPort(int latch, int dataDirection) {
        return (latch | ~dataDirection) & 0xFF;
    }

    private void writeTimerAHigh(int value) {
        timerALatch = (timerALatch & 0x00FF) | (value << 8);
        if ((controlA & START) == 0 || (controlA & RUNMODE) != 0) {
            timerACounter = timerALatch;
        }
        if ((controlA & RUNMODE) != 0) {
            controlA |= START;
        }
    }

    private void writeTimerBHigh(int value) {
        timerBLatch = (timerBLatch & 0x00FF) | (value << 8);
        if ((controlB & START) == 0 || (controlB & RUNMODE) != 0) {
            timerBCounter = timerBLatch;
        }
        if ((controlB & RUNMODE) != 0) {
            controlB |= START;
        }
    }

    private void writeControlA(int value) {
        controlA = value & ~FORCE_LOAD;
        if ((value & FORCE_LOAD) != 0) {
            timerACounter = timerALatch;
        }
    }

    private void writeControlB(int value) {
        controlB = value & ~FORCE_LOAD;
        if ((value & FORCE_LOAD) != 0) {
            timerBCounter = timerBLatch;
        }
    }

    private boolean tickTimerA() {
        if ((controlA & START) == 0 || (controlA & 0x20) != 0) {
            return false;
        }
        if (timerACounter != 0) {
            timerACounter--;
            return false;
        }

        timerACounter = timerALatch;
        raiseInterruptFlag(TIMER_A_INTERRUPT);
        if ((controlA & RUNMODE) != 0) {
            controlA &= ~START;
        }
        return true;
    }

    private void tickTimerB(boolean timerAUnderflow) {
        if ((controlB & START) == 0) {
            return;
        }

        int inputMode = (controlB >>> 5) & 0x03;
        boolean tick = inputMode == 0 || (inputMode >= 2 && timerAUnderflow);
        if (!tick) {
            return;
        }
        if (timerBCounter != 0) {
            timerBCounter--;
            return;
        }

        timerBCounter = timerBLatch;
        raiseInterruptFlag(TIMER_B_INTERRUPT);
        if ((controlB & RUNMODE) != 0) {
            controlB &= ~START;
        }
    }

    private int readInterruptControl() {
        int value = interruptFlags | (interruptRaised ? 0x80 : 0);
        interruptFlags = 0;
        interruptRaised = false;
        return value;
    }

    private void writeInterruptControl(int value) {
        int listedBits = value & 0x1F;
        if ((value & 0x80) != 0) {
            interruptMask |= listedBits;
        } else {
            interruptMask &= ~listedBits;
        }
        reevaluateInterrupt();
    }

    private void raiseInterruptFlag(int flag) {
        interruptFlags |= flag;
        reevaluateInterrupt();
    }

    private void reevaluateInterrupt() {
        if ((interruptFlags & interruptMask) != 0) {
            interruptRaised = true;
        }
    }

    private int readTodHours() {
        if (!todLatched) {
            latchedTodTenths = todTenths;
            latchedTodSeconds = todSeconds;
            latchedTodMinutes = todMinutes;
            latchedTodHours = todHours;
            todLatched = true;
        }
        return latchedTodHours;
    }

    private int readTodTenths() {
        int value = todLatched ? latchedTodTenths : todTenths;
        todLatched = false;
        return value;
    }

    private void writeTodHours(int value) {
        if ((value & 0x1F) == 0x12) {
            value ^= 0x80;
        }
        todHours = value;
        todTickAccumulator = 0;
        todHalted = true;
    }

    private void writeTodTenths(int value) {
        todTenths = value;
        todHalted = false;
    }

    private void tickTod() {
        if (todHalted) {
            return;
        }
        todTickAccumulator++;
        if (todTickAccumulator == TOD_TENTH_T_STATES) {
            todTickAccumulator = 0;
            advanceTodTenth();
        }
    }

    private void advanceTodTenth() {
        if ((todTenths & 0x0F) != 9) {
            todTenths = (todTenths + 1) & 0xFF;
            return;
        }
        todTenths = 0;
        if (todSeconds != 0x59) {
            todSeconds = incrementBcd(todSeconds);
            return;
        }
        todSeconds = 0;
        if (todMinutes != 0x59) {
            todMinutes = incrementBcd(todMinutes);
            return;
        }
        todMinutes = 0;
        advanceTodHour();
    }

    private void advanceTodHour() {
        int hour = todHours & 0x1F;
        int pm = todHours & 0x80;
        if (hour == 0x11) {
            todHours = (pm ^ 0x80) | 0x12;
        } else if (hour == 0x12) {
            todHours = pm | 0x01;
        } else {
            todHours = pm | incrementBcd(hour);
        }
    }

    private int incrementBcd(int value) {
        int ones = (value & 0x0F) + 1;
        int tens = (value >>> 4) & 0x0F;
        if (ones == 10) {
            ones = 0;
            tens++;
        }
        return (tens << 4) | ones;
    }
}
