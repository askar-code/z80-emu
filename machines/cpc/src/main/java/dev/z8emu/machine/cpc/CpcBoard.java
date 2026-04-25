package dev.z8emu.machine.cpc;

import dev.z8emu.chip.ay.Ay38912Device;
import dev.z8emu.machine.cpc.device.CpcCrtcDevice;
import dev.z8emu.machine.cpc.device.CpcFdcDevice;
import dev.z8emu.machine.cpc.device.CpcGateArrayDevice;
import dev.z8emu.machine.cpc.device.CpcKeyboardDevice;
import dev.z8emu.machine.cpc.device.CpcPpiDevice;
import dev.z8emu.machine.cpc.disk.CpcDskImage;
import dev.z8emu.machine.cpc.memory.CpcMemory;
import dev.z8emu.machine.cpc.model.CpcModelConfig;
import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.machine.VideoMachineBoard;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Objects;

public final class CpcBoard implements VideoMachineBoard {
    private static final int CRTC_SCANLINE_TSTATES = 256;
    private static final int VSYNC_SCANLINES = 16;

    private final CpcModelConfig modelConfig;
    private final CpcMemory memory;
    private final CpcGateArrayDevice gateArray;
    private final CpcCrtcDevice crtc;
    private final CpcKeyboardDevice keyboard;
    private final Ay38912Device ay;
    private final CpcPpiDevice ppi;
    private final CpcFdcDevice fdc;
    private final CpcBus bus;

    public CpcBoard(byte[] combinedRomImage, TStateCounter clock) {
        this(CpcModelConfig.cpc6128(), new CpcMemory(combinedRomImage), clock, null);
    }

    public CpcBoard(byte[] combinedRomImage, CpcDskImage disk, TStateCounter clock) {
        this(CpcModelConfig.cpc6128(), new CpcMemory(combinedRomImage), clock, disk);
    }

    public CpcBoard(CpcModelConfig modelConfig, CpcMemory memory, TStateCounter clock) {
        this(modelConfig, memory, clock, null);
    }

    public CpcBoard(CpcModelConfig modelConfig, CpcMemory memory, TStateCounter clock, CpcDskImage disk) {
        Objects.requireNonNull(clock, "clock");
        this.modelConfig = Objects.requireNonNull(modelConfig, "modelConfig");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.gateArray = new CpcGateArrayDevice();
        this.crtc = new CpcCrtcDevice();
        this.keyboard = new CpcKeyboardDevice();
        this.ay = new Ay38912Device(modelConfig.cpuClockHz(), modelConfig.psgClockHz());
        this.ppi = new CpcPpiDevice(ay);
        this.fdc = new CpcFdcDevice();
        if (disk != null) {
            this.fdc.insertDisk(disk);
        }
        this.ay.setPortAReadProvider(() -> keyboard.readLine(ppi.selectedKeyboardLine()));
        this.ppi.setPortBInputProvider(() -> portBInput(clock.value(), modelConfig.frameTStates()));
        this.bus = new CpcBus(clock, memory, gateArray, crtc, ppi, fdc);
    }

    @Override
    public CpuBus cpuBus() {
        return bus;
    }

    @Override
    public void reset() {
        memory.reset();
        gateArray.reset();
        crtc.reset();
        keyboard.reset();
        ay.reset();
        ppi.reset();
        fdc.reset();
    }

    @Override
    public void onTStatesElapsed(int tStates, long currentTState) {
        ay.onTStatesElapsed(tStates);
        gateArray.onTStatesElapsed(tStates, currentTState);
    }

    @Override
    public boolean maskableInterruptLineActive(long currentTState) {
        return gateArray.maskableInterruptLineActive();
    }

    @Override
    public FrameBuffer renderVideoFrame() {
        return gateArray.renderFrame(memory, crtc);
    }

    public String modelName() {
        return modelConfig.modelName();
    }

    public CpcModelConfig modelConfig() {
        return modelConfig;
    }

    public CpcMemory memory() {
        return memory;
    }

    public CpcGateArrayDevice gateArray() {
        return gateArray;
    }

    public CpcCrtcDevice crtc() {
        return crtc;
    }

    public CpcKeyboardDevice keyboard() {
        return keyboard;
    }

    public Ay38912Device ay() {
        return ay;
    }

    public CpcPpiDevice ppi() {
        return ppi;
    }

    public CpcFdcDevice fdc() {
        return fdc;
    }

    public PcmMonoSource audio() {
        return ay;
    }

    private static int portBInput(long currentTState, int frameTStates) {
        int frameOffset = Math.floorMod((int) (currentTState % frameTStates), frameTStates);
        boolean vsyncActive = frameOffset < VSYNC_SCANLINES * CRTC_SCANLINE_TSTATES;
        return vsyncActive ? 0xFF : 0xFE;
    }
}
