package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.device.C64CiaDevice;
import dev.z8emu.machine.c64.device.C64SidDevice;
import dev.z8emu.machine.c64.device.C64VideoDevice;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.bus.io.IoAddressSpace;
import dev.z8emu.platform.bus.io.IoSelector;
import dev.z8emu.platform.bus.io.IoTraceSink;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class C64Bus extends ClockedCpuBus {
    private final C64Memory memory;
    private final C64CpuPort cpuPort;
    private final C64VideoDevice video;
    private final C64SidDevice sid;
    private final C64CiaDevice cia1;
    private final C64CiaDevice cia2;
    private final IoAddressSpace memoryMappedIo;

    public C64Bus(
            TStateCounter clock,
            C64Memory memory,
            C64CpuPort cpuPort,
            C64VideoDevice video,
            C64SidDevice sid,
            C64CiaDevice cia1,
            C64CiaDevice cia2
    ) {
        super(clock);
        this.memory = Objects.requireNonNull(memory, "memory");
        this.cpuPort = Objects.requireNonNull(cpuPort, "cpuPort");
        this.video = Objects.requireNonNull(video, "video");
        this.sid = Objects.requireNonNull(sid, "sid");
        this.cia1 = Objects.requireNonNull(cia1, "cia1");
        this.cia2 = Objects.requireNonNull(cia2, "cia2");
        this.memoryMappedIo = buildMemoryMappedIo();
    }

    @Override
    public int readMemory(int address) {
        int normalized = address & 0xFFFF;
        if (normalized == 0x0000) {
            return cpuPort.readDirection();
        }
        if (normalized == 0x0001) {
            return cpuPort.readData();
        }
        if (normalized >= C64Memory.BASIC_ROM_START
                && normalized < C64Memory.BASIC_ROM_START + C64Memory.BASIC_ROM_SIZE) {
            if (cpuPort.loram() && cpuPort.hiram()) {
                return memory.readBasicRom(normalized - C64Memory.BASIC_ROM_START);
            }
            return memory.readRam(normalized);
        }
        if (normalized >= C64Memory.IO_START && normalized < C64Memory.KERNAL_ROM_START) {
            if (!cpuPort.hiram() && !cpuPort.loram()) {
                return memory.readRam(normalized);
            }
            if (cpuPort.charen()) {
                return memoryMappedIo.read(normalized, clockValue(), 0);
            }
            return memory.readCharRom(normalized - C64Memory.IO_START);
        }
        if (normalized >= C64Memory.KERNAL_ROM_START) {
            if (cpuPort.hiram()) {
                return memory.readKernalRom(normalized - C64Memory.KERNAL_ROM_START);
            }
            return memory.readRam(normalized);
        }
        return memory.readRam(normalized);
    }

    @Override
    public void writeMemory(int address, int value) {
        int normalized = address & 0xFFFF;
        if (normalized == 0x0000) {
            cpuPort.writeDirection(value);
            return;
        }
        if (normalized == 0x0001) {
            cpuPort.writeData(value);
            return;
        }
        if (normalized >= C64Memory.IO_START
                && normalized < C64Memory.KERNAL_ROM_START
                && (cpuPort.hiram() || cpuPort.loram())
                && cpuPort.charen()) {
            memoryMappedIo.write(normalized, value, clockValue(), 0);
            return;
        }
        memory.writeRam(normalized, value);
    }

    public void setIoTraceSink(IoTraceSink traceSink) {
        memoryMappedIo.setTraceSink(traceSink);
    }

    private IoAddressSpace buildMemoryMappedIo() {
        IoAddressSpace ioMap = IoAddressSpace.withUnmappedValue(0xFF);
        ioMap.mapReadWrite(
                "c64.vic",
                IoSelector.mirroredRange(0xD000, 0xD03F, 0x03C0),
                access -> video.readRegister(access.offset()),
                (access, value) -> video.writeRegister(access.offset(), value)
        );
        ioMap.mapReadWrite(
                "c64.sid",
                IoSelector.mirroredRange(0xD400, 0xD41F, 0x03E0),
                access -> sid.readRegister(access.offset()),
                (access, value) -> sid.writeRegister(access.offset(), value)
        );
        ioMap.mapReadWrite(
                "c64.color-ram",
                IoSelector.range(0xD800, 0xDBFF),
                access -> memory.readColorRam(access.offset()),
                (access, value) -> memory.writeColorRam(access.offset(), value)
        );
        ioMap.mapReadWrite(
                "c64.cia1",
                IoSelector.mirroredRange(0xDC00, 0xDC0F, 0x00F0),
                access -> cia1.readRegister(access.offset()),
                (access, value) -> cia1.writeRegister(access.offset(), value)
        );
        ioMap.mapReadWrite(
                "c64.cia2",
                IoSelector.mirroredRange(0xDD00, 0xDD0F, 0x00F0),
                access -> cia2.readRegister(access.offset()),
                (access, value) -> cia2.writeRegister(access.offset(), value)
        );
        return ioMap;
    }
}
