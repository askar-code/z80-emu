package dev.z8emu.machine.spectrum128k;

import dev.z8emu.machine.spectrum.model.SpectrumPagingController;
import dev.z8emu.machine.spectrum.model.SpectrumContentionModel;
import dev.z8emu.machine.spectrum128k.device.Ay38912Device;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Spectrum128Bus implements CpuBus {
    private static final int CONTENTION_START_128K = 14_361;
    private static final int T_STATES_PER_SCANLINE_128K = 228;
    private static final int AY_REGISTER_PORT_MASK = 0xC002;
    private static final int AY_REGISTER_PORT_VALUE = 0xC000;
    private static final int AY_DATA_PORT_MASK = 0xC002;
    private static final int AY_DATA_PORT_VALUE = 0x8000;

    private final TStateCounter clock;
    private final Spectrum48kMemoryMap memory;
    private final SpectrumPagingController pagingController;
    private final SpectrumUlaDevice ula;
    private final KeyboardMatrixDevice keyboard;
    private final BeeperDevice beeper;
    private final TapeDevice tape;
    private final Ay38912Device ay;
    private final SpectrumContentionModel contentionModel;

    public Spectrum128Bus(
            TStateCounter clock,
            Spectrum48kMemoryMap memory,
            SpectrumPagingController pagingController,
            SpectrumUlaDevice ula,
            KeyboardMatrixDevice keyboard,
            BeeperDevice beeper,
            TapeDevice tape,
            Ay38912Device ay
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.pagingController = Objects.requireNonNull(pagingController, "pagingController");
        this.ula = Objects.requireNonNull(ula, "ula");
        this.keyboard = Objects.requireNonNull(keyboard, "keyboard");
        this.beeper = Objects.requireNonNull(beeper, "beeper");
        this.tape = Objects.requireNonNull(tape, "tape");
        this.ay = Objects.requireNonNull(ay, "ay");
        this.contentionModel = new SpectrumContentionModel(
                70_908,
                CONTENTION_START_128K,
                T_STATES_PER_SCANLINE_128K
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
        if (isAyRegisterPort(port)) {
            return ay.readSelectedRegister();
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
        if (isAyRegisterPort(port)) {
            return ay.readSelectedRegister();
        }

        return ula.readFloatingBus(memory, clock.value() + Math.max(0, phaseTStates) + readPortWaitStates(port, phaseTStates));
    }

    @Override
    public void writePort(int port, int value) {
        writePort(port, value, 0);
    }

    @Override
    public void writePort(int port, int value, int phaseTStates) {
        if (pagingController.handlesPortWrite(port)) {
            long eventTime = clock.value() + Math.max(0, phaseTStates) + writePortWaitStates(port, value, phaseTStates);
            ula.syncToTState(eventTime, memory);
            pagingController.handlePortWrite(port, value);
            return;
        }
        if ((port & 0xFF) == 0xFE) {
            long eventTime = clock.value() + Math.max(0, phaseTStates) + writePortWaitStates(port, value, phaseTStates);
            ula.writePortFe(value, eventTime, beeper, memory);
            return;
        }
        if (isAyRegisterPort(port)) {
            ay.selectRegister(value);
            return;
        }
        if (isAyDataPort(port)) {
            ay.writeSelectedRegister(value);
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

    private boolean isAyRegisterPort(int port) {
        return (port & AY_REGISTER_PORT_MASK) == AY_REGISTER_PORT_VALUE;
    }

    private boolean isAyDataPort(int port) {
        return (port & AY_DATA_PORT_MASK) == AY_DATA_PORT_VALUE;
    }
}
