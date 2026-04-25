package dev.z8emu.platform.bus;

public interface CpuBus {
    default int fetchOpcode(int address) {
        return readMemory(address);
    }

    default int fetchOpcodeWaitStates(int address, int phaseTStates) {
        return 0;
    }

    int readMemory(int address);

    default int readMemoryWaitStates(int address, int phaseTStates) {
        return 0;
    }

    void writeMemory(int address, int value);

    default int writeMemoryWaitStates(int address, int value, int phaseTStates) {
        return 0;
    }

    default int readPort(int port) {
        return 0xFF;
    }

    default int readPort(int port, int phaseTStates) {
        return readPort(port);
    }

    default int readPortWaitStates(int port, int phaseTStates) {
        return 0;
    }

    default void writePort(int port, int value) {
    }

    default void writePort(int port, int value, int phaseTStates) {
        writePort(port, value);
    }

    default int writePortWaitStates(int port, int value, int phaseTStates) {
        return 0;
    }

    default int acknowledgeInterrupt() {
        return 0xFF;
    }

    default void onRefresh(int irValue) {
    }

    default int currentTState() {
        return 0;
    }
}
