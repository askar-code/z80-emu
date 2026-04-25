package dev.z8emu.app.desktop;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DesktopLauncher {
    private DesktopLauncher() {
    }

    public static void main(String[] args) throws IOException {
        DesktopLaunchConfig config = createLaunchConfig(args);
        DesktopMachineDefinitions.forKind(config.machineKind()).open(config);
    }

    private static DesktopLaunchConfig createLaunchConfig(String[] args) throws IOException {
        if (args.length == 0) {
            return DesktopMachineDefinitions.demoConfig();
        }

        DesktopMachineDefinition definition = null;
        Integer loadAddress = null;
        Integer startAddress = null;
        Path disk2RomPath = null;
        List<String> positionalArgs = new ArrayList<>(2);
        for (String arg : args) {
            if (arg.startsWith("--machine=")) {
                definition = DesktopMachineDefinitions.parse(arg.substring("--machine=".length()));
            } else if (arg.startsWith("--load-address=")) {
                if (loadAddress != null) {
                    throw new IllegalArgumentException("Duplicate --load-address");
                }
                loadAddress = DesktopLaunchOptions.parseAddress(arg.substring("--load-address=".length()));
            } else if (arg.startsWith("--start-address=")) {
                if (startAddress != null) {
                    throw new IllegalArgumentException("Duplicate --start-address");
                }
                startAddress = DesktopLaunchOptions.parseAddress(arg.substring("--start-address=".length()));
            } else if (arg.startsWith("--disk2-rom=")) {
                if (disk2RomPath != null) {
                    throw new IllegalArgumentException("Duplicate --disk2-rom");
                }
                disk2RomPath = Path.of(arg.substring("--disk2-rom=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else {
                positionalArgs.add(arg);
            }
        }

        if (definition == null || positionalArgs.isEmpty()) {
            throw new IllegalArgumentException(DesktopMachineDefinitions.usage());
        }
        definition.validateArgumentCount(positionalArgs.size());
        DesktopLaunchOptions launchOptions = new DesktopLaunchOptions(loadAddress, startAddress, disk2RomPath);
        definition.validateLaunchOptions(launchOptions, positionalArgs.size());

        Path romPath = Path.of(positionalArgs.get(0)).toAbsolutePath().normalize();
        byte[] romImage = DesktopMachineDefinitions.loadRom(romPath, definition);
        DesktopLaunchConfig.LoadedMedia loadedMedia = positionalArgs.size() == 1
                ? null
                : definition.loadMedia(positionalArgs.get(1), launchOptions);

        return new DesktopLaunchConfig(
                romPath.toString(),
                romImage,
                false,
                loadedMedia,
                launchOptions,
                definition.kind()
        );
    }
}
