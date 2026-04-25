package dev.z8emu.machine.spectrum128k;

import dev.z8emu.chip.ay.Ay38912Device;
import dev.z8emu.machine.spectrum.SpectrumBoard;
import dev.z8emu.machine.spectrum.model.SpectrumMachineState;
import dev.z8emu.machine.spectrum.model.SpectrumModelConfig;
import dev.z8emu.machine.spectrum.model.SpectrumPagingController;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.audio.MixedPcmMonoSource;
import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Objects;

public final class Spectrum128Board implements SpectrumBoard {
    private static final int ULA_SCANLINES_PER_FRAME_128K = 311;

    private final SpectrumModelConfig modelConfig;
    private final SpectrumMachineState machineState;
    private final Spectrum48kMemoryMap memory;
    private final KeyboardMatrixDevice keyboard;
    private final BeeperDevice beeper;
    private final Ay38912Device ay;
    private final PcmMonoSource audio;
    private final TapeDevice tape;
    private final SpectrumUlaDevice ula;
    private final Spectrum128Bus bus;

    public Spectrum128Board(byte[] rom0Image, byte[] rom1Image, TStateCounter clock) {
        Objects.requireNonNull(clock, "clock");
        this.modelConfig = SpectrumModelConfig.spectrum128();
        this.machineState = new SpectrumMachineState(modelConfig);
        this.memory = new Spectrum48kMemoryMap(modelConfig, machineState, rom0Image, rom1Image);
        SpectrumPagingController pagingController = new SpectrumPagingController(modelConfig, machineState, memory);
        this.keyboard = new KeyboardMatrixDevice();
        this.beeper = new BeeperDevice(modelConfig.cpuClockHz());
        this.ay = new Ay38912Device(modelConfig.cpuClockHz());
        this.audio = new MixedPcmMonoSource(beeper, ay);
        this.tape = new TapeDevice(modelConfig.cpuClockHz(), false);
        this.ula = new SpectrumUlaDevice(modelConfig.frameTStates(), ULA_SCANLINES_PER_FRAME_128K);
        this.bus = new Spectrum128Bus(clock, memory, pagingController, ula, keyboard, beeper, tape, ay);
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
        ay.reset();
        tape.reset();
        ula.reset();
    }

    @Override
    public void onTStatesElapsed(int tStates, long currentTState) {
        keyboard.onTStatesElapsed(tStates);
        beeper.onTStatesElapsed(tStates);
        ay.onTStatesElapsed(tStates);
        tape.syncToTState(currentTState);
        beeper.setTapeInputLevel(tape.isPlaying() && tape.earHigh());
        ula.onTStatesElapsed(tStates, memory);
    }

    @Override
    public boolean maskableInterruptLineActive(long currentTState) {
        return ula.maskableInterruptLineActive(currentTState);
    }

    @Override
    public KeyboardMatrixDevice keyboard() {
        return keyboard;
    }

    @Override
    public BeeperDevice beeper() {
        return beeper;
    }

    @Override
    public PcmMonoSource audio() {
        return audio;
    }

    @Override
    public SpectrumUlaDevice ula() {
        return ula;
    }

    @Override
    public TapeDevice tape() {
        return tape;
    }

    @Override
    public Spectrum48kMemoryMap memory() {
        return memory;
    }

    @Override
    public SpectrumModelConfig modelConfig() {
        return modelConfig;
    }

    @Override
    public SpectrumMachineState machineState() {
        return machineState;
    }

    public Ay38912Device ay() {
        return ay;
    }

    @Override
    public FrameBuffer renderVideoFrame() {
        return ula.renderFrame(memory);
    }
}
