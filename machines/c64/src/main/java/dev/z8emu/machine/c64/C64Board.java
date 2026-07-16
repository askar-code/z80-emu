package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.device.C64CiaDevice;
import dev.z8emu.machine.c64.device.C64KeyboardDevice;
import dev.z8emu.machine.c64.device.C64SidDevice;
import dev.z8emu.machine.c64.device.C64VideoDevice;
import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.bus.io.IoTraceSink;
import dev.z8emu.platform.machine.VideoMachineBoard;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Objects;

public final class C64Board implements VideoMachineBoard {
    private final C64ModelConfig modelConfig;
    private final C64Memory memory;
    private final C64CpuPort cpuPort;
    private final C64KeyboardDevice keyboard;
    private final C64CiaDevice cia1;
    private final C64CiaDevice cia2;
    private final C64VideoDevice video;
    private final C64SidDevice sid;
    private final C64Bus bus;

    public C64Board(C64ModelConfig modelConfig, C64Memory memory, TStateCounter clock) {
        this.modelConfig = Objects.requireNonNull(modelConfig, "modelConfig");
        this.memory = Objects.requireNonNull(memory, "memory");
        TStateCounter requiredClock = Objects.requireNonNull(clock, "clock");
        this.cpuPort = new C64CpuPort();
        this.keyboard = new C64KeyboardDevice();
        this.cia1 = new C64CiaDevice();
        this.cia2 = new C64CiaDevice();
        this.cia1.setPortInputs(keyboard);
        this.video = new C64VideoDevice(memory);
        this.sid = new C64SidDevice(modelConfig.cpuClockHz());
        this.bus = new C64Bus(requiredClock, memory, cpuPort, video, sid, cia1, cia2);
    }

    @Override
    public CpuBus cpuBus() {
        return bus;
    }

    @Override
    public void reset() {
        memory.reset();
        cpuPort.reset();
        keyboard.reset();
        cia1.reset();
        cia2.reset();
        video.reset();
        sid.reset();
    }

    @Override
    public void onTStatesElapsed(int tStates, long currentTState) {
        cia1.onTStatesElapsed(tStates);
        cia2.onTStatesElapsed(tStates);
        video.onTStatesElapsed(tStates);
        sid.onTStatesElapsed(tStates);
    }

    @Override
    public boolean maskableInterruptLineActive(long currentTState) {
        return cia1.interruptLineActive() || video.interruptLineActive();
    }

    @Override
    public boolean nonMaskableInterruptLineActive(long currentTState) {
        return cia2.interruptLineActive() || keyboard.restorePressed();
    }

    @Override
    public FrameBuffer renderVideoFrame() {
        return video.renderFrame(cia2.readRegister(0x00));
    }

    public C64Memory memory() {
        return memory;
    }

    public C64CpuPort cpuPort() {
        return cpuPort;
    }

    public C64CiaDevice cia1() {
        return cia1;
    }

    public C64KeyboardDevice keyboard() {
        return keyboard;
    }

    public C64CiaDevice cia2() {
        return cia2;
    }

    public C64VideoDevice video() {
        return video;
    }

    public C64SidDevice sid() {
        return sid;
    }

    public PcmMonoSource audio() {
        return sid;
    }

    public C64ModelConfig modelConfig() {
        return modelConfig;
    }

    public void setIoTraceSink(IoTraceSink traceSink) {
        bus.setIoTraceSink(traceSink);
    }
}
