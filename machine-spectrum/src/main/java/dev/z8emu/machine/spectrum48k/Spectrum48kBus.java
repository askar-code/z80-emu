package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum.model.SpectrumContentionModel;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Spectrum48kBus implements CpuBus {
    private static final int CONTENTION_START_48K = 14_335;
    private static final int T_STATES_PER_SCANLINE_48K = 224;

    private final TStateCounter clock;
    private final Spectrum48kMemoryMap memory;
    private final SpectrumUlaDevice ula;
    private final KeyboardMatrixDevice keyboard;
    private final BeeperDevice beeper;
    private final TapeDevice tape;
    private final SpectrumContentionModel contentionModel;

    public Spectrum48kBus(
            TStateCounter clock,
            Spectrum48kMemoryMap memory,
            SpectrumUlaDevice ula,
            KeyboardMatrixDevice keyboard,
            BeeperDevice beeper,
            TapeDevice tape
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
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
    }

    @Override
    public int fetchOpcode(int address) {
        return memory.read(address);
    }

    @Override
    public int fetchOpcodeWaitStates(int address, int phaseTStates) {
        return contentionModel.memoryDelay(clock.value(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public int readMemory(int address) {
        return memory.read(address);
    }

    @Override
    public int readMemoryWaitStates(int address, int phaseTStates) {
        return contentionModel.memoryDelay(clock.value(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public void writeMemory(int address, int value) {
        memory.write(address, value);
    }

    @Override
    public int writeMemoryWaitStates(int address, int value, int phaseTStates) {
        return contentionModel.memoryDelay(clock.value(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public int readPort(int port) {
        if ((port & 0xFF) == 0xFE) {
            return ula.readPortFe(port, keyboard, tape);
        }

        return ula.readFloatingBus(memory, clock.value());
    }

    @Override
    public int readPort(int port, int phaseTStates) {
        if ((port & 0xFF) == 0xFE) {
            long sampleTime = clock.value()
                    + Math.max(0, phaseTStates)
                    + readPortWaitStates(port, phaseTStates);
            tape.syncToTState(sampleTime);
            return ula.readPortFe(port, keyboard, tape);
        }

        return ula.readFloatingBus(memory, clock.value() + Math.max(0, phaseTStates) + readPortWaitStates(port, phaseTStates));
    }

    @Override
    public void writePort(int port, int value) {
        writePort(port, value, 0);
    }

    @Override
    public void writePort(int port, int value, int phaseTStates) {
        if ((port & 0xFF) == 0xFE) {
            long eventTime = clock.value() + Math.max(0, phaseTStates) + writePortWaitStates(port, value, phaseTStates);
            ula.writePortFe(value, eventTime, beeper, memory);
        }
    }

    @Override
    public int readPortWaitStates(int port, int phaseTStates) {
        return contentionModel.ioPortDelay(clock.value(), phaseTStates, port);
    }

    @Override
    public int writePortWaitStates(int port, int value, int phaseTStates) {
        return contentionModel.ioPortDelay(clock.value(), phaseTStates, port);
    }

    @Override
    public int acknowledgeInterrupt() {
        return 0xFF;
    }

    @Override
    public void onRefresh(int irValue) {
        ula.onRefreshAddress(irValue);
    }

    @Override
    public int currentTState() {
        long tState = clock.value();
        return tState > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tState;
    }
}
