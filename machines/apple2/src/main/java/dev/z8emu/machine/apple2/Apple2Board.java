package dev.z8emu.machine.apple2;

import dev.z8emu.machine.apple2.device.Apple2GamePortDevice;
import dev.z8emu.machine.apple2.device.Apple2KeyboardDevice;
import dev.z8emu.machine.apple2.device.Apple2SpeakerDevice;
import dev.z8emu.machine.apple2.disk.Apple2BlockDevice;
import dev.z8emu.machine.apple2.disk.Apple2Disk2Controller;
import dev.z8emu.machine.apple2.disk.Apple2Disk2TraceSink;
import dev.z8emu.machine.apple2.disk.Apple2ProDosBlockShimController;
import dev.z8emu.machine.apple2.disk.Apple2SuperDriveController;
import dev.z8emu.machine.apple2.disk.Apple2SuperDriveTraceSink;
import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.bus.io.IoTraceSink;
import dev.z8emu.platform.machine.VideoMachineBoard;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Objects;

public final class Apple2Board implements VideoMachineBoard {
    private final Apple2ModelConfig modelConfig;
    private final Apple2Memory memory;
    private final Apple2KeyboardDevice keyboard;
    private final Apple2GamePortDevice gamePort;
    private final Apple2SpeakerDevice speaker;
    private final Apple2SoftSwitches softSwitches;
    private final Apple2AuxMemory auxMemory;
    private final Apple2LanguageCard languageCard;
    private final Apple2Disk2Controller disk2Controller;
    private final Apple2SlotBus slotBus;
    private Apple2ProDosBlockShimController proDosBlockShimController;
    private Apple2SuperDriveController superDrive35Controller;
    private final Apple2VideoDevice video;
    private final Apple2Bus bus;
    private final TStateCounter clock;

    public Apple2Board(Apple2ModelConfig modelConfig, Apple2Memory memory, TStateCounter clock) {
        this.modelConfig = Objects.requireNonNull(modelConfig, "modelConfig");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.keyboard = new Apple2KeyboardDevice();
        this.gamePort = new Apple2GamePortDevice();
        this.speaker = new Apple2SpeakerDevice(modelConfig.cpuClockHz());
        this.softSwitches = new Apple2SoftSwitches();
        this.auxMemory = new Apple2AuxMemory(modelConfig.ramSize() > Apple2Memory.ADDRESS_SPACE_SIZE);
        this.languageCard = new Apple2LanguageCard();
        this.disk2Controller = new Apple2Disk2Controller(this.clock);
        this.slotBus = new Apple2SlotBus();
        this.slotBus.install(0, languageCard);
        this.slotBus.install(Apple2Disk2Controller.SLOT, disk2Controller);
        this.video = new Apple2VideoDevice(modelConfig.frameWidth(), modelConfig.frameHeight());
        this.bus = new Apple2Bus(
                this.clock,
                memory,
                keyboard,
                gamePort,
                speaker,
                softSwitches,
                auxMemory,
                languageCard,
                slotBus,
                modelConfig.frameTStates()
        );
    }

    @Override
    public CpuBus cpuBus() {
        return bus;
    }

    @Override
    public void reset() {
        memory.reset();
        keyboard.reset();
        gamePort.reset();
        speaker.reset();
        softSwitches.reset();
        auxMemory.reset();
        languageCard.resetSwitches();
        disk2Controller.reset();
        if (proDosBlockShimController != null) {
            proDosBlockShimController.reset();
        }
        if (superDrive35Controller != null) {
            superDrive35Controller.reset();
        }
    }

    @Override
    public void onTStatesElapsed(int tStates, long currentTState) {
        speaker.onTStatesElapsed(tStates);
        if (superDrive35Controller != null) {
            superDrive35Controller.onHostTStatesElapsed(tStates);
        }
    }

    @Override
    public FrameBuffer renderVideoFrame() {
        return video.renderFrame(memory, auxMemory, softSwitches, clock.value(), modelConfig.frameTStates());
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

    public Apple2GamePortDevice gamePort() {
        return gamePort;
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

    public Apple2AuxMemory auxMemory() {
        return auxMemory;
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

    public Apple2SuperDriveController superDrive35Controller() {
        return superDrive35Controller;
    }

    public void setIoTraceSink(IoTraceSink traceSink) {
        bus.setIoTraceSink(traceSink);
    }

    public void setDisk2TraceSink(Apple2Disk2TraceSink traceSink) {
        disk2Controller.setTraceSink(traceSink);
    }

    public void setSuperDriveTraceSink(Apple2SuperDriveTraceSink traceSink) {
        if (superDrive35Controller != null) {
            superDrive35Controller.setTraceSink(traceSink);
        }
    }

    public void loadDisk2SlotRom(byte[] slotRom) {
        disk2Controller.loadSlotRom(slotRom);
    }

    public void installProDosBlockShim(Apple2BlockDevice blockDevice) {
        clearSuperDrive35Controller();
        proDosBlockShimController = new Apple2ProDosBlockShimController(blockDevice);
        slotBus.install(Apple2ProDosBlockShimController.SLOT, proDosBlockShimController);
    }

    public Apple2SuperDriveController installSuperDrive35Controller(int slot, byte[] controllerRom) {
        clearSuperDrive35Controller();
        if (proDosBlockShimController != null) {
            slotBus.uninstall(Apple2ProDosBlockShimController.SLOT);
            proDosBlockShimController = null;
        }
        superDrive35Controller = new Apple2SuperDriveController(slot, controllerRom);
        slotBus.install(slot, superDrive35Controller);
        return superDrive35Controller;
    }

    private void clearSuperDrive35Controller() {
        if (superDrive35Controller != null) {
            slotBus.uninstall(superDrive35Controller.slot());
            superDrive35Controller = null;
        }
    }
}
