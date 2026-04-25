package dev.z8emu.platform.bus;

public interface CpuBus {
    int fetchOpcode(int address);

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

    int readPort(int port);

    default int readPort(int port, int phaseTStates) {
        return readPort(port);
    }

    default int readPortWaitStates(int port, int phaseTStates) {
        return 0;
    }

    void writePort(int port, int value);

    default void writePort(int port, int value, int phaseTStates) {
        writePort(port, value);
    }

    default int writePortWaitStates(int port, int value, int phaseTStates) {
        return 0;
    }

    int acknowledgeInterrupt();

    void onRefresh(int irValue);

    int currentTState();
}
