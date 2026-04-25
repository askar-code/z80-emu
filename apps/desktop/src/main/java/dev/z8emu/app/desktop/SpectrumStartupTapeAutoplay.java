package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;

final class SpectrumStartupTapeAutoplay {
    private final SpectrumMachine machine;
    private final DesktopLaunchConfig config;
    private final SpectrumDesktopRunner.HostKeyTyper hostKeyTyper;

    private boolean pending;

    SpectrumStartupTapeAutoplay(
            SpectrumMachine machine,
            DesktopLaunchConfig config,
            SpectrumDesktopRunner.HostKeyTyper hostKeyTyper
    ) {
        this.machine = machine;
        this.config = config;
        this.hostKeyTyper = hostKeyTyper;
    }

    void armIfNeeded() {
        if (config.loadedTape() == null) {
            pending = false;
            return;
        }

        pending = true;
        if (machine.board().modelConfig().pagingSupported()) {
            // On 128K the boot menu highlights Tape Loader by default, so a single
            // synthetic Enter plus an app-side wait for the ROM loader routine
            // reproduces the common desktop flow without coupling autoplay to
            // EAR sampling in the machine core.
            hostKeyTyper.queueChord(new int[][]{{6, 0}}, 12, 6);
        }
    }

    void cancel() {
        pending = false;
    }

    void tick() {
        if (!pending || config.loadedTape() == null) {
            return;
        }
        if (machine.board().tape().isPlaying()) {
            pending = false;
            return;
        }
        if (!SpectrumTapeAutostartSupport.isLoaderReadyForPlayback(machine)) {
            return;
        }
        machine.board().tape().play();
        pending = false;
    }
}
