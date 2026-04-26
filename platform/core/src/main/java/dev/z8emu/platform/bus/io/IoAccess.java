package dev.z8emu.platform.bus.io;

public record IoAccess(int address, int offset, long tState, int phaseTStates) {
    public IoAccess {
        address &= 0xFFFF;
        offset &= 0xFFFF;
    }

    public long effectiveTState() {
        return tState + Math.max(0, phaseTStates);
    }
}
