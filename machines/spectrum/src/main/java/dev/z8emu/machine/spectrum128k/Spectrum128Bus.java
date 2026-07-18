package dev.z8emu.machine.spectrum128k;

import dev.z8emu.chip.ay.Ay38912Device;
import dev.z8emu.machine.spectrum.SpectrumBusBase;
import dev.z8emu.machine.spectrum.model.SpectrumModelConfig;
import dev.z8emu.machine.spectrum.model.SpectrumPagingController;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.KempstonJoystickDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.bus.io.IoSelector;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Spectrum128Bus extends SpectrumBusBase {
    private static final int AY_REGISTER_PORT_MASK = 0xC002;
    private static final int AY_REGISTER_PORT_VALUE = 0xC000;
    private static final int AY_DATA_PORT_MASK = 0xC002;
    private static final int AY_DATA_PORT_VALUE = 0x8000;
    private static final int ULA_AY_REGISTER_READ_OVERLAP_MASK = AY_REGISTER_PORT_MASK | 0x0001;
    private static final int KEMPSTON_AY_REGISTER_READ_OVERLAP_MASK = AY_REGISTER_PORT_MASK | 0x00E1;

    private final Spectrum48kMemoryMap memory;
    private final SpectrumPagingController pagingController;
    private final SpectrumUlaDevice ula;
    private final Ay38912Device ay;
    private final int ayAudioTimeline;

    public Spectrum128Bus(
            TStateCounter clock,
            Spectrum48kMemoryMap memory,
            SpectrumPagingController pagingController,
            SpectrumUlaDevice ula,
            KeyboardMatrixDevice keyboard,
            KempstonJoystickDevice kempstonJoystick,
            BeeperDevice beeper,
            TapeDevice tape,
            Ay38912Device ay,
            SpectrumModelConfig modelConfig
    ) {
        super(
                clock,
                memory,
                ula,
                keyboard,
                kempstonJoystick,
                beeper,
                tape,
                modelConfig.frameTStates(),
                modelConfig.contentionStartTState(),
                modelConfig.tStatesPerScanline()
        );
        this.memory = memory;
        this.pagingController = Objects.requireNonNull(pagingController, "pagingController");
        this.ula = ula;
        this.ay = Objects.requireNonNull(ay, "ay");
        this.ayAudioTimeline = registerAudioDevice(ay);
        ports().mapWrite(
                "spectrum128.paging-7ffd",
                pagingController.portSelector(),
                this::writePagingPort,
                100
        );
        ports().mapReadWrite(
                "spectrum128.ay-register",
                IoSelector.mask(AY_REGISTER_PORT_MASK, AY_REGISTER_PORT_VALUE),
                access -> ay.readSelectedRegister(),
                (access, value) -> {
                    syncAudioDevice(ayAudioTimeline, access.effectiveTState());
                    ay.selectRegister(value);
                },
                80
        );
        ports().mapRead(
                "spectrum128.ula-ay-register-overlap",
                IoSelector.mask(ULA_AY_REGISTER_READ_OVERLAP_MASK, AY_REGISTER_PORT_VALUE),
                access -> ula.readPortFe(access, keyboard, tape) & ay.readSelectedRegister(),
                110
        );
        ports().mapRead(
                "spectrum128.kempston-ay-register-overlap",
                kempstonJoystick.enabledPortSelector(
                        KEMPSTON_AY_REGISTER_READ_OVERLAP_MASK,
                        AY_REGISTER_PORT_VALUE | 0x0001
                ),
                access -> kempstonJoystick.readPort() & ay.readSelectedRegister(),
                120
        );
        ports().mapRead(
                "spectrum128.ula-kempston-ay-register-overlap",
                kempstonJoystick.enabledPortSelector(
                        KEMPSTON_AY_REGISTER_READ_OVERLAP_MASK,
                        AY_REGISTER_PORT_VALUE
                ),
                access -> ula.readPortFe(access, keyboard, tape)
                        & kempstonJoystick.readPort()
                        & ay.readSelectedRegister(),
                130
        );
        ports().mapWrite(
                "spectrum128.ay-data",
                IoSelector.mask(AY_DATA_PORT_MASK, AY_DATA_PORT_VALUE),
                (access, value) -> {
                    syncAudioDevice(ayAudioTimeline, access.effectiveTState());
                    ay.writeSelectedRegister(value);
                },
                70
        );
    }

    private void writePagingPort(IoAccess access, int value) {
        ula.syncToTState(access.effectiveTState(), memory);
        pagingController.handlePortWrite(access.address(), value);
    }
}
