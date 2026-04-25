package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.Apple2Machine;
import dev.z8emu.machine.apple2.Apple2Memory;
import dev.z8emu.machine.cpc.CpcMachine;
import dev.z8emu.machine.cpc.disk.CpcDskImage;
import dev.z8emu.machine.cpc.disk.CpcDskLoader;
import dev.z8emu.machine.radio86rk.Radio86Machine;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.machine.radio86rk.tape.Radio86TapeLoaders;
import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.machine.spectrum48k.tape.TapeLoaders;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.SwingUtilities;

final class DesktopMachineDefinitions {
    private static final DesktopMachineDefinition SPECTRUM48 = new SpectrumDefinition(DesktopMachineKind.SPECTRUM48);
    private static final DesktopMachineDefinition SPECTRUM128 = new SpectrumDefinition(DesktopMachineKind.SPECTRUM128);
    private static final DesktopMachineDefinition RADIO86RK = new Radio86Definition();
    private static final DesktopMachineDefinition CPC6128 = new CpcDefinition();
    private static final DesktopMachineDefinition APPLE2 = new Apple2Definition();

    private static final List<DesktopMachineDefinition> DEFINITIONS = List.of(
            SPECTRUM48,
            SPECTRUM128,
            RADIO86RK,
            CPC6128,
            APPLE2
    );
    private static final Map<DesktopMachineKind, DesktopMachineDefinition> BY_KIND = new HashMap<>();
    private static final Map<String, DesktopMachineDefinition> BY_ALIAS = new HashMap<>();

    static {
        for (DesktopMachineDefinition definition : DEFINITIONS) {
            BY_KIND.put(definition.kind(), definition);
        }

        register(SPECTRUM48, "48", "48k", "spectrum48", "spectrum48k");
        register(SPECTRUM128, "128", "128k", "spectrum128", "spectrum128k");
        register(RADIO86RK, "radio86", "radio86rk", "rk86", "86rk");
        register(CPC6128, "cpc", "cpc6128", "amstradcpc", "amstradcpc6128");
        register(APPLE2, "apple2", "appleii", "apple2plus", "appleiiplus");
    }

    private DesktopMachineDefinitions() {
    }

    static DesktopMachineDefinition parse(String value) {
        DesktopMachineDefinition definition = BY_ALIAS.get(value.toLowerCase(Locale.ROOT));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown machine kind: " + value);
        }
        return definition;
    }

    static DesktopMachineDefinition forKind(DesktopMachineKind kind) {
        DesktopMachineDefinition definition = BY_KIND.get(kind);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown machine kind: " + kind);
        }
        return definition;
    }

    static String usage() {
        return "Usage: DesktopLauncher --machine=48|128|radio86rk|cpc6128|apple2|apple2plus <rom-or-memory-image> [media]";
    }

    static DesktopLaunchConfig demoConfig() {
        return new DesktopLaunchConfig(
                "demo",
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                true,
                null,
                DesktopMachineKind.SPECTRUM48
        );
    }

    static byte[] loadRom(Path romPath, DesktopMachineDefinition definition) throws IOException {
        byte[] romImage = Files.readAllBytes(romPath);
        definition.validateRom(romImage, romPath);
        return romImage;
    }

    private static void register(DesktopMachineDefinition definition, String... aliases) {
        for (String alias : aliases) {
            BY_ALIAS.put(alias, definition);
        }
    }

    private static final class SpectrumDefinition implements DesktopMachineDefinition {
        private final DesktopMachineKind kind;

        private SpectrumDefinition(DesktopMachineKind kind) {
            if (!kind.isSpectrum()) {
                throw new IllegalArgumentException("Expected Spectrum machine kind");
            }
            this.kind = kind;
        }

        @Override
        public DesktopMachineKind kind() {
            return kind;
        }

        @Override
        public void validateRom(byte[] romImage, Path romPath) {
            if (kind == DesktopMachineKind.SPECTRUM48 && romImage.length != Spectrum48kMemoryMap.ROM_SIZE) {
                throw new IllegalArgumentException("Spectrum 48K ROM must be exactly 16 KB: " + romPath);
            }
            if (kind == DesktopMachineKind.SPECTRUM128 && romImage.length != Spectrum128Machine.ROM_IMAGE_SIZE) {
                throw new IllegalArgumentException("Spectrum 128 ROM must be exactly 32 KB: " + romPath);
            }
        }

        @Override
        public void validateArgumentCount(int positionalCount) {
            if (positionalCount > 2) {
                throw new IllegalArgumentException("Usage: " + usage());
            }
        }

        @Override
        public DesktopLaunchConfig.LoadedMedia loadMedia(String rawPath) throws IOException {
            Path tapePath = Path.of(rawPath).toAbsolutePath().normalize();
            return new DesktopLaunchConfig.LoadedSpectrumTape(
                    tapePath.toString(),
                    TapeLoaders.load(tapePath)
            );
        }

        @Override
        public void open(DesktopLaunchConfig config) {
            SpectrumMachine machine = createMachine(config);
            if (config.demoMode()) {
                seedDemoScreen(machine);
            }
            SwingUtilities.invokeLater(() -> SpectrumDesktopRunner.open(machine, config));
        }

        @Override
        public String usage() {
            return "--machine=48|128 <rom> [tape.tap|tape.tzx]";
        }

        private SpectrumMachine createMachine(DesktopLaunchConfig config) {
            if (config.demoMode()) {
                return new Spectrum48kMachine(config.romImage());
            }
            return switch (kind) {
                case SPECTRUM48 -> new Spectrum48kMachine(config.romImage());
                case SPECTRUM128 -> new Spectrum128Machine(config.romImage());
                case RADIO86RK, CPC6128, APPLE2 -> throw new IllegalArgumentException("Expected Spectrum launch config");
            };
        }

        private static void seedDemoScreen(SpectrumMachine machine) {
            machine.board().ula().writePortFe(0x03, machine.currentTState(), machine.board().beeper());

            for (int y = 0; y < SpectrumUlaDevice.DISPLAY_HEIGHT; y++) {
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

    private static final class Radio86Definition implements DesktopMachineDefinition {
        @Override
        public DesktopMachineKind kind() {
            return DesktopMachineKind.RADIO86RK;
        }

        @Override
        public void validateRom(byte[] romImage, Path romPath) {
            if (romImage.length != Radio86Memory.ROM_SIZE_2K && romImage.length != Radio86Memory.ROM_SIZE_4K) {
                throw new IllegalArgumentException("Radio-86RK ROM must be exactly 2 KB or 4 KB: " + romPath);
            }
        }

        @Override
        public void validateArgumentCount(int positionalCount) {
            if (positionalCount > 2) {
                throw new IllegalArgumentException("Usage: " + usage());
            }
        }

        @Override
        public DesktopLaunchConfig.LoadedMedia loadMedia(String rawPath) throws IOException {
            Path tapePath = Path.of(rawPath).toAbsolutePath().normalize();
            return new DesktopLaunchConfig.LoadedRadioTape(
                    tapePath.toString(),
                    Radio86TapeLoaders.load(tapePath)
            );
        }

        @Override
        public void open(DesktopLaunchConfig config) {
            Radio86Machine machine = new Radio86Machine(config.romImage());
            SwingUtilities.invokeLater(() -> Radio86DesktopRunner.open(machine, config));
        }

        @Override
        public String usage() {
            return "--machine=radio86rk <rom> [tape.rk|tape.rkr]";
        }
    }

    private static final class CpcDefinition implements DesktopMachineDefinition {
        @Override
        public DesktopMachineKind kind() {
            return DesktopMachineKind.CPC6128;
        }

        @Override
        public void validateRom(byte[] romImage, Path romPath) {
            if (!CpcMachine.isSupportedCombinedRomSize(romImage.length)) {
                throw new IllegalArgumentException("CPC 6128 ROM bundle must be exactly 16 KB, 32 KB, or 48 KB: " + romPath);
            }
        }

        @Override
        public void validateArgumentCount(int positionalCount) {
            if (positionalCount > 2) {
                throw new IllegalArgumentException("Usage: " + usage());
            }
        }

        @Override
        public DesktopLaunchConfig.LoadedMedia loadMedia(String rawPath) throws IOException {
            Path diskPath = Path.of(rawPath).toAbsolutePath().normalize();
            return new DesktopLaunchConfig.LoadedCpcDisk(
                    diskPath.toString(),
                    CpcDskLoader.load(diskPath)
            );
        }

        @Override
        public void open(DesktopLaunchConfig config) {
            CpcDskImage disk = config.loadedMedia(DesktopLaunchConfig.LoadedCpcDisk.class)
                    .map(DesktopLaunchConfig.LoadedCpcDisk::diskImage)
                    .orElse(null);
            CpcMachine machine = new CpcMachine(config.romImage(), disk);
            SwingUtilities.invokeLater(() -> CpcDesktopRunner.open(machine, config));
        }

        @Override
        public String usage() {
            return "--machine=cpc6128 <rom-bundle> [disk.dsk]";
        }
    }

    private static final class Apple2Definition implements DesktopMachineDefinition {
        @Override
        public DesktopMachineKind kind() {
            return DesktopMachineKind.APPLE2;
        }

        @Override
        public void validateRom(byte[] romImage, Path romPath) {
            if (!Apple2Memory.isSupportedLaunchImageSize(romImage.length)) {
                throw new IllegalArgumentException("Apple II image must be exactly 4 KB, 8 KB, 12 KB, or 64 KB: " + romPath);
            }
        }

        @Override
        public void validateArgumentCount(int positionalCount) {
            if (positionalCount > 1) {
                throw new IllegalArgumentException("Usage: " + usage());
            }
        }

        @Override
        public void open(DesktopLaunchConfig config) {
            Apple2Machine machine = Apple2Machine.fromLaunchImage(config.romImage());
            SwingUtilities.invokeLater(() -> Apple2DesktopRunner.open(machine, config));
        }

        @Override
        public String usage() {
            return "--machine=apple2|apple2plus <system-rom|memory-image>";
        }
    }
}
