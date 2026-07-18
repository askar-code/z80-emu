package dev.z8emu.machine.spectrum.snapshot;

final class SnapshotBytes {
    private SnapshotBytes() {
    }

    static int u8(byte[] image, int offset) {
        return Byte.toUnsignedInt(image[offset]);
    }

    static int le16(byte[] image, int offset) {
        return u8(image, offset) | (u8(image, offset + 1) << 8);
    }

    static void put8(byte[] image, int offset, int value) {
        image[offset] = (byte) value;
    }

    static void putLe16(byte[] image, int offset, int value) {
        image[offset] = (byte) value;
        image[offset + 1] = (byte) (value >>> 8);
    }
}
