package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum.model.SpectrumContentionModel;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.bus.io.IoAddressSpace;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Spectrum48kBus extends ClockedCpuBus {
    private static final int CONTENTION_START_48K = 14_335;
    private static final int T_STATES_PER_SCANLINE_48K = 224;

    private final Spectrum48kMemoryMap memory;
    private final SpectrumUlaDevice ula;
    private final KeyboardMatrixDevice keyboard;
    private final BeeperDevice beeper;
    private final TapeDevice tape;
    private final SpectrumContentionModel contentionModel;
    private final IoAddressSpace ports;

    public Spectrum48kBus(
            TStateCounter clock,
            Spectrum48kMemoryMap memory,
            SpectrumUlaDevice ula,
            KeyboardMatrixDevice keyboard,
            BeeperDevice beeper,
            TapeDevice tape
    ) {
        super(clock);
        this.memory = Objects.requireNonNull(memory, "memory");
        this.ula = Objects.requireNonNull(ula, "ula");
        this.keyboard = Objects.requireNonNull(keyboard, "keyboard");
        this.beeper = Objects.requireNonNull(beeper, "beeper");
        this.tape = Objects.requireNonNull(tape, "tape");
        this.contentionModel = new SpectrumContentionModel(
                SpectrumUlaDevice.T_STATES_PER_FRAME,
                CONTENTION_START_48K,
                T_STATES_PER_SCANLINE_48K
        );
        this.ports = buildPortMap();
    }

    @Override
    public int fetchOpcodeWaitStates(int address, int phaseTStates) {
        return contentionModel.memoryDelay(clockValue(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public int readMemory(int address) {
        return memory.read(address);
    }

    @Override
    public int readMemoryWaitStates(int address, int phaseTStates) {
        return contentionModel.memoryDelay(clockValue(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public void writeMemory(int address, int value) {
        memory.write(address, value);
    }

    @Override
    public int writeMemoryWaitStates(int address, int value, int phaseTStates) {
        return contentionModel.memoryDelay(clockValue(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public int readPort(int port) {
        return ports.read(port, clockValue(), 0);
    }

    @Override
    public int readPort(int port, int phaseTStates) {
        return ports.read(port, clockValue() + readPortWaitStates(port, phaseTStates), phaseTStates);
    }

    @Override
    public void writePort(int port, int value) {
        writePort(port, value, 0);
    }

    @Override
    public void writePort(int port, int value, int phaseTStates) {
        ports.write(port, value, clockValue() + writePortWaitStates(port, value, phaseTStates), phaseTStates);
    }

    @Override
    public int readPortWaitStates(int port, int phaseTStates) {
        return contentionModel.ioPortDelay(clockValue(), phaseTStates, port);
    }

    @Override
    public int writePortWaitStates(int port, int value, int phaseTStates) {
        return contentionModel.ioPortDelay(clockValue(), phaseTStates, port);
    }

    @Override
    public void onRefresh(int irValue) {
        ula.onRefreshAddress(irValue);
    }

    private IoAddressSpace buildPortMap() {
        IoAddressSpace portMap = new IoAddressSpace(
                access -> ula.readFloatingBus(memory, access.effectiveTState())
        );
        portMap.mapReadWrite(
                "spectrum.ula-fe",
                SpectrumUlaDevice.portSelector(),
                access -> ula.readPortFe(access, keyboard, tape),
                (access, value) -> ula.writePortFe(access, value, beeper, memory)
        );
        return portMap;
    }
}
