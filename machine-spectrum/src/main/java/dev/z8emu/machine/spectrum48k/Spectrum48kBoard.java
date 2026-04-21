package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum.SpectrumBoard;
import dev.z8emu.machine.spectrum.model.SpectrumPagingController;
import dev.z8emu.machine.spectrum.model.SpectrumMachineState;
import dev.z8emu.machine.spectrum.model.SpectrumModelConfig;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.machine.MachineBoard;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Objects;

public final class Spectrum48kBoard implements SpectrumBoard {
    private final TStateCounter clock;
    private final SpectrumModelConfig modelConfig;
    private final SpectrumMachineState machineState;
    private final Spectrum48kMemoryMap memory;
    private final SpectrumPagingController pagingController;
    private final KeyboardMatrixDevice keyboard;
    private final BeeperDevice beeper;
    private final TapeDevice tape;
    private final SpectrumUlaDevice ula;
    private final Spectrum48kBus bus;

    public Spectrum48kBoard(byte[] romImage, TStateCounter clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.modelConfig = SpectrumModelConfig.spectrum48k();
        this.machineState = new SpectrumMachineState(modelConfig);
        this.memory = new Spectrum48kMemoryMap(modelConfig, machineState, romImage);
        this.pagingController = new SpectrumPagingController(modelConfig, machineState, memory);
        this.keyboard = new KeyboardMatrixDevice();
        this.beeper = new BeeperDevice();
        this.tape = new TapeDevice(modelConfig.cpuClockHz(), true);
        this.ula = new SpectrumUlaDevice();
        this.bus = new Spectrum48kBus(clock, memory, pagingController, ula, keyboard, beeper, tape);
    }

    @Override
    public CpuBus cpuBus() {
        return bus;
    }

    @Override
    public void reset() {
        machineState.reset();
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
        beeper.setTapeInputLevel(tape.isPlaying() && tape.earHigh());
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

    @Override
    public PcmMonoSource audio() {
        return beeper;
    }

    @Override
    public SpectrumUlaDevice ula() {
        return ula;
    }

    public TapeDevice tape() {
        return tape;
    }

    public Spectrum48kMemoryMap memory() {
        return memory;
    }

    public SpectrumModelConfig modelConfig() {
        return modelConfig;
    }

    public SpectrumMachineState machineState() {
        return machineState;
    }

    public SpectrumPagingController pagingController() {
        return pagingController;
    }

    public FrameBuffer renderVideoFrame() {
        return ula.renderFrame(memory);
    }
}
