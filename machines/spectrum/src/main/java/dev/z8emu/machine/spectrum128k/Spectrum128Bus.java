package dev.z8emu.machine.spectrum128k;

import dev.z8emu.chip.ay.Ay38912Device;
import dev.z8emu.machine.spectrum.model.SpectrumContentionModel;
import dev.z8emu.machine.spectrum.model.SpectrumPagingController;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.bus.io.IoAddressSpace;
import dev.z8emu.platform.bus.io.IoSelector;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Spectrum128Bus extends ClockedCpuBus {
    private static final int CONTENTION_START_128K = 14_361;
    private static final int T_STATES_PER_SCANLINE_128K = 228;
    private static final int AY_REGISTER_PORT_MASK = 0xC002;
    private static final int AY_REGISTER_PORT_VALUE = 0xC000;
    private static final int AY_DATA_PORT_MASK = 0xC002;
    private static final int AY_DATA_PORT_VALUE = 0x8000;

    private final Spectrum48kMemoryMap memory;
    private final SpectrumPagingController pagingController;
    private final SpectrumUlaDevice ula;
    private final KeyboardMatrixDevice keyboard;
    private final BeeperDevice beeper;
    private final TapeDevice tape;
    private final Ay38912Device ay;
    private final SpectrumContentionModel contentionModel;
    private final IoAddressSpace ports;

    public Spectrum128Bus(
            TStateCounter clock,
            Spectrum48kMemoryMap memory,
            SpectrumPagingController pagingController,
            SpectrumUlaDevice ula,
            KeyboardMatrixDevice keyboard,
            BeeperDevice beeper,
            TapeDevice tape,
            Ay38912Device ay
    ) {
        super(clock);
        this.memory = Objects.requireNonNull(memory, "memory");
        this.pagingController = Objects.requireNonNull(pagingController, "pagingController");
        this.ula = Objects.requireNonNull(ula, "ula");
        this.keyboard = Objects.requireNonNull(keyboard, "keyboard");
        this.beeper = Objects.requireNonNull(beeper, "beeper");
        this.tape = Objects.requireNonNull(tape, "tape");
        this.ay = Objects.requireNonNull(ay, "ay");
        this.contentionModel = new SpectrumContentionModel(
                70_908,
                CONTENTION_START_128K,
                T_STATES_PER_SCANLINE_128K
        );
        this.ports = buildPortMap();
    }

    @Override
    public int fetchOpcodeWaitStates(int address, int phaseTStates) {
        return contentionModel.memoryDelay(clockValue(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public int readMemory(int address) {
        return memory.read(address);
    }

    @Override
    public int readMemoryWaitStates(int address, int phaseTStates) {
        return contentionModel.memoryDelay(clockValue(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public void writeMemory(int address, int value) {
        memory.write(address, value);
    }

    @Override
    public int writeMemoryWaitStates(int address, int value, int phaseTStates) {
        return contentionModel.memoryDelay(clockValue(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public int readPort(int port) {
        return ports.read(port, clockValue(), 0);
    }

    @Override
    public int readPort(int port, int phaseTStates) {
        return ports.read(port, clockValue() + readPortWaitStates(port, phaseTStates), phaseTStates);
    }

    @Override
    public void writePort(int port, int value) {
        writePort(port, value, 0);
    }

    @Override
    public void writePort(int port, int value, int phaseTStates) {
        ports.write(port, value, clockValue() + writePortWaitStates(port, value, phaseTStates), phaseTStates);
    }

    @Override
    public int readPortWaitStates(int port, int phaseTStates) {
        return contentionModel.ioPortDelay(clockValue(), phaseTStates, port);
    }

    @Override
    public int writePortWaitStates(int port, int value, int phaseTStates) {
        return contentionModel.ioPortDelay(clockValue(), phaseTStates, port);
    }

    @Override
    public void onRefresh(int irValue) {
        ula.onRefreshAddress(irValue);
    }

    private IoAddressSpace buildPortMap() {
        IoAddressSpace portMap = new IoAddressSpace(
                access -> ula.readFloatingBus(memory, access.effectiveTState())
        );
        portMap.mapWrite(
                "spectrum128.paging-7ffd",
                pagingController.portSelector(),
                this::writePagingPort,
                100
        );
        portMap.mapReadWrite(
                "spectrum128.ula-fe",
                SpectrumUlaDevice.portSelector(),
                access -> ula.readPortFe(access, keyboard, tape),
                (access, value) -> ula.writePortFe(access, value, beeper, memory),
                90
        );
        portMap.mapReadWrite(
                "spectrum128.ay-register",
                IoSelector.mask(AY_REGISTER_PORT_MASK, AY_REGISTER_PORT_VALUE),
                access -> ay.readSelectedRegister(),
                (access, value) -> ay.selectRegister(value),
                80
        );
        portMap.mapWrite(
                "spectrum128.ay-data",
                IoSelector.mask(AY_DATA_PORT_MASK, AY_DATA_PORT_VALUE),
                (access, value) -> ay.writeSelectedRegister(value),
                70
        );
        return portMap;
    }

    private void writePagingPort(IoAccess access, int value) {
        ula.syncToTState(access.effectiveTState(), memory);
        pagingController.handlePortWrite(access.address(), value);
    }
}
