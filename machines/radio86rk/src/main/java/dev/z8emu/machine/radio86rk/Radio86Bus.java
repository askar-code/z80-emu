package dev.z8emu.machine.radio86rk;

import dev.z8emu.machine.radio86rk.device.Radio86DmaDevice;
import dev.z8emu.machine.radio86rk.device.Radio86KeyboardDevice;
import dev.z8emu.machine.radio86rk.device.Radio86VideoDevice;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.bus.io.IoAddressSpace;
import dev.z8emu.platform.bus.io.IoSelector;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Radio86Bus extends ClockedCpuBus {
    private static final int KEYBOARD_BASE = 0x8000;
    private static final int VIDEO_BASE = 0xC000;
    private static final int DMA_BASE = 0xE000;

    private final Radio86Memory memory;
    private final Radio86KeyboardDevice keyboard;
    private final Radio86DmaDevice dma;
    private final Radio86VideoDevice video;
    private final IoAddressSpace memoryMappedIo;

    public Radio86Bus(
            TStateCounter clock,
            Radio86Memory memory,
            Radio86KeyboardDevice keyboard,
            Radio86DmaDevice dma,
            Radio86VideoDevice video
    ) {
        this(clock, memory, keyboard, dma, video, null);
    }

    public Radio86Bus(
            TStateCounter clock,
            Radio86Memory memory,
            Radio86KeyboardDevice keyboard,
            Radio86DmaDevice dma,
            Radio86VideoDevice video,
            AccessTraceListener traceListener
    ) {
        super(clock);
        this.memory = Objects.requireNonNull(memory, "memory");
        this.keyboard = Objects.requireNonNull(keyboard, "keyboard");
        this.dma = Objects.requireNonNull(dma, "dma");
        this.video = Objects.requireNonNull(video, "video");
        this.memoryMappedIo = buildMemoryMappedIo();
        if (traceListener != null) {
            memoryMappedIo.setTraceSink((mappingName, read, access, value) -> {
                if (!mappingName.startsWith("radio86.")) {
                    return;
                }
                if (read) {
                    traceListener.onRead(access.address(), value, access.tState());
                } else {
                    traceListener.onWrite(access.address(), value, access.tState());
                }
            });
        }
    }

    @Override
    public int readMemory(int address) {
        int normalized = address & 0xFFFF;
        if (normalized < Radio86Memory.RAM_SIZE) {
            return memory.readLowMemory(normalized);
        }
        if (normalized >= Radio86Memory.ROM_START) {
            return memory.readTopRom(normalized);
        }
        return memoryMappedIo.read(normalized, clockValue(), 0);
    }

    @Override
    public void writeMemory(int address, int value) {
        int normalized = address & 0xFFFF;
        if (normalized < Radio86Memory.RAM_SIZE) {
            memory.writeLowMemory(normalized, value);
            return;
        }
        memoryMappedIo.write(normalized, value, clockValue(), 0);
    }

    @Override
    public int readPort(int port) {
        return readMemory(portToMemoryAddress(port));
    }

    @Override
    public void writePort(int port, int value) {
        writeMemory(portToMemoryAddress(port), value);
    }

    private int portToMemoryAddress(int port) {
        int offset = port & 0xFF;
        return (offset << 8) | offset;
    }

    private IoAddressSpace buildMemoryMappedIo() {
        IoAddressSpace ioMap = IoAddressSpace.withUnmappedValue(0xFF);
        ioMap.mapReadWrite(
                "radio86.keyboard",
                IoSelector.mask(0xFFFC, KEYBOARD_BASE, 0x0003, 0),
                access -> keyboard.readRegister(access.offset()),
                (access, value) -> keyboard.writeRegister(access.offset(), value)
        );
        ioMap.mapReadWrite(
                "radio86.video",
                IoSelector.mask(0xFFFE, VIDEO_BASE, 0x0001, 0),
                access -> video.readRegister(access.offset()),
                (access, value) -> video.writeRegister(access.offset(), value)
        );
        ioMap.mapReadWrite(
                "radio86.dma",
                IoSelector.mask(0xE000, DMA_BASE, 0x000F, 0),
                access -> dma.readRegister(access.offset()),
                (access, value) -> dma.writeRegister(access.offset(), value)
        );
        return ioMap;
    }

    public interface AccessTraceListener {
        void onRead(int address, int value, long tState);

        void onWrite(int address, int value, long tState);
    }
}
