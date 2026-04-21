package dev.z8emu.machine.spectrum;

import dev.z8emu.machine.spectrum.model.SpectrumMachineState;
import dev.z8emu.machine.spectrum.model.SpectrumModelConfig;
import dev.z8emu.machine.spectrum.model.SpectrumPagingController;
import dev.z8emu.machine.spectrum128k.Spectrum128Bus;
import dev.z8emu.machine.spectrum128k.device.Ay38912Device;
import dev.z8emu.machine.spectrum48k.Spectrum48kBus;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.time.TStateCounter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectrumBusIoTest {
    @Test
    void floatingBusReturnsScreenAndAttributeBytesOn48k() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum48k();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(config, state, new byte[Spectrum48kMemoryMap.ROM_SIZE]);
        TStateCounter clock = new TStateCounter();
        Spectrum48kBus bus = new Spectrum48kBus(
                clock,
                memory,
                new SpectrumPagingController(config, state, memory),
                new SpectrumUlaDevice(),
                new KeyboardMatrixDevice(),
                new BeeperDevice(),
                new TapeDevice(config.cpuClockHz(), true)
        );

        memory.write(0x4000, 0x12);
        memory.write(0x5800, 0x34);
        memory.write(0x4001, 0x56);
        memory.write(0x5801, 0x78);

        clock.advance(14_347);
        assertEquals(0x12, bus.readPort(0xFFFF));
        clock.advance(1);
        assertEquals(0x34, bus.readPort(0xFFFF));
        clock.advance(1);
        assertEquals(0x56, bus.readPort(0xFFFF));
        clock.advance(1);
        assertEquals(0x78, bus.readPort(0xFFFF));
        clock.advance(1);
        assertEquals(0xFF, bus.readPort(0xFFFF));
    }

    @Test
    void floatingBusAlsoAppliesToUnmappedPortsOn128k() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum128();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(
                config,
                state,
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE]
        );
        TStateCounter clock = new TStateCounter();
        Spectrum128Bus bus = new Spectrum128Bus(
                clock,
                memory,
                new SpectrumPagingController(config, state, memory),
                new SpectrumUlaDevice(config.frameTStates(), 311),
                new KeyboardMatrixDevice(),
                new BeeperDevice(),
                new TapeDevice(config.cpuClockHz(), false),
                new Ay38912Device(config.cpuClockHz())
        );

        memory.ramBank(5).write(0, 0x9A);
        memory.ramBank(5).write(0x1800, 0xBC);

        clock.advance(14_368);
        assertEquals(0x9A, bus.readPort(0x7FFD));
        clock.advance(1);
        assertEquals(0xBC, bus.readPort(0x7FFD));
    }

    @Test
    void pagingPortSelectsDistinctTopRamBanksForReadsAndWrites() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum128();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(
                config,
                state,
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE]
        );
        Spectrum128Bus bus = new Spectrum128Bus(
                new TStateCounter(),
                memory,
                new SpectrumPagingController(config, state, memory),
                new SpectrumUlaDevice(config.frameTStates(), 311),
                new KeyboardMatrixDevice(),
                new BeeperDevice(),
                new TapeDevice(config.cpuClockHz(), false),
                new Ay38912Device(config.cpuClockHz())
        );

        bus.writePort(0x7FFD, 0x03);
        bus.writeMemory(0xC000, 0x5A);

        bus.writePort(0x7FFD, 0x04);
        bus.writeMemory(0xC000, 0xA5);

        bus.writePort(0x7FFD, 0x03);
        assertEquals(0x5A, bus.readMemory(0xC000));

        bus.writePort(0x7FFD, 0x04);
        assertEquals(0xA5, bus.readMemory(0xC000));
    }

    @Test
    void portFeUpdatesBorderWithoutChanging128kPagingState() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum128();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(
                config,
                state,
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE]
        );
        SpectrumUlaDevice ula = new SpectrumUlaDevice(config.frameTStates(), 311);
        BeeperDevice beeper = new BeeperDevice();
        Spectrum128Bus bus = new Spectrum128Bus(
                new TStateCounter(),
                memory,
                new SpectrumPagingController(config, state, memory),
                ula,
                new KeyboardMatrixDevice(),
                beeper,
                new TapeDevice(config.cpuClockHz(), false),
                new Ay38912Device(config.cpuClockHz())
        );

        bus.writePort(0x7FFD, 0x1B);
        bus.writePort(0x00FE, 0x05);

        assertEquals(5, ula.borderColor());
        assertEquals(1, state.selectedRomIndex());
        assertEquals(3, state.topRamBankIndex());
        assertEquals(7, state.activeScreenBankIndex());
        assertEquals(0x1B, state.pagingPort7ffd());
    }

    @Test
    void ayPortsDoNotChangePagingStateOrMemoryMapping() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum128();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(
                config,
                state,
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE]
        );
        Spectrum128Bus bus = new Spectrum128Bus(
                new TStateCounter(),
                memory,
                new SpectrumPagingController(config, state, memory),
                new SpectrumUlaDevice(config.frameTStates(), 311),
                new KeyboardMatrixDevice(),
                new BeeperDevice(),
                new TapeDevice(config.cpuClockHz(), false),
                new Ay38912Device(config.cpuClockHz())
        );

        bus.writePort(0x7FFD, 0x1B);
        bus.writePort(0xFFFD, 0x08);
        bus.writePort(0xBFFD, 0x0F);

        assertEquals(1, state.selectedRomIndex());
        assertEquals(3, state.topRamBankIndex());
        assertEquals(7, state.activeScreenBankIndex());
        assertEquals(0x1B, state.pagingPort7ffd());
        assertEquals(0x0F, bus.readPort(0xFFFD));
    }

    @Test
    void phasedAyReadsReturnSelectedRegisterInsteadOfFloatingBus() {
        SpectrumModelConfig config = SpectrumModelConfig.spectrum128();
        SpectrumMachineState state = new SpectrumMachineState(config);
        Spectrum48kMemoryMap memory = new Spectrum48kMemoryMap(
                config,
                state,
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE]
        );
        Spectrum128Bus bus = new Spectrum128Bus(
                new TStateCounter(),
                memory,
                new SpectrumPagingController(config, state, memory),
                new SpectrumUlaDevice(config.frameTStates(), 311),
                new KeyboardMatrixDevice(),
                new BeeperDevice(),
                new TapeDevice(config.cpuClockHz(), false),
                new Ay38912Device(config.cpuClockHz())
        );

        bus.writePort(0xFFFD, 0x08);
        bus.writePort(0xBFFD, 0x1F);

        assertEquals(0x1F, bus.readPort(0xFFFD, 8));
    }
}
