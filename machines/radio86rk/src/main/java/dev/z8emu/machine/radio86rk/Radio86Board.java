package dev.z8emu.machine.radio86rk;

import dev.z8emu.machine.radio86rk.device.Radio86KeyboardDevice;
import dev.z8emu.machine.radio86rk.device.Radio86DmaDevice;
import dev.z8emu.machine.radio86rk.device.Radio86AudioDevice;
import dev.z8emu.machine.radio86rk.device.Radio86TapeDevice;
import dev.z8emu.machine.radio86rk.device.Radio86VideoDevice;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.machine.radio86rk.model.Radio86ModelConfig;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.machine.VideoMachineBoard;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Objects;

public final class Radio86Board implements VideoMachineBoard {
    private final Radio86ModelConfig modelConfig;
    private final Radio86Memory memory;
    private final Radio86KeyboardDevice keyboard;
    private final Radio86DmaDevice dma;
    private final Radio86AudioDevice audio;
    private final Radio86TapeDevice tape;
    private final Radio86VideoDevice video;
    private final Radio86Bus bus;
    private boolean cpuInteOutputHigh;

    public Radio86Board(byte[] romImage, TStateCounter clock) {
        this(romImage, clock, null);
    }

    public Radio86Board(byte[] romImage, TStateCounter clock, Radio86Bus.AccessTraceListener traceListener) {
        Objects.requireNonNull(clock, "clock");
        this.modelConfig = Radio86ModelConfig.radio86rk();
        this.memory = new Radio86Memory(romImage);
        this.keyboard = new Radio86KeyboardDevice();
        this.dma = new Radio86DmaDevice();
        this.audio = new Radio86AudioDevice(modelConfig.cpuClockHz());
        this.tape = new Radio86TapeDevice();
        this.video = new Radio86VideoDevice(dma);
        this.bus = new Radio86Bus(clock, memory, keyboard, dma, video, traceListener);
    }

    @Override
    public CpuBus cpuBus() {
        return bus;
    }

    @Override
    public void reset() {
        memory.reset();
        keyboard.reset();
        dma.reset();
        audio.reset();
        tape.reset();
        video.reset();
        cpuInteOutputHigh = false;
    }

    @Override
    public void onTStatesElapsed(int tStates, long currentTState) {
        tape.syncToTState(currentTState);
        tape.setOutputHigh((keyboard.portCOutput() & 0x01) != 0);
        keyboard.setTapeInputLow(tape.inputLow());
        audio.setSpeakerHigh(cpuInteOutputHigh);
        audio.setTapeOutputHigh(tape.outputHigh());
        audio.onTStatesElapsed(tStates);
        video.onTStatesElapsed(tStates);
    }

    @Override
    public FrameBuffer renderVideoFrame() {
        return video.renderFrame(memory);
    }

    public String modelName() {
        return modelConfig.modelName();
    }

    public Radio86ModelConfig modelConfig() {
        return modelConfig;
    }

    public Radio86Memory memory() {
        return memory;
    }

    public Radio86KeyboardDevice keyboard() {
        return keyboard;
    }

    public Radio86VideoDevice video() {
        return video;
    }

    public Radio86DmaDevice dma() {
        return dma;
    }

    public Radio86TapeDevice tape() {
        return tape;
    }

    public Radio86AudioDevice audio() {
        return audio;
    }

    public boolean cpuInteOutputHigh() {
        return cpuInteOutputHigh;
    }

    public void setCpuInteOutputHigh(boolean cpuInteOutputHigh) {
        this.cpuInteOutputHigh = cpuInteOutputHigh;
    }
}
