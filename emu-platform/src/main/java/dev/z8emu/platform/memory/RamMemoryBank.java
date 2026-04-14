package dev.z8emu.platform.memory;

import java.util.Arrays;

public final class RamMemoryBank implements MemoryBank {
    private final byte[] data;

    public RamMemoryBank(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        this.data = new byte[length];
    }

    @Override
    public int length() {
        return data.length;
    }

    @Override
    public int read(int offset) {
        return Byte.toUnsignedInt(data[offset]);
    }

    @Override
    public void write(int offset, int value) {
        data[offset] = (byte) value;
    }

    @Override
    public void reset() {
        Arrays.fill(data, (byte) 0);
    }
}
