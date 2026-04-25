package dev.z8emu.machine.apple2;

import dev.z8emu.machine.apple2.device.Apple2KeyboardDevice;
import dev.z8emu.machine.apple2.device.Apple2SpeakerDevice;
import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.machine.VideoMachineBoard;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Objects;

public final class Apple2Board implements VideoMachineBoard {
    private final Apple2ModelConfig modelConfig;
    private final Apple2Memory memory;
    private final Apple2KeyboardDevice keyboard;
    private final Apple2SpeakerDevice speaker;
    private final Apple2SoftSwitches softSwitches;
    private final Apple2VideoDevice video;
    private final Apple2Bus bus;
    private final TStateCounter clock;

    public Apple2Board(Apple2ModelConfig modelConfig, Apple2Memory memory, TStateCounter clock) {
        this.modelConfig = Objects.requireNonNull(modelConfig, "modelConfig");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.keyboard = new Apple2KeyboardDevice();
        this.speaker = new Apple2SpeakerDevice(modelConfig.cpuClockHz());
        this.softSwitches = new Apple2SoftSwitches();
        this.video = new Apple2VideoDevice(modelConfig.frameWidth(), modelConfig.frameHeight());
        this.bus = new Apple2Bus(this.clock, memory, keyboard, speaker, softSwitches);
    }

    @Override
    public CpuBus cpuBus() {
        return bus;
    }

    @Override
    public void reset() {
        memory.reset();
        keyboard.reset();
        speaker.reset();
        softSwitches.reset();
    }

    @Override
    public void onTStatesElapsed(int tStates, long currentTState) {
        speaker.onTStatesElapsed(tStates);
    }

    @Override
    public FrameBuffer renderVideoFrame() {
        return video.renderFrame(memory, softSwitches, clock.value(), modelConfig.frameTStates());
    }

    public String modelName() {
        return modelConfig.modelName();
    }

    public Apple2ModelConfig modelConfig() {
        return modelConfig;
    }

    public Apple2Memory memory() {
        return memory;
    }

    public Apple2KeyboardDevice keyboard() {
        return keyboard;
    }

    public Apple2SpeakerDevice speaker() {
        return speaker;
    }

    public PcmMonoSource audio() {
        return speaker;
    }

    public Apple2SoftSwitches softSwitches() {
        return softSwitches;
    }
}
