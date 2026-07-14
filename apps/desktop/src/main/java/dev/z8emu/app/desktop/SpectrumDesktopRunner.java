package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.swing.JFrame;

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

        private SpectrumKeyboardController keyboardController;
        private final SpectrumStartupTapeAutoplay startupTapeAutoplay;

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
            this.startupTapeAutoplay = new SpectrumStartupTapeAutoplay(machine, config, hostKeyTyper);
        }

        @Override
        protected void attachMachine(JFrame frame) {
            config.loadedMedia(DesktopLaunchConfig.LoadedSpectrumTape.class)
                    .ifPresent(loadedTape -> machine.board().tape().load(loadedTape.tapeFile()));
            keyboardController = SpectrumKeyboardController.bind(
                    frame,
                    displayComponent(),
                    machine.board().keyboard(),
                    new SpectrumKeyboardController.HostActions() {
                        @Override
                        public boolean typeHostCharacter(char character) {
                            return queueHostCharacter(hostKeyTyper, character);
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
                    + "  tape=" + tapeStatus(machine, config)
                    + "  rom=" + machine.board().machineState().selectedRomIndex()
                    + "  bank=" + machine.board().machineState().topRamBankIndex()
                    + "  screen=" + machine.board().machineState().activeScreenBankIndex()
                    + "  " + loaderStatus(machine)
                    + "  key=" + keyboardController.lastEvent()
                    + "  host=[symbols direct, Cmd+P play/pause, Cmd+R rewind, Cmd+S stop]";
            return base + "  " + status;
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

            int framesPerSlice = machine.board().tape().isPlaying() ? TAPE_FRAMES_PER_SLICE : NORMAL_FRAMES_PER_SLICE;
            for (int frameIndex = 0; frameIndex < framesPerSlice; frameIndex++) {
                if (frameIndex > 0 && !machine.board().tape().isPlaying()) {
                    break;
                }
                hostKeyTyper.tick();
                startupTapeAutoplay.tick();
                runUntilTState(machine, nextFrameBoundaryTState(machine, machine.board().modelConfig().frameTStates()));
            }
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
                        body.append("tape=").append(tapeStatus(machine, config)).append('\n');
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
    }

    private static String tapeStatus(SpectrumMachine machine, DesktopLaunchConfig config) {
        if (config.loadedMedia(DesktopLaunchConfig.LoadedSpectrumTape.class).isEmpty()) {
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

    private static boolean tapeTurboEnabled() {
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

        private HostKeyTyper(SpectrumMachine machine) {
            this.machine = machine;
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
