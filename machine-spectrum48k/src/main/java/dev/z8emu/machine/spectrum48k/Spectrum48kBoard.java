package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.machine.MachineBoard;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Objects;

public final class Spectrum48kBoard implements MachineBoard {
    private final TStateCounter clock;
    private final Spectrum48kMemoryMap memory;
    private final KeyboardMatrixDevice keyboard;
    private final BeeperDevice beeper;
    private final TapeDevice tape;
    private final SpectrumUlaDevice ula;
    private final Spectrum48kBus bus;

    public Spectrum48kBoard(byte[] romImage, TStateCounter clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.memory = new Spectrum48kMemoryMap(romImage);
        this.keyboard = new KeyboardMatrixDevice();
        this.beeper = new BeeperDevice();
        this.tape = new TapeDevice();
        this.ula = new SpectrumUlaDevice();
        this.bus = new Spectrum48kBus(clock, memory, ula, keyboard, beeper, tape);
    }

    @Override
    public CpuBus cpuBus() {
        return bus;
    }

    @Override
    public void reset() {
        memory.reset();
        keyboard.reset();
        beeper.reset();
        tape.reset();
        ula.reset();
    }

    @Override
    public void onTStatesElapsed(int tStates, long currentTState) {
        keyboard.onTStatesElapsed(tStates);
        beeper.onTStatesElapsed(tStates);
        tape.syncToTState(currentTState);
        ula.onTStatesElapsed(tStates);
    }

    @Override
    public boolean consumeMaskableInterrupt() {
        return ula.consumeMaskableInterrupt();
    }

    public KeyboardMatrixDevice keyboard() {
        return keyboard;
    }

    public BeeperDevice beeper() {
        return beeper;
    }

    public SpectrumUlaDevice ula() {
        return ula;
    }

    public TapeDevice tape() {
        return tape;
    }

    public Spectrum48kMemoryMap memory() {
        return memory;
    }

    public FrameBuffer renderVideoFrame() {
        return ula.renderFrame(memory);
    }
}
