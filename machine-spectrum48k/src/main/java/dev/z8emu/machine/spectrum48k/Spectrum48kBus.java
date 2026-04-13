package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Spectrum48kBus implements CpuBus {
    private final TStateCounter clock;
    private final Spectrum48kMemoryMap memory;
    private final SpectrumUlaDevice ula;
    private final KeyboardMatrixDevice keyboard;
    private final BeeperDevice beeper;
    private final TapeDevice tape;

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
    }

    @Override
    public int fetchOpcode(int address) {
        return memory.read(address);
    }

    @Override
    public int readMemory(int address) {
        return memory.read(address);
    }

    @Override
    public void writeMemory(int address, int value) {
        memory.write(address, value);
    }

    @Override
    public int readPort(int port) {
        if ((port & 0xFF) == 0xFE) {
            return ula.readPortFe(port, keyboard, tape);
        }

        return 0xFF;
    }

    @Override
    public int readPort(int port, int phaseTStates) {
        if ((port & 0xFF) == 0xFE) {
            long sampleTime = clock.value() + Math.max(0, phaseTStates);
            tape.syncToTState(sampleTime);
            return ula.readPortFe(port, keyboard, tape);
        }

        return readPort(port);
    }

    @Override
    public void writePort(int port, int value) {
        if ((port & 0xFF) == 0xFE) {
            ula.writePortFe(value, beeper);
        }
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
