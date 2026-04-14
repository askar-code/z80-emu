package dev.z8emu.platform.bus;

public interface CpuBus {
    int fetchOpcode(int address);

    int readMemory(int address);

    void writeMemory(int address, int value);

    int readPort(int port);

    default int readPort(int port, int phaseTStates) {
        return readPort(port);
    }

    void writePort(int port, int value);

    default void writePort(int port, int value, int phaseTStates) {
        writePort(port, value);
    }

    int acknowledgeInterrupt();

    void onRefresh(int irValue);

    int currentTState();
}
