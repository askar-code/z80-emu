package dev.z8emu.app.desktop;

import dev.z8emu.machine.radio86rk.Radio86Machine;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.machine.radio86rk.tape.Radio86TapeLoaders;
import dev.z8emu.machine.cpc.CpcMachine;
import dev.z8emu.machine.cpc.disk.CpcDskLoader;
import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.machine.spectrum48k.tape.TapeLoaders;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

public final class DesktopLauncher {
    private DesktopLauncher() {
    }

    public static void main(String[] args) throws IOException {
        DesktopLaunchConfig config = createLaunchConfig(args);
        switch (config.machineKind()) {
            case SPECTRUM48, SPECTRUM128 -> {
                SpectrumMachine machine = createSpectrumMachine(config);
                if (config.demoMode()) {
                    seedSpectrumDemoScreen(machine);
                }
                SwingUtilities.invokeLater(() -> SpectrumDesktopRunner.open(machine, config));
            }
            case RADIO86RK -> {
                Radio86Machine machine = createRadioMachine(config);
                SwingUtilities.invokeLater(() -> Radio86DesktopRunner.open(machine, config));
            }
            case CPC6128 -> {
                CpcMachine machine = createCpcMachine(config);
                SwingUtilities.invokeLater(() -> CpcDesktopRunner.open(machine, config));
            }
        }
    }

    private static DesktopLaunchConfig createLaunchConfig(String[] args) throws IOException {
        if (args.length == 0) {
            return new DesktopLaunchConfig(
                    "demo",
                    new byte[Spectrum48kMemoryMap.ROM_SIZE],
                    true,
                    null,
                    null,
                    null,
                    DesktopMachineKind.SPECTRUM48
            );
        }

        DesktopMachineKind machineKind = null;
        List<String> positionalArgs = new ArrayList<>(2);
        for (String arg : args) {
            if (arg.startsWith("--machine=")) {
                machineKind = DesktopMachineKind.parse(arg.substring("--machine=".length()));
            } else {
                positionalArgs.add(arg);
            }
        }

        if (machineKind == null || positionalArgs.isEmpty()) {
            throw new IllegalArgumentException("Usage: DesktopLauncher --machine=48|128|radio86rk|cpc6128 <rom> [tape]");
        }

        Path romPath = Path.of(positionalArgs.get(0)).toAbsolutePath().normalize();
        byte[] romImage = Files.readAllBytes(romPath);
        validateRomSize(machineKind, romImage, romPath);
        validateArgumentCount(machineKind, positionalArgs.size());

        DesktopLaunchConfig.LoadedSpectrumTape loadedTape =
                !machineKind.isSpectrum() || positionalArgs.size() == 1
                        ? null
                        : loadSpectrumTape(positionalArgs.get(1));
        DesktopLaunchConfig.LoadedRadioTape loadedRadioTape =
                machineKind == DesktopMachineKind.RADIO86RK && positionalArgs.size() == 2
                        ? loadRadioTape(positionalArgs.get(1))
                        : null;
        DesktopLaunchConfig.LoadedCpcDisk loadedCpcDisk =
                machineKind == DesktopMachineKind.CPC6128 && positionalArgs.size() == 2
                        ? loadCpcDisk(positionalArgs.get(1))
                        : null;

        return new DesktopLaunchConfig(
                romPath.toString(),
                romImage,
                false,
                loadedTape,
                loadedRadioTape,
                loadedCpcDisk,
                machineKind
        );
    }

    private static void validateRomSize(DesktopMachineKind machineKind, byte[] romImage, Path romPath) {
        if (machineKind == DesktopMachineKind.SPECTRUM48 && romImage.length != Spectrum48kMemoryMap.ROM_SIZE) {
            throw new IllegalArgumentException("Spectrum 48K ROM must be exactly 16 KB: " + romPath);
        }
        if (machineKind == DesktopMachineKind.SPECTRUM128 && romImage.length != Spectrum128Machine.ROM_IMAGE_SIZE) {
            throw new IllegalArgumentException("Spectrum 128 ROM must be exactly 32 KB: " + romPath);
        }
        if (machineKind == DesktopMachineKind.RADIO86RK
                && romImage.length != Radio86Memory.ROM_SIZE_2K
                && romImage.length != Radio86Memory.ROM_SIZE_4K) {
            throw new IllegalArgumentException("Radio-86RK ROM must be exactly 2 KB or 4 KB: " + romPath);
        }
        if (machineKind == DesktopMachineKind.CPC6128 && !CpcMachine.isSupportedCombinedRomSize(romImage.length)) {
            throw new IllegalArgumentException("CPC 6128 ROM bundle must be exactly 16 KB, 32 KB, or 48 KB: " + romPath);
        }
    }

    private static void validateArgumentCount(DesktopMachineKind machineKind, int positionalCount) {
        if (machineKind == DesktopMachineKind.RADIO86RK && positionalCount > 2) {
            throw new IllegalArgumentException("Usage: DesktopLauncher --machine=radio86rk <rom> [tape.rk|tape.rkr]");
        }
        if (machineKind.isSpectrum() && positionalCount > 2) {
            throw new IllegalArgumentException("Usage: DesktopLauncher --machine=48|128 <rom> [tape.tap|tape.tzx]");
        }
        if (machineKind == DesktopMachineKind.CPC6128 && positionalCount > 2) {
            throw new IllegalArgumentException("Usage: DesktopLauncher --machine=cpc6128 <rom-bundle> [disk.dsk]");
        }
    }

    private static DesktopLaunchConfig.LoadedSpectrumTape loadSpectrumTape(String rawPath) throws IOException {
        Path tapePath = Path.of(rawPath).toAbsolutePath().normalize();
        return new DesktopLaunchConfig.LoadedSpectrumTape(
                tapePath.toString(),
                TapeLoaders.load(tapePath)
        );
    }

    private static DesktopLaunchConfig.LoadedRadioTape loadRadioTape(String rawPath) throws IOException {
        Path tapePath = Path.of(rawPath).toAbsolutePath().normalize();
        return new DesktopLaunchConfig.LoadedRadioTape(
                tapePath.toString(),
                Radio86TapeLoaders.load(tapePath)
        );
    }

    private static DesktopLaunchConfig.LoadedCpcDisk loadCpcDisk(String rawPath) throws IOException {
        Path diskPath = Path.of(rawPath).toAbsolutePath().normalize();
        return new DesktopLaunchConfig.LoadedCpcDisk(
                diskPath.toString(),
                CpcDskLoader.load(diskPath)
        );
    }

    private static SpectrumMachine createSpectrumMachine(DesktopLaunchConfig config) {
        if (config.demoMode()) {
            return new Spectrum48kMachine(config.romImage());
        }
        return switch (config.machineKind()) {
            case SPECTRUM48 -> new Spectrum48kMachine(config.romImage());
            case SPECTRUM128 -> new Spectrum128Machine(config.romImage());
            case RADIO86RK -> throw new IllegalArgumentException("Radio-86RK uses createRadioMachine()");
            case CPC6128 -> throw new IllegalArgumentException("CPC 6128 uses createCpcMachine()");
        };
    }

    private static Radio86Machine createRadioMachine(DesktopLaunchConfig config) {
        if (config.demoMode()) {
            throw new IllegalArgumentException("Demo mode is only available for Spectrum");
        }
        if (config.machineKind() != DesktopMachineKind.RADIO86RK) {
            throw new IllegalArgumentException("Expected Radio-86RK launch config");
        }
        return new Radio86Machine(config.romImage());
    }

    private static CpcMachine createCpcMachine(DesktopLaunchConfig config) {
        if (config.demoMode()) {
            throw new IllegalArgumentException("Demo mode is only available for Spectrum");
        }
        if (config.machineKind() != DesktopMachineKind.CPC6128) {
            throw new IllegalArgumentException("Expected CPC 6128 launch config");
        }
        return new CpcMachine(
                config.romImage(),
                config.loadedCpcDisk() == null ? null : config.loadedCpcDisk().diskImage()
        );
    }

    private static void seedSpectrumDemoScreen(SpectrumMachine machine) {
        machine.board().ula().writePortFe(0x03, machine.currentTState(), machine.board().beeper());

        for (int y = 0; y < dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice.DISPLAY_HEIGHT; y++) {
            int rowBase = 0x4000
                    | ((y & 0xC0) << 5)
                    | ((y & 0x07) << 8)
                    | ((y & 0x38) << 2);

            for (int xByte = 0; xByte < 32; xByte++) {
                int pattern = ((xByte + (y >>> 3)) & 1) == 0 ? 0xAA : 0x55;
                machine.board().memory().write(rowBase + xByte, pattern);
            }
        }

        for (int row = 0; row < 24; row++) {
            for (int column = 0; column < 32; column++) {
                int ink = column & 0x07;
                int paper = row & 0x07;
                int bright = ((column / 8) & 0x01) << 6;
                int attribute = bright | (paper << 3) | ink;
                machine.board().memory().write(0x5800 + (row * 32) + column, attribute);
            }
        }
    }
}
