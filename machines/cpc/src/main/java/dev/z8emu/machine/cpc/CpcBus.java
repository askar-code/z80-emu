package dev.z8emu.machine.cpc;

import dev.z8emu.machine.cpc.device.CpcCrtcDevice;
import dev.z8emu.machine.cpc.device.CpcFdcDevice;
import dev.z8emu.machine.cpc.device.CpcGateArrayDevice;
import dev.z8emu.machine.cpc.device.CpcPpiDevice;
import dev.z8emu.machine.cpc.memory.CpcMemory;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.bus.io.IoAddressSpace;
import dev.z8emu.platform.bus.io.IoSelector;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class CpcBus extends ClockedCpuBus {
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
    private static final int FDC_SUBPORT_MASK = FDC_PORT_MASK | 0x0100 | 0x0001;
    private static final int FDC_MOTOR_PORT_VALUE = FDC_PORT_VALUE;
    private static final int FDC_STATUS_PORT_VALUE = FDC_PORT_VALUE | 0x0100;
    private static final int FDC_DATA_PORT_VALUE = FDC_PORT_VALUE | 0x0100 | 0x0001;

    private final CpcMemory memory;
    private final CpcGateArrayDevice gateArray;
    private final CpcCrtcDevice crtc;
    private final CpcPpiDevice ppi;
    private final CpcFdcDevice fdc;
    private final IoAddressSpace ports;

    public CpcBus(
            TStateCounter clock,
            CpcMemory memory,
            CpcGateArrayDevice gateArray,
            CpcCrtcDevice crtc,
            CpcPpiDevice ppi,
            CpcFdcDevice fdc
    ) {
        super(clock);
        this.memory = Objects.requireNonNull(memory, "memory");
        this.gateArray = Objects.requireNonNull(gateArray, "gateArray");
        this.crtc = Objects.requireNonNull(crtc, "crtc");
        this.ppi = Objects.requireNonNull(ppi, "ppi");
        this.fdc = Objects.requireNonNull(fdc, "fdc");
        this.ports = buildPortMap();
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
        return ports.read(port, clockValue(), 0);
    }

    @Override
    public void writePort(int port, int value) {
        writePort(port, value, 0);
    }

    @Override
    public void writePort(int port, int value, int phaseTStates) {
        ports.write(port, value, clockValue(), phaseTStates);
    }

    @Override
    public int acknowledgeInterrupt() {
        gateArray.acknowledgeInterrupt();
        return 0xFF;
    }

    private IoAddressSpace buildPortMap() {
        IoAddressSpace portMap = IoAddressSpace.withUnmappedValue(0xFF);
        portMap.mapWrite("cpc.fdc-motor", IoSelector.mask(FDC_SUBPORT_MASK, FDC_MOTOR_PORT_VALUE),
                (access, value) -> fdc.writeMotorControl(value),
                100
        );
        portMap.mapRead("cpc.fdc-status", IoSelector.mask(FDC_SUBPORT_MASK, FDC_STATUS_PORT_VALUE),
                access -> fdc.readMainStatusRegister(),
                100
        );
        portMap.mapReadWrite("cpc.fdc-data", IoSelector.mask(FDC_SUBPORT_MASK, FDC_DATA_PORT_VALUE),
                access -> fdc.readDataRegister(),
                (access, value) -> fdc.writeDataRegister(value),
                100
        );
        portMap.mapReadWrite("cpc.ppi", IoSelector.mask(PPI_PORT_MASK, PPI_PORT_VALUE, 0x0300, 8),
                access -> ppi.readRegister(access.offset()),
                (access, value) -> ppi.writeRegister(access.offset(), value),
                90
        );
        portMap.mapWrite("cpc.upper-rom-select", IoSelector.mask(ROM_SELECT_PORT_MASK, ROM_SELECT_PORT_VALUE),
                (access, value) -> memory.selectUpperRom(value),
                80
        );
        portMap.mapRead("cpc.crtc-status", IoSelector.mask(CRTC_PORT_MASK, CRTC_STATUS_READ_PORT_VALUE),
                access -> 0xFF,
                70
        );
        portMap.mapRead("cpc.crtc-data-read", IoSelector.mask(CRTC_PORT_MASK, CRTC_DATA_READ_PORT_VALUE),
                access -> crtc.readSelectedRegister(),
                70
        );
        portMap.mapWrite("cpc.crtc-register-select", IoSelector.mask(CRTC_PORT_MASK, CRTC_REGISTER_SELECT_PORT_VALUE),
                (access, value) -> crtc.selectRegister(value),
                70
        );
        portMap.mapWrite("cpc.crtc-data-write", IoSelector.mask(CRTC_PORT_MASK, CRTC_DATA_WRITE_PORT_VALUE),
                (access, value) -> crtc.writeSelectedRegister(value),
                70
        );
        portMap.mapWrite("cpc.gate-array", IoSelector.mask(GATE_ARRAY_PORT_MASK, GATE_ARRAY_PORT_VALUE),
                (access, value) -> gateArray.writeRegister(value, memory, access.effectiveTState()),
                60
        );
        return portMap;
    }
}
