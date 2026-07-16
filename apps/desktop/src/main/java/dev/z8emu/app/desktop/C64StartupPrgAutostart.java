package dev.z8emu.app.desktop;

import dev.z8emu.machine.c64.C64KeyboardTyper;
import dev.z8emu.machine.c64.C64Machine;
import dev.z8emu.machine.c64.C64PrgLoader;
import dev.z8emu.machine.c64.C64ScreenText;

final class C64StartupPrgAutostart {
    private final C64Machine machine;
    private final DesktopLaunchConfig config;

    private boolean pending;

    C64StartupPrgAutostart(C64Machine machine, DesktopLaunchConfig config) {
        this.machine = machine;
        this.config = config;
    }

    void armIfNeeded() {
        pending = config.loadedMedia(DesktopLaunchConfig.LoadedC64Prg.class).isPresent();
    }

    void tick() {
        if (!pending) {
            return;
        }
        DesktopLaunchConfig.LoadedC64Prg prg = config
                .loadedMedia(DesktopLaunchConfig.LoadedC64Prg.class)
                .orElse(null);
        if (prg == null) {
            pending = false;
            return;
        }
        if (!C64ScreenText.contains(machine, "READY.")) {
            return;
        }
        C64PrgLoader.inject(machine, prg.image());
        String command = C64PrgLoader.startCommand(prg.image(), null);
        for (int index = 0; index < command.length(); index++) {
            C64KeyboardTyper.typeCharacter(machine, command.charAt(index));
        }
        pending = false;
    }

    void cancel() {
        pending = false;
    }
}
