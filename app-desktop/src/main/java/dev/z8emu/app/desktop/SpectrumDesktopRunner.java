package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

final class SpectrumDesktopRunner {
    private static final int NORMAL_FRAMES_PER_SLICE = 1;

    private SpectrumDesktopRunner() {
    }

    static void open(SpectrumMachine machine, DesktopLaunchConfig config) {
        DesktopWindowRunner.open(new Session(machine, config));
    }

    private static final class Session implements DesktopMachineSession {
        private final SpectrumMachine machine;
        private final DesktopLaunchConfig config;
        private final SpectrumDisplayPanel panel = new SpectrumDisplayPanel();
        private final HostKeyTyper hostKeyTyper;
        private final SpectrumAutostartRunRescue autostartRunRescue = new SpectrumAutostartRunRescue();

        private SpectrumKeyboardController keyboardController;
        private PcmMonoAudioEngine audioEngine;
        private final SpectrumStartupTapeAutoplay startupTapeAutoplay;

        private Session(SpectrumMachine machine, DesktopLaunchConfig config) {
            this.machine = machine;
            this.config = config;
            this.hostKeyTyper = HostKeyTyper.get(machine);
            this.startupTapeAutoplay = new SpectrumStartupTapeAutoplay(machine, config, hostKeyTyper);
        }

        @Override
        public void attachToFrame(JFrame frame) {
            if (config.loadedTape() != null) {
                machine.board().tape().load(config.loadedTape().tapeFile());
            }
            keyboardController = SpectrumKeyboardController.bind(
                    frame,
                    panel,
                    machine.board().keyboard(),
                    new SpectrumKeyboardController.HostActions() {
                        @Override
                        public boolean typeHostCharacter(char character) {
                            return queueHostCharacter(machine, character);
                        }

                        @Override
                        public void toggleTapePlayback() {
                            if (!machine.board().tape().isLoaded()) {
                                return;
                            }
                            startupTapeAutoplay.cancel();
                            if (machine.board().tape().isPlaying()) {
                                machine.board().tape().stop();
                            } else {
                                machine.board().tape().play();
                            }
                        }

                        @Override
                        public void rewindTape() {
                            startupTapeAutoplay.cancel();
                            machine.board().tape().rewind();
                        }

                        @Override
                        public void stopTape() {
                            startupTapeAutoplay.cancel();
                            machine.board().tape().stop();
                        }
                    }
            );
            startupTapeAutoplay.armIfNeeded();
            audioEngine = tryStartAudio(machine);
        }

        @Override
        public javax.swing.JComponent component() {
            return panel;
        }

        @Override
        public String initialTitle() {
            return title(null);
        }

        @Override
        public String title(Throwable failure) {
            String base = "z8-emu " + machine.board().modelConfig().modelName();
            String status = "source=" + config.sourceLabel()
                    + "  pc=0x" + hex16(machine.cpu().registers().pc())
                    + "  t=" + machine.currentTState()
                    + "  frame=" + machine.board().ula().frameCounter()
                    + "  tape=" + tapeStatus(machine, config)
                    + "  rom=" + machine.board().machineState().selectedRomIndex()
                    + "  bank=" + machine.board().machineState().topRamBankIndex()
                    + "  screen=" + machine.board().machineState().activeScreenBankIndex()
                    + "  " + loaderStatus(machine)
                    + "  key=" + keyboardController.lastEvent()
                    + "  host=[symbols direct, Cmd+P play/pause, Cmd+R rewind, Cmd+S stop]";

            if (failure == null) {
                return base + "  " + status;
            }

            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            return base + "  " + status + "  stopped: " + message;
        }

        @Override
        public long frameDurationNanos() {
            return DesktopRunnerSupport.frameDurationNanos(
                    machine.board().modelConfig().cpuClockHz(),
                    machine.board().modelConfig().frameTStates()
            );
        }

        @Override
        public boolean turboActive() {
            return machine.board().tape().isPlaying() && tapeTurboEnabled();
        }

        @Override
        public void runSlice() {
            if (config.demoMode()) {
                return;
            }

            int framesPerSlice = machine.board().tape().isPlaying() ? tapeFramesPerSlice() : NORMAL_FRAMES_PER_SLICE;
            for (int frameIndex = 0; frameIndex < framesPerSlice; frameIndex++) {
                if (frameIndex > 0 && !machine.board().tape().isPlaying()) {
                    break;
                }
                hostKeyTyper.tick();
                startupTapeAutoplay.tick();
                long targetTState = nextFrameBoundaryTState();
                while (machine.currentTState() < targetTState) {
                    autostartRunRescue.tick(machine);
                    machine.runInstruction();
                }
            }
        }

        private long nextFrameBoundaryTState() {
            long current = machine.currentTState();
            long frameTStates = machine.board().modelConfig().frameTStates();
            long remainder = Math.floorMod(current, frameTStates);
            return remainder == 0
                    ? current + frameTStates
                    : current + frameTStates - remainder;
        }

        @Override
        public void presentFrame() {
            panel.present(machine.board().renderVideoFrame());
        }

        @Override
        public void onFocusGained() {
            panel.requestFocusInWindow();
        }

        @Override
        public void onFocusLost() {
            keyboardController.releaseAllKeys();
        }

        @Override
        public void close() {
            keyboardController.close();
            if (audioEngine != null) {
                audioEngine.close();
            }
        }

        @Override
        public void handleFailure(Throwable failure) {
            writeFailureReport(machine, config, keyboardController.lastEvent(), failure);
        }

        @Override
        public String threadName() {
            return "spectrum-video-runner";
        }
    }

    private static PcmMonoAudioEngine tryStartAudio(SpectrumMachine machine) {
        try {
            return PcmMonoAudioEngine.start(machine.board().audio(), "spectrum-audio");
        } catch (LineUnavailableException unavailable) {
            System.err.println("Audio disabled: " + unavailable.getMessage());
            return null;
        }
    }

    private static void writeFailureReport(
            SpectrumMachine machine,
            DesktopLaunchConfig config,
            String lastKeyEvent,
            Throwable failure
    ) {
        try {
            Path reportPath = Path.of("/tmp/z8-emu-last-crash.txt");
            StringWriter stack = new StringWriter();
            failure.printStackTrace(new PrintWriter(stack));

            int pc = machine.cpu().registers().pc();
            StringBuilder body = new StringBuilder();
            body.append("source=").append(config.sourceLabel()).append('\n');
            body.append("pc=0x").append(hex16(pc)).append('\n');
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
            body.append("tape=").append(tapeStatus(machine, config)).append('\n');
            body.append("lastKey=").append(lastKeyEvent).append('\n');
            body.append("bytesAroundPc:\n");
            for (int address = pc - 16; address <= pc + 16; address++) {
                int normalized = address & 0xFFFF;
                body.append(hex16(normalized))
                        .append(": ")
                        .append(hex8(machine.board().memory().read(normalized)))
                        .append('\n');
            }
            body.append("failure=\n").append(stack);

            Files.writeString(
                    reportPath,
                    body.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            System.err.println("Wrote crash report to " + reportPath);
        } catch (IOException io) {
            System.err.println("Failed to write crash report: " + io.getMessage());
        }
    }

    private static String tapeStatus(SpectrumMachine machine, DesktopLaunchConfig config) {
        if (config.loadedTape() == null) {
            return "none";
        }

        String state = machine.board().tape().isPlaying()
                ? "play"
                : (machine.board().tape().isAtEnd() ? "eof" : "stop");
        String speed = machine.board().tape().isPlaying()
                ? (tapeTurboEnabled() ? "turbo" : "real")
                : "idle";
        return state + ":" + machine.board().tape().currentBlockIndex() + "/" + machine.board().tape().totalBlocks() + ":" + speed;
    }

    private static String loaderStatus(SpectrumMachine machine) {
        int pc = machine.cpu().registers().pc();
        String loader = switch (pc) {
            case 0x0556 -> "LD_BYTES";
            case 0x05E3 -> "LD_EDGE_2";
            case 0x05E7 -> "LD_EDGE_1";
            case 0x05ED -> "LD_SAMPLE";
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

    private static boolean queueHostCharacter(SpectrumMachine machine, char character) {
        HostKeyTyper typer = HostKeyTyper.get(machine);
        return switch (character) {
            case '"' -> {
                typer.queueChord(new int[][]{{7, 1}, {5, 0}});
                yield true;
            }
            case ':' -> {
                typer.queueChord(new int[][]{{7, 1}, {0, 1}});
                yield true;
            }
            case ';' -> {
                typer.queueChord(new int[][]{{7, 1}, {5, 1}});
                yield true;
            }
            case ',' -> {
                typer.queueChord(new int[][]{{7, 1}, {7, 3}});
                yield true;
            }
            case '.' -> {
                typer.queueChord(new int[][]{{7, 1}, {7, 2}});
                yield true;
            }
            case '!' -> {
                typer.queueChord(new int[][]{{7, 1}, {3, 0}});
                yield true;
            }
            case '?' -> {
                typer.queueChord(new int[][]{{7, 1}, {0, 3}});
                yield true;
            }
            case '\'' -> {
                typer.queueChord(new int[][]{{7, 1}, {4, 3}});
                yield true;
            }
            case '#' -> {
                typer.queueChord(new int[][]{{7, 1}, {3, 2}});
                yield true;
            }
            case '$' -> {
                typer.queueChord(new int[][]{{7, 1}, {3, 3}});
                yield true;
            }
            case '%' -> {
                typer.queueChord(new int[][]{{7, 1}, {3, 4}});
                yield true;
            }
            case '&' -> {
                typer.queueChord(new int[][]{{7, 1}, {4, 4}});
                yield true;
            }
            case '@' -> {
                typer.queueChord(new int[][]{{7, 1}, {3, 1}});
                yield true;
            }
            case '+' -> {
                typer.queueChord(new int[][]{{7, 1}, {6, 2}});
                yield true;
            }
            case '-' -> {
                typer.queueChord(new int[][]{{7, 1}, {6, 3}});
                yield true;
            }
            case '*' -> {
                typer.queueChord(new int[][]{{7, 1}, {7, 4}});
                yield true;
            }
            case '/' -> {
                typer.queueChord(new int[][]{{7, 1}, {0, 4}});
                yield true;
            }
            case '(' -> {
                typer.queueChord(new int[][]{{7, 1}, {4, 2}});
                yield true;
            }
            case ')' -> {
                typer.queueChord(new int[][]{{7, 1}, {4, 1}});
                yield true;
            }
            case '=' -> {
                typer.queueChord(new int[][]{{7, 1}, {6, 1}});
                yield true;
            }
            case '<' -> {
                typer.queueChord(new int[][]{{7, 1}, {2, 3}});
                yield true;
            }
            case '>' -> {
                typer.queueChord(new int[][]{{7, 1}, {2, 4}});
                yield true;
            }
            case '_' -> {
                typer.queueChord(new int[][]{{7, 1}, {4, 0}});
                yield true;
            }
            default -> false;
        };
    }

    private static int tapeFramesPerSlice() {
        return Math.max(1, Integer.getInteger("z8emu.tapeTurboFrames", 1));
    }

    private static boolean tapeTurboEnabled() {
        return tapeFramesPerSlice() > 1;
    }

    private static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }

    static final class HostKeyTyper {
        private static final int FRAMES_PER_PRESS = 3;
        private static final int FRAMES_PER_GAP = 2;
        private static final java.util.Map<SpectrumMachine, HostKeyTyper> INSTANCES = new java.util.WeakHashMap<>();

        private final SpectrumMachine machine;
        private final Queue<QueuedChord> queue = new ArrayDeque<>();
        private QueuedChord activeKey;
        private int framesRemaining;
        private boolean pressPhase = true;

        private HostKeyTyper(SpectrumMachine machine) {
            this.machine = machine;
        }

        static synchronized HostKeyTyper get(SpectrumMachine machine) {
            return INSTANCES.computeIfAbsent(machine, HostKeyTyper::new);
        }

        synchronized void queueChord(int[][] keys) {
            queueChord(keys, FRAMES_PER_PRESS, FRAMES_PER_GAP);
        }

        synchronized void queueChord(int[][] keys, int pressFrames, int gapFrames) {
            queue.add(new QueuedChord(keys, Math.max(1, pressFrames), Math.max(0, gapFrames)));
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
}
