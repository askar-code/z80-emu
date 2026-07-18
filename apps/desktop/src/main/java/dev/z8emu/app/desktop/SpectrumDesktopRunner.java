package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshot;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import static dev.z8emu.app.desktop.ProbeOutput.hex16;
import static dev.z8emu.app.desktop.ProbeOutput.hex8;

final class SpectrumDesktopRunner {
    private static final int NORMAL_FRAMES_PER_SLICE = 1;
    private static final int TAPE_FRAMES_PER_SLICE = Math.max(1, Integer.getInteger("z8emu.tapeTurboFrames", 1));

    private SpectrumDesktopRunner() {
    }

    static void open(SpectrumMachine machine, DesktopLaunchConfig config) {
        DesktopWindowRunner.open(new Session(machine, config));
    }

    private static final class Session extends AbstractFrameDesktopSession {
        private final SpectrumMachine machine;
        private final DesktopLaunchConfig config;
        private final HostKeyTyper hostKeyTyper;
        private final SpectrumDesktopPreferences preferences;
        private volatile SpectrumJoystickProfile joystickProfile;
        private final Queue<SnapshotCommand> snapshotCommands = new ConcurrentLinkedQueue<>();
        private final SpectrumRuntimeTapeController runtimeTape;

        private SpectrumKeyboardController keyboardController;
        private final SpectrumStartupTapeAutoplay startupTapeAutoplay;
        private JFrame frame;
        private volatile String snapshotStatus;

        private Session(SpectrumMachine machine, DesktopLaunchConfig config) {
            super(
                    new FrameDisplayPanel(SpectrumUlaDevice.FRAME_WIDTH, SpectrumUlaDevice.FRAME_HEIGHT, 2),
                    machine.board().audio(),
                    "spectrum-audio",
                    machine.board().modelConfig().cpuClockHz(),
                    machine.board().modelConfig().frameTStates()
            );
            this.machine = machine;
            this.config = config;
            this.hostKeyTyper = new HostKeyTyper(machine);
            this.preferences = SpectrumDesktopPreferences.userPreferences();
            this.joystickProfile = preferences.initialJoystickProfile();
            this.startupTapeAutoplay = new SpectrumStartupTapeAutoplay(machine, hostKeyTyper);
            this.runtimeTape = new SpectrumRuntimeTapeController(
                    machine.board().tape(),
                    config.loadedMedia(DesktopLaunchConfig.LoadedSpectrumTape.class)
                            .map(DesktopLaunchConfig.LoadedSpectrumTape::sourceLabel)
                            .orElse(null),
                    this::cancelTapeAutomation,
                    this::showTapeError
            );
            this.snapshotStatus = config.loadedMedia(DesktopLaunchConfig.LoadedSpectrumSnapshot.class)
                    .map(loaded -> "loaded:" + Path.of(loaded.sourceLabel()).getFileName())
                    .orElse("ready");
        }

        @Override
        protected void attachMachine(JFrame frame) {
            this.frame = frame;
            config.loadedMedia(DesktopLaunchConfig.LoadedSpectrumTape.class)
                    .ifPresent(loadedTape -> machine.board().tape().load(loadedTape.tapeFile()));
            keyboardController = SpectrumKeyboardController.bind(
                    frame,
                    displayComponent(),
                    machine.board().keyboard(),
                    machine.board().kempstonJoystick(),
                    joystickProfile,
                    new SpectrumKeyboardController.HostActions() {
                        @Override
                        public boolean typeHostCharacter(char character) {
                            return queueHostCharacter(hostKeyTyper, character);
                        }

                        @Override
                        public void toggleTapePlayback() {
                            runtimeTape.togglePlayback();
                        }

                        @Override
                        public void rewindTape() {
                            runtimeTape.rewind();
                        }

                        @Override
                        public void stopTape() {
                            runtimeTape.stop();
                        }

                        @Override
                        public void openTape() {
                            chooseTapeToOpen();
                        }

                        @Override
                        public void ejectTape() {
                            runtimeTape.eject();
                        }

                        @Override
                        public void previousTapeBlock() {
                            runtimeTape.previousBlock();
                        }

                        @Override
                        public void nextTapeBlock() {
                            runtimeTape.nextBlock();
                        }

                        @Override
                        public void cycleJoystickProfile() {
                            SpectrumDesktopRunner.Session.this.cycleJoystickProfile();
                        }

                        @Override
                        public void loadSnapshot() {
                            chooseSnapshotToLoad();
                        }

                        @Override
                        public void saveSnapshot() {
                            chooseSnapshotToSave();
                        }
                    }
            );
            bindKeyboardController(keyboardController);
            startupTapeAutoplay.armIfNeeded();
        }

        @Override
        protected String statusTitle() {
            String base = "z8-emu " + machine.board().modelConfig().modelName();
            String status = "source=" + config.sourceLabel()
                    + "  pc=0x" + hex16(machine.cpu().registers().pc())
                    + "  t=" + machine.currentTState()
                    + "  frame=" + machine.board().ula().frameCounter()
                    + "  tape=" + runtimeTape.statusText()
                    + "  rom=" + machine.board().machineState().selectedRomIndex()
                    + "  bank=" + machine.board().machineState().topRamBankIndex()
                    + "  screen=" + machine.board().machineState().activeScreenBankIndex()
                    + "  joy=" + joystickProfile.settingValue()
                    + "  snapshot=" + snapshotStatus
                    + "  " + loaderStatus(machine)
                    + "  key=" + keyboardController.lastEvent()
                    + "  host=[arrows, fire=" + joystickProfile.fireKeyHint()
                    + ", symbols direct, Cmd+J joystick, Cmd+O open tape, Cmd+E eject"
                    + ", Cmd+P play/pause, Cmd+R rewind, Cmd+S stop, Cmd+[ / ] block"
                    + ", Cmd+Shift+O load snapshot, Cmd+Shift+S save snapshot]";
            return base + "  " + status;
        }

        @Override
        public boolean turboActive() {
            return machine.board().tape().isPlaying() && tapeTurboEnabled();
        }

        @Override
        public void runSlice() {
            runtimeTape.processPending();
            processSnapshotCommands();
            synchronizeTurboAudio();
            if (config.demoMode()) {
                return;
            }

            int framesPerSlice = machine.board().tape().isPlaying() ? TAPE_FRAMES_PER_SLICE : NORMAL_FRAMES_PER_SLICE;
            try {
                for (int frameIndex = 0; frameIndex < framesPerSlice; frameIndex++) {
                    if (frameIndex > 0 && !machine.board().tape().isPlaying()) {
                        break;
                    }
                    hostKeyTyper.tick();
                    startupTapeAutoplay.tick();
                    synchronizeTurboAudio();
                    long frameBoundary = nextFrameBoundaryTState(machine, machine.board().modelConfig().frameTStates());
                    if (startupTapeAutoplay.pending()) {
                        runUntilTStateWithAutoplay(machine, frameBoundary, startupTapeAutoplay);
                    } else {
                        runUntilTState(machine, frameBoundary);
                    }
                }
            } finally {
                synchronizeTurboAudio();
            }
        }

        private void synchronizeTurboAudio() {
            setAudioMuted(turboActive());
        }

        @Override
        protected dev.z8emu.platform.video.FrameBuffer renderVideoFrame() {
            return machine.board().renderVideoFrame();
        }

        @Override
        public void handleFailure(Throwable failure) {
            CrashReportWriter.write(
                    config.sourceLabel(),
                    machine.cpu().registers().pc(),
                    body -> {
                        body.append("sp=0x").append(hex16(machine.cpu().registers().sp())).append('\n');
                        body.append("af=0x").append(hex16(machine.cpu().registers().af())).append('\n');
                        body.append("bc=0x").append(hex16(machine.cpu().registers().bc())).append('\n');
                        body.append("de=0x").append(hex16(machine.cpu().registers().de())).append('\n');
                        body.append("hl=0x").append(hex16(machine.cpu().registers().hl())).append('\n');
                        body.append("ix=0x").append(hex16(machine.cpu().registers().ix())).append('\n');
                        body.append("iy=0x").append(hex16(machine.cpu().registers().iy())).append('\n');
                        body.append("iff1=").append(machine.cpu().registers().iff1()).append('\n');
                        body.append("iff2=").append(machine.cpu().registers().iff2()).append('\n');
                        body.append("im=").append(machine.cpu().registers().interruptMode()).append('\n');
                        body.append("t=").append(machine.currentTState()).append('\n');
                        body.append("tape=").append(runtimeTape.statusText()).append('\n');
                    },
                    keyboardController.lastEvent(),
                    address -> machine.board().memory().read(address),
                    failure
            );
        }

        @Override
        public String threadName() {
            return "spectrum-video-runner";
        }

        private void chooseTapeToOpen() {
            JFileChooser chooser = tapeChooser();
            if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
                displayComponent().requestFocusInWindow();
                return;
            }

            Path source = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            try {
                // Parse completely before publishing a replacement command. A malformed
                // side therefore leaves the currently inserted tape untouched.
                runtimeTape.replace(SpectrumTapeFiles.load(source));
                preferences.rememberTape(source);
            } catch (Exception failure) {
                showTapeError("Cannot open Spectrum tape " + source + ": " + failure.getMessage());
            } finally {
                displayComponent().requestFocusInWindow();
            }
        }

        private void chooseSnapshotToLoad() {
            SpectrumSnapshotFiles.Model model = SpectrumSnapshotFiles.modelOf(machine);
            JFileChooser chooser = snapshotChooser("Load " + model.displayName() + " snapshot");
            if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
                displayComponent().requestFocusInWindow();
                return;
            }

            Path source = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            try {
                SpectrumSnapshotFiles.LoadedSnapshot loaded = SpectrumSnapshotFiles.load(source, model);
                snapshotCommands.add(new LoadSnapshotCommand(loaded));
                snapshotStatus = "pending-load:" + source.getFileName();
                preferences.rememberSnapshot(source);
            } catch (Exception failure) {
                showSnapshotError("Cannot load " + model.displayName() + " snapshot "
                        + source + ": " + failure.getMessage());
            } finally {
                displayComponent().requestFocusInWindow();
            }
        }

        private void chooseSnapshotToSave() {
            SpectrumSnapshotFiles.Model model = SpectrumSnapshotFiles.modelOf(machine);
            JFileChooser chooser = snapshotChooser("Save " + model.displayName() + " snapshot");
            chooser.setSelectedFile(new File("spectrum.z80"));
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
                displayComponent().requestFocusInWindow();
                return;
            }

            Path target = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            if (!SpectrumSnapshotFiles.hasSnapshotExtension(target)) {
                showSnapshotError("Snapshot filename must end in .sna or .z80; .z80 saves are compressed.");
                return;
            }
            String warning = SpectrumSnapshotFiles.warning(target, model);
            if (warning != null) {
                int continueWithLoss = JOptionPane.showConfirmDialog(
                        frame,
                        warning + "\nContinue with .sna?",
                        "128K SNA limitation",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (continueWithLoss != JOptionPane.YES_OPTION) {
                    displayComponent().requestFocusInWindow();
                    return;
                }
            }
            if (Files.exists(target)) {
                int overwrite = JOptionPane.showConfirmDialog(
                        frame,
                        "Replace existing snapshot?\n" + target,
                        "Save Spectrum snapshot",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (overwrite != JOptionPane.YES_OPTION) {
                    displayComponent().requestFocusInWindow();
                    return;
                }
            }

            snapshotCommands.add(new SaveSnapshotCommand(target));
            snapshotStatus = "pending-save:" + target.getFileName();
            preferences.rememberSnapshot(target);
            displayComponent().requestFocusInWindow();
        }

        private void cycleJoystickProfile() {
            SpectrumJoystickProfile nextProfile = joystickProfile.next();
            keyboardController.setJoystickProfile(nextProfile);
            joystickProfile = nextProfile;
            preferences.rememberJoystickProfile(nextProfile);
        }

        private void processSnapshotCommands() {
            SnapshotCommand command;
            while ((command = snapshotCommands.poll()) != null) {
                try {
                    switch (command) {
                        case LoadSnapshotCommand load -> applySnapshot(load.loaded());
                        case SaveSnapshotCommand save -> saveSnapshot(save.target());
                    }
                } catch (Exception failure) {
                    snapshotStatus = "error:" + failure.getClass().getSimpleName();
                    showSnapshotError("Spectrum snapshot operation failed: " + failure.getMessage());
                }
            }
        }

        private void applySnapshot(SpectrumSnapshotFiles.LoadedSnapshot loaded) {
            // This is intentionally first: even a stale or forged command with
            // the wrong model must not stop tape playback or release host keys.
            SpectrumSnapshotFiles.requireCompatible(
                    SpectrumSnapshotFiles.modelOf(machine),
                    loaded.snapshot(),
                    loaded.source()
            );
            startupTapeAutoplay.cancel();
            machine.board().tape().stop();
            hostKeyTyper.clear();
            keyboardController.releaseAllKeys();
            SpectrumSnapshotFiles.restore(machine, loaded.snapshot());
            snapshotStatus = "loaded:" + loaded.source().getFileName();
        }

        private void saveSnapshot(Path target) throws Exception {
            SpectrumSnapshot snapshot = SpectrumSnapshotFiles.capture(machine);
            SpectrumSnapshotFiles.SaveResult result = SpectrumSnapshotFiles.save(target, snapshot);
            snapshotStatus = "saved:" + target.getFileName();
            String message = "Saved " + result.model().displayName() + " "
                    + result.format().name().toLowerCase() + " snapshot:\n" + target;
            if (result.warning() != null) {
                message += "\n\n" + result.warning();
            }
            showSnapshotMessage(message);
        }

        private void showSnapshotError(String message) {
            showSnapshotDialog(message, "Spectrum snapshot error", JOptionPane.ERROR_MESSAGE);
        }

        private void showTapeError(String message) {
            showSnapshotDialog(message, "Spectrum tape error", JOptionPane.ERROR_MESSAGE);
        }

        private void showSnapshotMessage(String message) {
            showSnapshotDialog(message, "Spectrum snapshot", JOptionPane.INFORMATION_MESSAGE);
        }

        private void showSnapshotDialog(String message, String title, int messageType) {
            Runnable show = () -> {
                JOptionPane.showMessageDialog(frame, message, title, messageType);
                displayComponent().requestFocusInWindow();
            };
            if (SwingUtilities.isEventDispatchThread()) {
                show.run();
            } else {
                SwingUtilities.invokeLater(show);
            }
        }

        private JFileChooser snapshotChooser(String title) {
            JFileChooser chooser = new JFileChooser();
            preferences.snapshotDirectory().ifPresent(path -> chooser.setCurrentDirectory(path.toFile()));
            chooser.setDialogTitle(title);
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "Spectrum snapshots (*.sna, *.z80)",
                    "sna",
                    "z80"
            ));
            return chooser;
        }

        private JFileChooser tapeChooser() {
            JFileChooser chooser = new JFileChooser();
            preferences.tapeDirectory().ifPresent(path -> chooser.setCurrentDirectory(path.toFile()));
            chooser.setDialogTitle("Open or replace Spectrum tape");
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "Spectrum tapes (*.tap, *.tzx)",
                    "tap",
                    "tzx"
            ));
            return chooser;
        }

        private void cancelTapeAutomation() {
            startupTapeAutoplay.cancel();
            hostKeyTyper.clear();
        }

        private sealed interface SnapshotCommand permits LoadSnapshotCommand, SaveSnapshotCommand {
        }

        private record LoadSnapshotCommand(SpectrumSnapshotFiles.LoadedSnapshot loaded) implements SnapshotCommand {
        }

        private record SaveSnapshotCommand(Path target) implements SnapshotCommand {
        }
    }

    private static String loaderStatus(SpectrumMachine machine) {
        int pc = machine.cpu().registers().pc();
        String loader = switch (pc) {
            case SpectrumTapeAutostartSupport.LD_BYTES -> "LD_BYTES";
            case SpectrumTapeAutostartSupport.LD_EDGE_2 -> "LD_EDGE_2";
            case SpectrumTapeAutostartSupport.LD_EDGE_1 -> "LD_EDGE_1";
            case SpectrumTapeAutostartSupport.LD_SAMPLE -> "LD_SAMPLE";
            case 0x15E6 -> "WAIT_KEY2";
            case 0x15DE -> "WAIT_KEY1";
            default -> "";
        };

        if (loader.isEmpty()) {
            return "ear=" + (machine.board().tape().earHigh() ? 1 : 0);
        }

        int flags = machine.board().memory().read(0x5C3B);
        int lastKey = machine.board().memory().read(0x5C08);
        return "ldr=" + loader
                + " ear=" + (machine.board().tape().earHigh() ? 1 : 0)
                + " flags=0x" + hex8(flags)
                + " lastk=0x" + hex8(lastKey);
    }

    private static boolean queueHostCharacter(HostKeyTyper typer, char character) {
        int[] key = switch (character) {
            case '"' -> new int[]{5, 0};
            case ':' -> new int[]{0, 1};
            case ';' -> new int[]{5, 1};
            case ',' -> new int[]{7, 3};
            case '.' -> new int[]{7, 2};
            case '!' -> new int[]{3, 0};
            case '?' -> new int[]{0, 3};
            case '\'' -> new int[]{4, 3};
            case '#' -> new int[]{3, 2};
            case '$' -> new int[]{3, 3};
            case '%' -> new int[]{3, 4};
            case '&' -> new int[]{4, 4};
            case '@' -> new int[]{3, 1};
            case '+' -> new int[]{6, 2};
            case '-' -> new int[]{6, 3};
            case '*' -> new int[]{7, 4};
            case '/' -> new int[]{0, 4};
            case '(' -> new int[]{4, 2};
            case ')' -> new int[]{4, 1};
            case '=' -> new int[]{6, 1};
            case '<' -> new int[]{2, 3};
            case '>' -> new int[]{2, 4};
            case '_' -> new int[]{4, 0};
            default -> null;
        };
        if (key == null) {
            return false;
        }
        typer.queueChord(new int[][]{{7, 1}, key});
        return true;
    }

    static boolean tapeTurboEnabled() {
        return TAPE_FRAMES_PER_SLICE > 1;
    }

    static final class HostKeyTyper {
        private static final int FRAMES_PER_PRESS = 3;
        private static final int FRAMES_PER_GAP = 2;

        private final SpectrumMachine machine;
        private final Queue<QueuedChord> queue = new ArrayDeque<>();
        private QueuedChord activeKey;
        private int framesRemaining;
        private boolean pressPhase = true;

        HostKeyTyper(SpectrumMachine machine) {
            this.machine = machine;
        }

        synchronized void queueChord(int[][] keys) {
            queueChord(keys, FRAMES_PER_PRESS, FRAMES_PER_GAP);
        }

        synchronized void queueChord(int[][] keys, int pressFrames, int gapFrames) {
            queue.add(new QueuedChord(keys, Math.max(1, pressFrames), Math.max(0, gapFrames)));
        }

        synchronized void queuePause(int frames) {
            if (frames > 0) {
                queue.add(new QueuedChord(new int[0][], frames, 0));
            }
        }

        synchronized boolean isIdle() {
            return activeKey == null && queue.isEmpty();
        }

        synchronized void clear() {
            queue.clear();
            if (activeKey != null && pressPhase) {
                setChordState(activeKey, false);
            }
            activeKey = null;
            framesRemaining = 0;
            pressPhase = true;
        }

        synchronized void tick() {
            if (activeKey == null && queue.isEmpty()) {
                return;
            }

            if (activeKey == null) {
                activeKey = queue.poll();
                pressPhase = true;
                framesRemaining = activeKey.pressFrames();
                setChordState(activeKey, true);
                return;
            }

            framesRemaining--;
            if (framesRemaining > 0) {
                return;
            }

            if (pressPhase) {
                setChordState(activeKey, false);
                pressPhase = false;
                framesRemaining = activeKey.gapFrames();
            } else {
                activeKey = null;
            }
        }

        private void setChordState(QueuedChord chord, boolean pressed) {
            for (int[] key : chord.keys()) {
                machine.board().keyboard().setKeyPressed(key[0], key[1], pressed);
            }
        }

        private record QueuedChord(int[][] keys, int pressFrames, int gapFrames) {
        }
    }

    private static void runUntilTStateWithAutoplay(
            SpectrumMachine machine,
            long targetTState,
            SpectrumStartupTapeAutoplay autoplay
    ) {
        while (machine.currentTState() < targetTState) {
            autoplay.tick();
            machine.runInstruction();
        }
    }
}
