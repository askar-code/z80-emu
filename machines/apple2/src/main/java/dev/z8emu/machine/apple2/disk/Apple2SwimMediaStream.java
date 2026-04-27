package dev.z8emu.machine.apple2.disk;

public interface Apple2SwimMediaStream {
    int nextByte();

    default void reset() {
    }
}
