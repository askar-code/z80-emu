package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import dev.z8emu.machine.spectrum48k.tape.TapeLoaders;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class DesktopLauncher {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long CPU_CLOCK_HZ = 3_500_000L;
    private static final int NORMAL_FRAMES_PER_SLICE = 1;
    private static final long FRAME_DURATION_NANOS =
            (SpectrumUlaDevice.T_STATES_PER_FRAME * NANOS_PER_SECOND) / CPU_CLOCK_HZ;

    private DesktopLauncher() {
    }

    public static void main(String[] args) throws IOException {
        LaunchConfig config = createLaunchConfig(args);
        SpectrumMachine machine = createMachine(config);

        if (config.demoMode()) {
            seedDemoScreen(machine);
        }

        SwingUtilities.invokeLater(() -> openWindow(machine, config));
    }

    private static LaunchConfig createLaunchConfig(String[] args) throws IOException {
        if (args.length == 0) {
            return new LaunchConfig("demo", new byte[Spectrum48kMemoryMap.ROM_SIZE], true, null);
        }
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: DesktopLauncher [48.rom] [tape.tap|tape.tzx]");
        }

        Path romPath = Path.of(args[0]).toAbsolutePath().normalize();
        byte[] romImage = Files.readAllBytes(romPath);
        if (romImage.length != Spectrum48kMemoryMap.ROM_SIZE && romImage.length != Spectrum128Machine.ROM_IMAGE_SIZE) {
            throw new IllegalArgumentException("Spectrum ROM must be exactly 16 KB or 32 KB: " + romPath);
        }

        LoadedTape loadedTape = args.length == 2 ? loadTape(args[1]) : null;
        return new LaunchConfig(romPath.toString(), romImage, false, loadedTape);
    }

    private static LoadedTape loadTape(String rawPath) throws IOException {
        Path tapePath = Path.of(rawPath).toAbsolutePath().normalize();
        TapeFile tapeFile = TapeLoaders.load(tapePath);
        return new LoadedTape(tapePath.toString(), tapeFile);
    }

    private static SpectrumMachine createMachine(LaunchConfig config) {
        if (config.demoMode()) {
            return new Spectrum48kMachine(config.romImage());
        }
        return config.romImage().length == Spectrum128Machine.ROM_IMAGE_SIZE
                ? new Spectrum128Machine(config.romImage())
                : new Spectrum48kMachine(config.romImage());
    }

    private static void openWindow(SpectrumMachine machine, LaunchConfig config) {
        SpectrumDisplayPanel panel = new SpectrumDisplayPanel();
        JFrame frame = new JFrame();
        if (config.loadedTape() != null) {
            machine.board().tape().load(config.loadedTape().tapeFile());
        }
        SpectrumKeyboardController keyboardController = SpectrumKeyboardController.bind(
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
                        if (machine.board().tape().isPlaying()) {
                            machine.board().tape().stop();
                        } else {
                            machine.board().tape().play();
                        }
                    }

                    @Override
                    public void rewindTape() {
                        machine.board().tape().rewind();
                    }

                    @Override
                    public void stopTape() {
                        machine.board().tape().stop();
                    }
                }
        );
        BeeperAudioEngine audioEngine = tryStartAudio(machine);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
        panel.requestFocusInWindow();

        AtomicBoolean running = new AtomicBoolean(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                running.set(false);
                keyboardController.close();
                if (audioEngine != null) {
                    audioEngine.close();
                }
            }
        });
        frame.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                panel.requestFocusInWindow();
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                keyboardController.releaseAllKeys();
            }
        });

        panel.present(machine.board().renderVideoFrame());
        frame.setTitle(buildTitle(machine, config, keyboardController.lastEvent(), null));

        Thread runner = new Thread(
                () -> runMachineLoop(machine, panel, frame, config, keyboardController, running),
                "spectrum-video-runner"
        );
        runner.setDaemon(true);
        runner.start();
    }

    private static BeeperAudioEngine tryStartAudio(SpectrumMachine machine) {
        try {
            return BeeperAudioEngine.start(machine.board().beeper());
        } catch (LineUnavailableException unavailable) {
            System.err.println("Audio disabled: " + unavailable.getMessage());
            return null;
        }
    }

    private static void runMachineLoop(
            SpectrumMachine machine,
            SpectrumDisplayPanel panel,
            JFrame frame,
            LaunchConfig config,
            SpectrumKeyboardController keyboardController,
            AtomicBoolean running
    ) {
        Throwable failure = null;
        long nextFrameDeadline = System.nanoTime();
        HostKeyTyper hostKeyTyper = HostKeyTyper.get(machine);

        while (running.get()) {
            try {
                if (failure == null && !config.demoMode()) {
                    int framesPerSlice = machine.board().tape().isPlaying()
                            ? tapeFramesPerSlice()
                            : NORMAL_FRAMES_PER_SLICE;
                    for (int frameIndex = 0; frameIndex < framesPerSlice; frameIndex++) {
                        if (frameIndex > 0 && !machine.board().tape().isPlaying()) {
                            break;
                        }
                        hostKeyTyper.tick();
                        long targetTState = machine.currentTState() + SpectrumUlaDevice.T_STATES_PER_FRAME;
                        while (machine.currentTState() < targetTState) {
                            machine.runInstruction();
                        }
                    }
                }
            } catch (Throwable t) {
                failure = t;
                writeFailureReport(machine, config, keyboardController.lastEvent(), t);
            }

            panel.present(machine.board().renderVideoFrame());
            Throwable currentFailure = failure;
            boolean immediatePaint = !tapeTurboEnabled();
            if (immediatePaint) {
                invokeUiUpdate(panel, frame, machine, config, keyboardController.lastEvent(), currentFailure);
            } else {
                SwingUtilities.invokeLater(() -> {
                    panel.repaint();
                    frame.setTitle(buildTitle(machine, config, keyboardController.lastEvent(), currentFailure));
                });
            }

            if (failure != null) {
                return;
            }

            if (!machine.board().tape().isPlaying() || !tapeTurboEnabled()) {
                nextFrameDeadline += FRAME_DURATION_NANOS;
                long remaining = nextFrameDeadline - System.nanoTime();
                if (remaining > 0) {
                    LockSupport.parkNanos(remaining);
                } else if (remaining < -FRAME_DURATION_NANOS) {
                    // If rendering or execution stalls badly, resync to "now" instead of spiraling.
                    nextFrameDeadline = System.nanoTime();
                }
            } else {
                nextFrameDeadline = System.nanoTime();
            }
        }
    }

    private static String buildTitle(SpectrumMachine machine, LaunchConfig config, String lastKeyEvent, Throwable failure) {
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
                + "  key=" + lastKeyEvent
                + "  host=[symbols direct, Cmd+P play/pause, Cmd+R rewind, Cmd+S stop]";

        if (failure == null) {
            return base + "  " + status;
        }

        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return base + "  " + status + "  stopped: " + message;
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

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }

    private static void writeFailureReport(
            SpectrumMachine machine,
            LaunchConfig config,
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

    private static String tapeStatus(SpectrumMachine machine, LaunchConfig config) {
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

    private static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
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

    private static void invokeUiUpdate(
            SpectrumDisplayPanel panel,
            JFrame frame,
            SpectrumMachine machine,
            LaunchConfig config,
            String lastKeyEvent,
            Throwable failure
    ) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.setTitle(buildTitle(machine, config, lastKeyEvent, failure));
                panel.paintImmediately(0, 0, panel.getWidth(), panel.getHeight());
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException invocationFailure) {
            throw new IllegalStateException("Failed to update Swing UI", invocationFailure.getCause());
        }
    }

    private static int tapeFramesPerSlice() {
        return Math.max(1, Integer.getInteger("z8emu.tapeTurboFrames", 1));
    }

    private static boolean tapeTurboEnabled() {
        return tapeFramesPerSlice() > 1;
    }

    private static final class HostKeyTyper {
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
            queue.add(new QueuedChord(keys));
        }

        synchronized void tick() {
            if (activeKey == null && queue.isEmpty()) {
                return;
            }

            if (activeKey == null) {
                activeKey = queue.poll();
                pressPhase = true;
                framesRemaining = FRAMES_PER_PRESS;
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
                framesRemaining = FRAMES_PER_GAP;
            } else {
                activeKey = null;
            }
        }

        private void setChordState(QueuedChord chord, boolean pressed) {
            for (int[] key : chord.keys()) {
                machine.board().keyboard().setKeyPressed(key[0], key[1], pressed);
            }
        }

        private record QueuedChord(int[][] keys) {
        }
    }

    private record LaunchConfig(String sourceLabel, byte[] romImage, boolean demoMode, LoadedTape loadedTape) {
    }

    private record LoadedTape(String sourceLabel, TapeFile tapeFile) {
    }
}
