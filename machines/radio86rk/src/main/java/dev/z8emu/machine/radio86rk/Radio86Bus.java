package dev.z8emu.machine.radio86rk;

import dev.z8emu.machine.radio86rk.device.Radio86KeyboardDevice;
import dev.z8emu.machine.radio86rk.device.Radio86DmaDevice;
import dev.z8emu.machine.radio86rk.device.Radio86VideoDevice;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Radio86Bus extends ClockedCpuBus {
    private static final int KEYBOARD_BASE = 0x8000;
    private static final int KEYBOARD_LIMIT = 0x9FFF;
    private static final int VIDEO_BASE = 0xC000;
    private static final int VIDEO_LIMIT = 0xDFFF;
    private static final int DMA_BASE = 0xE000;
    private static final int DMA_LIMIT = 0xFFFF;

    private final Radio86Memory memory;
    private final Radio86KeyboardDevice keyboard;
    private final Radio86DmaDevice dma;
    private final Radio86VideoDevice video;
    private final AccessTraceListener traceListener;

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
        if (isKeyboardRegister(normalized)) {
            int value = keyboard.readRegister(normalized & 0x03);
            traceRead(normalized, value);
            return value;
        }
        if (isVideoRegister(normalized)) {
            int value = video.readRegister(normalized & 0x01);
            traceRead(normalized, value);
            return value;
        }
        if (isDmaRegister(normalized)) {
            int value = dma.readRegister(normalized & 0x0F);
            traceRead(normalized, value);
            return value;
        }
        return 0xFF;
    }

    @Override
    public void writeMemory(int address, int value) {
        int normalized = address & 0xFFFF;
        if (normalized < Radio86Memory.RAM_SIZE) {
            memory.writeLowMemory(normalized, value);
            return;
        }
        if (isKeyboardRegister(normalized)) {
            keyboard.writeRegister(normalized & 0x03, value);
            traceWrite(normalized, value);
            return;
        }
        if (isVideoRegister(normalized)) {
            video.writeRegister(normalized & 0x01, value);
            traceWrite(normalized, value);
            return;
        }
        if (isDmaRegister(normalized)) {
            dma.writeRegister(normalized & 0x0F, value);
            traceWrite(normalized, value);
        }
    }

    @Override
    public int readPort(int port) {
        return readMemory(portToMemoryAddress(port));
    }

    @Override
    public void writePort(int port, int value) {
        writeMemory(portToMemoryAddress(port), value);
    }

    private boolean isKeyboardRegister(int address) {
        return address >= KEYBOARD_BASE
                && address <= KEYBOARD_LIMIT
                && (address & 0x1FFC) == 0;
    }

    private boolean isVideoRegister(int address) {
        return address >= VIDEO_BASE
                && address <= VIDEO_LIMIT
                && (address & 0x1FFE) == 0;
    }

    private boolean isDmaRegister(int address) {
        return address >= DMA_BASE && address <= DMA_LIMIT;
    }

    private int portToMemoryAddress(int port) {
        int offset = port & 0xFF;
        return (offset << 8) | offset;
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
}
