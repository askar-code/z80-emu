package dev.z8emu.machine.c64;

public final class C64CpuPort {
    public static final int PULL_UP_BITS = 0x17;

    private int direction;
    private int dataLatch;

    public int readDirection() {
        return direction;
    }

    public void writeDirection(int value) {
        direction = value & 0xFF;
    }

    public int readData() {
        return (dataLatch & direction) | (PULL_UP_BITS & ~direction & 0xFF);
    }

    public void writeData(int value) {
        dataLatch = value & 0xFF;
    }

    public boolean loram() {
        return (readData() & 0x01) != 0;
    }

    public boolean hiram() {
        return (readData() & 0x02) != 0;
    }

    public boolean charen() {
        return (readData() & 0x04) != 0;
    }

    public void reset() {
        direction = 0x2F;
        dataLatch = 0x27;
    }
}
