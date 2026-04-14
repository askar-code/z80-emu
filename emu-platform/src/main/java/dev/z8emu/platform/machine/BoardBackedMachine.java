package dev.z8emu.platform.machine;

public interface BoardBackedMachine<B extends MachineBoard> extends Machine {
    B board();
}
