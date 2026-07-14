package dev.z8emu.machine.apple2.disk;

final class CyclingSwimMediaStream implements Apple2SwimMediaStream {
    private final int[] bytes;
    private int offset;

    CyclingSwimMediaStream(int... bytes) {
        this.bytes = bytes;
    }

    @Override
    public int nextByte() {
        int value = bytes[offset];
        offset = (offset + 1) % bytes.length;
        return value;
    }

    @Override
    public void reset() {
        offset = 0;
    }
}
