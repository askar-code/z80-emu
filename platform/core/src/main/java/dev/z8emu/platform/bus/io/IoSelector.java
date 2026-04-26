package dev.z8emu.platform.bus.io;

public interface IoSelector {
    boolean matches(int address);

    int offset(int address);

    static IoSelector exact(int address) {
        int normalized = address & 0xFFFF;
        return range(normalized, normalized);
    }

    static IoSelector range(int startInclusive, int endInclusive) {
        return mirroredRange(startInclusive, endInclusive, 0);
    }

    static IoSelector mirroredRange(int startInclusive, int endInclusive, int mirrorMask) {
        if (startInclusive < 0 || endInclusive < startInclusive || endInclusive > 0xFFFF) {
            throw new IllegalArgumentException("invalid I/O range");
        }
        int start = startInclusive & 0xFFFF;
        int end = endInclusive & 0xFFFF;
        int mirrors = mirrorMask & 0xFFFF;
        return new IoSelector() {
            @Override
            public boolean matches(int address) {
                int unmirrored = (address & 0xFFFF) & ~mirrors;
                return unmirrored >= start && unmirrored <= end;
            }

            @Override
            public int offset(int address) {
                return (((address & 0xFFFF) & ~mirrors) - start) & 0xFFFF;
            }
        };
    }

    static IoSelector mask(int decodeMask, int decodeValue) {
        return mask(decodeMask, decodeValue, 0xFFFF, 0);
    }

    static IoSelector mask(int decodeMask, int decodeValue, int offsetMask, int offsetShift) {
        if (offsetShift < 0 || offsetShift > 15) {
            throw new IllegalArgumentException("offsetShift out of range");
        }
        int mask = decodeMask & 0xFFFF;
        int value = decodeValue & mask;
        int localOffsetMask = offsetMask & 0xFFFF;
        return new IoSelector() {
            @Override
            public boolean matches(int address) {
                return ((address & 0xFFFF) & mask) == value;
            }

            @Override
            public int offset(int address) {
                return ((address & 0xFFFF) & localOffsetMask) >>> offsetShift;
            }
        };
    }
}
