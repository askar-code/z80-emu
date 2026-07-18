package dev.z8emu.platform.bus;

public interface CpuBus {
    int NO_ADDRESS = -1;

    enum InternalCycleType {
        READ_NO_MREQ,
        WRITE_NO_MREQ,
        INTERRUPT_ACKNOWLEDGE,
        NON_MASKABLE_INTERRUPT_ACKNOWLEDGE
    }

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

    /**
     * Observes a consecutive run of internal Z80 t-states and returns any
     * wait states inserted by the bus. The phase is relative to the start of
     * the current instruction and already includes waits from earlier cycles.
     * Opaque acknowledge periods for which the Z80 reference exposes no
     * memory-bus address use {@link #NO_ADDRESS}.
     *
     * <p>For {@code *_NO_MREQ}, implementations must evaluate every t-state
     * in order: a wait inserted for one t-state shifts the contention point of
     * every following t-state in the run.</p>
     */
    default int internalCycleWaitStates(
            int address,
            int phaseTStates,
            int tStates,
            InternalCycleType type
    ) {
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
