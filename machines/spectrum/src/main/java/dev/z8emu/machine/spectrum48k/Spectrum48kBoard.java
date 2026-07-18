package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum.SpectrumBoard;
import dev.z8emu.machine.spectrum.model.SpectrumMachineState;
import dev.z8emu.machine.spectrum.model.SpectrumModelConfig;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.KempstonJoystickDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Objects;

public final class Spectrum48kBoard implements SpectrumBoard {
    private final SpectrumModelConfig modelConfig;
    private final SpectrumMachineState machineState;
    private final Spectrum48kMemoryMap memory;
    private final KeyboardMatrixDevice keyboard;
    private final KempstonJoystickDevice kempstonJoystick;
    private final BeeperDevice beeper;
    private final TapeDevice tape;
    private final SpectrumUlaDevice ula;
    private final Spectrum48kBus bus;

    public Spectrum48kBoard(byte[] romImage, TStateCounter clock) {
        Objects.requireNonNull(clock, "clock");
        this.modelConfig = SpectrumModelConfig.spectrum48k();
        this.machineState = new SpectrumMachineState(modelConfig);
        this.memory = new Spectrum48kMemoryMap(modelConfig, machineState, romImage);
        this.keyboard = new KeyboardMatrixDevice();
        this.kempstonJoystick = new KempstonJoystickDevice();
        this.beeper = new BeeperDevice(modelConfig.cpuClockHz());
        this.tape = new TapeDevice(modelConfig.cpuClockHz(), true);
        this.ula = new SpectrumUlaDevice(
                modelConfig.frameTStates(),
                modelConfig.scanlinesPerFrame(),
                modelConfig.floatingBusDisplayStartTState(),
                modelConfig.firstDisplayPixelTState()
        );
        this.bus = new Spectrum48kBus(
                clock,
                memory,
                ula,
                keyboard,
                kempstonJoystick,
                beeper,
                tape,
                modelConfig
        );
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
        kempstonJoystick.reset();
        beeper.reset();
        tape.reset();
        ula.reset();
        bus.resetAudioTiming();
    }

    @Override
    public void onTStatesElapsed(int tStates, long currentTState) {
        keyboard.onTStatesElapsed(tStates);
        bus.syncAudioTo(currentTState);
        beeper.setTapeInputLevel(tape.syncAndReadEarLevel(currentTState));
        ula.syncToTState(currentTState, memory);
    }

    @Override
    public boolean maskableInterruptLineActive(long currentTState) {
        return ula.maskableInterruptLineActive(currentTState);
    }

    public KeyboardMatrixDevice keyboard() {
        return keyboard;
    }

    @Override
    public KempstonJoystickDevice kempstonJoystick() {
        return kempstonJoystick;
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

    public FrameBuffer renderVideoFrame() {
        return ula.renderFrame(memory);
    }
}
