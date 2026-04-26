package dev.z8emu.machine.apple2;

import dev.z8emu.machine.apple2.device.Apple2KeyboardDevice;
import dev.z8emu.machine.apple2.device.Apple2SpeakerDevice;
import dev.z8emu.machine.apple2.disk.Apple2Disk2Controller;
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
    private final Apple2LanguageCard languageCard;
    private final Apple2Disk2Controller disk2Controller;
    private final Apple2SlotBus slotBus;
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
        this.languageCard = new Apple2LanguageCard();
        this.disk2Controller = new Apple2Disk2Controller(this.clock);
        this.slotBus = new Apple2SlotBus();
        this.slotBus.install(0, languageCard);
        this.slotBus.install(Apple2Disk2Controller.SLOT, disk2Controller);
        this.video = new Apple2VideoDevice(modelConfig.frameWidth(), modelConfig.frameHeight());
        this.bus = new Apple2Bus(this.clock, memory, keyboard, speaker, softSwitches, languageCard, slotBus);
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
        languageCard.resetSwitches();
        disk2Controller.reset();
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

    public Apple2LanguageCard languageCard() {
        return languageCard;
    }

    public Apple2Disk2Controller disk2Controller() {
        return disk2Controller;
    }

    public Apple2SlotBus slotBus() {
        return slotBus;
    }

    public void loadDisk2SlotRom(byte[] slotRom) {
        disk2Controller.loadSlotRom(slotRom);
    }
}
