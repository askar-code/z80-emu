package dev.z8emu.platform.memory;

import java.util.Arrays;
import java.util.Objects;

public final class ReadOnlyMemoryBank implements MemoryBank {
    private final byte[] data;

    public ReadOnlyMemoryBank(byte[] image) {
        Objects.requireNonNull(image, "image");
        if (image.length == 0) {
            throw new IllegalArgumentException("image must not be empty");
        }
        this.data = Arrays.copyOf(image, image.length);
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
        // ROM ignores writes.
    }
}
