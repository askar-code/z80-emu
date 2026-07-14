package dev.z8emu.machine.spectrum;

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

public abstract class SpectrumBusBase extends ClockedCpuBus {
    private static final int ULA_PORT_PRIORITY = 90;

    private final Spectrum48kMemoryMap memory;
    private final SpectrumContentionModel contentionModel;
    private final IoAddressSpace ports;

    protected SpectrumBusBase(
            TStateCounter clock,
            Spectrum48kMemoryMap memory,
            SpectrumUlaDevice ula,
            KeyboardMatrixDevice keyboard,
            BeeperDevice beeper,
            TapeDevice tape,
            int frameTStates,
            int contentionStartTState,
            int tStatesPerScanline
    ) {
        super(clock);
        this.memory = Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(ula, "ula");
        Objects.requireNonNull(keyboard, "keyboard");
        Objects.requireNonNull(beeper, "beeper");
        Objects.requireNonNull(tape, "tape");
        this.contentionModel = new SpectrumContentionModel(
                frameTStates,
                contentionStartTState,
                tStatesPerScanline
        );
        this.ports = new IoAddressSpace(
                access -> ula.readFloatingBus(memory, access.effectiveTState())
        );
        ports.mapReadWrite(
                "spectrum.ula-fe",
                SpectrumUlaDevice.portSelector(),
                access -> ula.readPortFe(access, keyboard, tape),
                (access, value) -> ula.writePortFe(access, value, beeper, memory),
                ULA_PORT_PRIORITY
        );
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

    protected final IoAddressSpace ports() {
        return ports;
    }
}
