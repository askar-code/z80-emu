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
        List<String> positionalArgs = new ArrayList<>(2);
        for (String arg : args) {
            if (arg.startsWith("--machine=")) {
                definition = DesktopMachineDefinitions.parse(arg.substring("--machine=".length()));
            } else {
                positionalArgs.add(arg);
            }
        }

        if (definition == null || positionalArgs.isEmpty()) {
            throw new IllegalArgumentException(DesktopMachineDefinitions.usage());
        }
        definition.validateArgumentCount(positionalArgs.size());

        Path romPath = Path.of(positionalArgs.get(0)).toAbsolutePath().normalize();
        byte[] romImage = DesktopMachineDefinitions.loadRom(romPath, definition);
        DesktopLaunchConfig.LoadedMedia loadedMedia = positionalArgs.size() == 1
                ? null
                : definition.loadMedia(positionalArgs.get(1));

        return new DesktopLaunchConfig(
                romPath.toString(),
                romImage,
                false,
                loadedMedia,
                definition.kind()
        );
    }
}
