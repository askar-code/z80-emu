package dev.z8emu.machine.apple2;

import dev.z8emu.platform.bus.io.IoAccess;

public interface Apple2SlotCard {
    default int readC0x(IoAccess access) {
        return 0x00;
    }

    default void writeC0x(IoAccess access, int value) {
    }

    default int readCnxx(int offset) {
        return 0xFF;
    }

    default void writeCnxx(int offset, int value) {
    }
}
