package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;

final class SpectrumStartupTapeAutoplay {
    private static final int[][] ENTER = {{6, 0}};
    private static final int[][] LOAD_KEYWORD = {{6, 3}};
    private static final int[][] DOUBLE_QUOTE = {{7, 1}, {5, 0}};
    private static final int COMMAND_PRESS_FRAMES = 4;
    private static final int COMMAND_GAP_FRAMES = 6;

    private final SpectrumMachine machine;
    private final SpectrumDesktopRunner.HostKeyTyper hostKeyTyper;
    private final int menuPressFrames;
    private final int menuGapFrames;
    private final int playDelayFrames;

    private Phase phase = Phase.IDLE;

    SpectrumStartupTapeAutoplay(
            SpectrumMachine machine,
            SpectrumDesktopRunner.HostKeyTyper hostKeyTyper
    ) {
        this(machine, hostKeyTyper, 12, 6, 0);
    }

    SpectrumStartupTapeAutoplay(
            SpectrumMachine machine,
            SpectrumDesktopRunner.HostKeyTyper hostKeyTyper,
            int menuPressFrames,
            int menuGapFrames,
            int playDelayFrames
    ) {
        this.machine = machine;
        this.hostKeyTyper = hostKeyTyper;
        this.menuPressFrames = Math.max(1, menuPressFrames);
        this.menuGapFrames = Math.max(0, menuGapFrames);
        this.playDelayFrames = Math.max(0, playDelayFrames);
    }

    void armIfNeeded() {
        if (!machine.board().tape().isLoaded()) {
            phase = Phase.IDLE;
            return;
        }

        phase = Phase.WAITING_FOR_BOOT_PROMPT;
    }

    void cancel() {
        phase = Phase.IDLE;
    }

    void tick() {
        if (phase == Phase.IDLE) {
            return;
        }
        if (!machine.board().tape().isLoaded()) {
            phase = Phase.IDLE;
            return;
        }
        if (machine.board().tape().isPlaying()) {
            phase = Phase.IDLE;
            return;
        }

        if (phase == Phase.WAITING_FOR_BOOT_PROMPT) {
            if (!SpectrumTapeAutostartSupport.isBootPromptReadyForAutostart(machine)) {
                return;
            }
            queueBootCommand();
            phase = Phase.WAITING_FOR_KEYS;
            return;
        }

        if (phase == Phase.WAITING_FOR_KEYS) {
            if (!hostKeyTyper.isIdle()) {
                return;
            }
            phase = Phase.WAITING_FOR_LOADER;
        }

        if (phase == Phase.WAITING_FOR_LOADER
                && SpectrumTapeAutostartSupport.isLoaderReadyForPlayback(machine)) {
            machine.board().tape().play();
            phase = Phase.IDLE;
        }
    }

    boolean pending() {
        return phase != Phase.IDLE;
    }

    Phase phase() {
        return phase;
    }

    private void queueBootCommand() {
        if (machine.board().modelConfig().pagingSupported()) {
            hostKeyTyper.queueChord(ENTER, menuPressFrames, menuGapFrames);
            if (playDelayFrames > 0) {
                hostKeyTyper.queuePause(playDelayFrames);
            }
            return;
        }

        // In 48K keyword mode J enters the LOAD token. Symbol Shift+P types a
        // quote, so this is the ROM-native LOAD "" command followed by Enter.
        hostKeyTyper.queueChord(LOAD_KEYWORD, COMMAND_PRESS_FRAMES, COMMAND_GAP_FRAMES);
        hostKeyTyper.queueChord(DOUBLE_QUOTE, COMMAND_PRESS_FRAMES, COMMAND_GAP_FRAMES);
        hostKeyTyper.queueChord(DOUBLE_QUOTE, COMMAND_PRESS_FRAMES, COMMAND_GAP_FRAMES);
        hostKeyTyper.queueChord(ENTER, COMMAND_PRESS_FRAMES, COMMAND_GAP_FRAMES);
    }

    enum Phase {
        IDLE,
        WAITING_FOR_BOOT_PROMPT,
        WAITING_FOR_KEYS,
        WAITING_FOR_LOADER
    }
}
