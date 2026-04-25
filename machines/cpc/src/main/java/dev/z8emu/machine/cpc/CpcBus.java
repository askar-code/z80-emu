package dev.z8emu.machine.cpc;

import dev.z8emu.machine.cpc.device.CpcCrtcDevice;
import dev.z8emu.machine.cpc.device.CpcFdcDevice;
import dev.z8emu.machine.cpc.device.CpcGateArrayDevice;
import dev.z8emu.machine.cpc.device.CpcPpiDevice;
import dev.z8emu.machine.cpc.memory.CpcMemory;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class CpcBus implements CpuBus {
    private static final int GATE_ARRAY_PORT_MASK = 0xC000;
    private static final int GATE_ARRAY_PORT_VALUE = 0x4000;
    private static final int CRTC_PORT_MASK = 0x4300;
    private static final int CRTC_REGISTER_SELECT_PORT_VALUE = 0x0000;
    private static final int CRTC_DATA_WRITE_PORT_VALUE = 0x0100;
    private static final int CRTC_STATUS_READ_PORT_VALUE = 0x0200;
    private static final int CRTC_DATA_READ_PORT_VALUE = 0x0300;
    private static final int ROM_SELECT_PORT_MASK = 0xFF00;
    private static final int ROM_SELECT_PORT_VALUE = 0xDF00;
    private static final int PPI_PORT_MASK = 0x0800;
    private static final int PPI_PORT_VALUE = 0x0000;
    private static final int FDC_PORT_MASK = 0x0480;
    private static final int FDC_PORT_VALUE = 0x0000;

    private final TStateCounter clock;
    private final CpcMemory memory;
    private final CpcGateArrayDevice gateArray;
    private final CpcCrtcDevice crtc;
    private final CpcPpiDevice ppi;
    private final CpcFdcDevice fdc;

    public CpcBus(
            TStateCounter clock,
            CpcMemory memory,
            CpcGateArrayDevice gateArray,
            CpcCrtcDevice crtc,
            CpcPpiDevice ppi,
            CpcFdcDevice fdc
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.gateArray = Objects.requireNonNull(gateArray, "gateArray");
        this.crtc = Objects.requireNonNull(crtc, "crtc");
        this.ppi = Objects.requireNonNull(ppi, "ppi");
        this.fdc = Objects.requireNonNull(fdc, "fdc");
    }

    @Override
    public int fetchOpcode(int address) {
        return readMemory(address);
    }

    @Override
    public int readMemory(int address) {
        return memory.read(address);
    }

    @Override
    public void writeMemory(int address, int value) {
        memory.write(address, value);
    }

    @Override
    public int readPort(int port) {
        int normalized = port & 0xFFFF;
        if (isFdcPort(normalized)) {
            return readFdcPort(normalized);
        }
        if ((normalized & PPI_PORT_MASK) == PPI_PORT_VALUE) {
            return ppi.readRegister(ppiRegister(normalized));
        }
        int crtcPort = port & CRTC_PORT_MASK;
        if (crtcPort == CRTC_STATUS_READ_PORT_VALUE) {
            return 0xFF;
        }
        if (crtcPort == CRTC_DATA_READ_PORT_VALUE) {
            return crtc.readSelectedRegister();
        }
        return 0xFF;
    }

    @Override
    public void writePort(int port, int value) {
        writePort(port, value, 0);
    }

    @Override
    public void writePort(int port, int value, int phaseTStates) {
        int normalized = port & 0xFFFF;
        if (isFdcPort(normalized)) {
            writeFdcPort(normalized, value);
            return;
        }
        if ((normalized & PPI_PORT_MASK) == PPI_PORT_VALUE) {
            ppi.writeRegister(ppiRegister(normalized), value);
            return;
        }
        if ((normalized & ROM_SELECT_PORT_MASK) == ROM_SELECT_PORT_VALUE) {
            memory.selectUpperRom(value);
            return;
        }
        int crtcPort = normalized & CRTC_PORT_MASK;
        if (crtcPort == CRTC_REGISTER_SELECT_PORT_VALUE) {
            crtc.selectRegister(value);
            return;
        }
        if (crtcPort == CRTC_DATA_WRITE_PORT_VALUE) {
            crtc.writeSelectedRegister(value);
            return;
        }
        if ((normalized & GATE_ARRAY_PORT_MASK) == GATE_ARRAY_PORT_VALUE) {
            gateArray.writeRegister(value, memory, clock.value() + phaseTStates);
        }
    }

    @Override
    public int acknowledgeInterrupt() {
        gateArray.acknowledgeInterrupt();
        return 0xFF;
    }

    @Override
    public void onRefresh(int irValue) {
    }

    @Override
    public int currentTState() {
        long tState = clock.value();
        return tState > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tState;
    }

    private int ppiRegister(int port) {
        return (port >>> 8) & 0x03;
    }

    private boolean isFdcPort(int port) {
        return (port & FDC_PORT_MASK) == FDC_PORT_VALUE;
    }

    private int readFdcPort(int port) {
        if ((port & 0x0100) == 0) {
            return 0xFF;
        }
        return (port & 0x0001) == 0
                ? fdc.readMainStatusRegister()
                : fdc.readDataRegister();
    }

    private void writeFdcPort(int port, int value) {
        if ((port & 0x0100) == 0) {
            if ((port & 0x0001) == 0) {
                fdc.writeMotorControl(value);
            }
            return;
        }
        if ((port & 0x0001) != 0) {
            fdc.writeDataRegister(value);
        }
    }
}
