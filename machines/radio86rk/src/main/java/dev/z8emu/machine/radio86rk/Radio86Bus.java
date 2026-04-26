package dev.z8emu.machine.radio86rk;

import dev.z8emu.machine.radio86rk.device.Radio86DmaDevice;
import dev.z8emu.machine.radio86rk.device.Radio86KeyboardDevice;
import dev.z8emu.machine.radio86rk.device.Radio86VideoDevice;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.bus.io.IoAddressSpace;
import dev.z8emu.platform.bus.io.IoSelector;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

public final class Radio86Bus extends ClockedCpuBus {
    private static final int KEYBOARD_BASE = 0x8000;
    private static final int VIDEO_BASE = 0xC000;
    private static final int DMA_BASE = 0xE000;

    private final Radio86Memory memory;
    private final Radio86KeyboardDevice keyboard;
    private final Radio86DmaDevice dma;
    private final Radio86VideoDevice video;
    private final AccessTraceListener traceListener;
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
        this.traceListener = traceListener;
        this.memoryMappedIo = buildMemoryMappedIo();
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
                access -> readRegister(access, keyboard::readRegister),
                (access, value) -> writeRegister(access, value, keyboard::writeRegister)
        );
        ioMap.mapReadWrite(
                "radio86.video",
                IoSelector.mask(0xFFFE, VIDEO_BASE, 0x0001, 0),
                access -> readRegister(access, video::readRegister),
                (access, value) -> writeRegister(access, value, video::writeRegister)
        );
        ioMap.mapReadWrite(
                "radio86.dma",
                IoSelector.mask(0xE000, DMA_BASE, 0x000F, 0),
                access -> readRegister(access, dma::readRegister),
                (access, value) -> writeRegister(access, value, dma::writeRegister)
        );
        return ioMap;
    }

    private int readRegister(IoAccess access, IntUnaryOperator reader) {
        int value = reader.applyAsInt(access.offset());
        traceRead(access.address(), value);
        return value;
    }

    private void writeRegister(IoAccess access, int value, RegisterWriter writer) {
        writer.write(access.offset(), value);
        traceWrite(access.address(), value);
    }

    private void traceRead(int address, int value) {
        if (traceListener != null) {
            traceListener.onRead(address & 0xFFFF, value & 0xFF, clockValue());
        }
    }

    private void traceWrite(int address, int value) {
        if (traceListener != null) {
            traceListener.onWrite(address & 0xFFFF, value & 0xFF, clockValue());
        }
    }

    public interface AccessTraceListener {
        void onRead(int address, int value, long tState);

        void onWrite(int address, int value, long tState);
    }

    @FunctionalInterface
    private interface RegisterWriter {
        void write(int register, int value);
    }
}
