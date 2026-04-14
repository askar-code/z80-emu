package dev.z8emu.app.desktop;

import dev.z8emu.machine.radio86rk.Radio86Machine;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.platform.video.FrameBuffer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.JFrame;

final class Radio86DesktopRunner {
    private static final int NORMAL_FRAMES_PER_SLICE = 1;

    private Radio86DesktopRunner() {
    }

    static void open(Radio86Machine machine, DesktopLaunchConfig config) {
        DesktopWindowRunner.open(new Session(machine, config));
    }

    private static final class Session implements DesktopMachineSession {
        private final Radio86Machine machine;
        private final DesktopLaunchConfig config;
        private final FrameDisplayPanel panel;

        private Radio86KeyboardController keyboardController;
        private Radio86AudioEngine audioEngine;

        private Session(Radio86Machine machine, DesktopLaunchConfig config) {
            this.machine = machine;
            this.config = config;
            FrameBuffer initialFrame = machine.board().renderVideoFrame();
            this.panel = new FrameDisplayPanel(initialFrame.width(), initialFrame.height(), 2);
        }

        @Override
        public void attachToFrame(JFrame frame) {
            if (config.loadedRadioTape() != null) {
                machine.board().tape().load(config.loadedRadioTape().tapeFile());
            }
            keyboardController = Radio86KeyboardController.bind(
                    frame,
                    panel,
                    machine.board().keyboard(),
                    new Radio86KeyboardController.HostActions() {
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
            String base = "z8-emu " + machine.board().modelName();
            String status = "source=" + config.sourceLabel()
                    + "  pc=0x" + hex16(machine.cpu().registers().pc())
                    + "  t=" + machine.currentTState()
                    + "  shadow=" + (machine.board().memory().bootShadowEnabled() ? 1 : 0)
                    + "  screen=0x" + hex16(Radio86Memory.VIDEO_MEMORY_START)
                    + "  tape=" + tapeStatus(machine, config)
                    + "  key=" + keyboardController.lastEvent()
                    + "  inte=" + (machine.board().cpuInteOutputHigh() ? 1 : 0)
                    + "  cpu=i8080";

            if (failure == null) {
                return base + "  " + status;
            }

            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            return base + "  " + status + "  stopped: " + message;
        }

        @Override
        public long frameDurationNanos() {
            return DesktopRunnerSupport.frameDurationNanos(Radio86Machine.CPU_CLOCK_HZ, Radio86Machine.FRAME_T_STATES);
        }

        @Override
        public boolean turboActive() {
            return machine.board().tape().isPlaying() && tapeTurboEnabled();
        }

        @Override
        public void runSlice() {
            int framesPerSlice = machine.board().tape().isPlaying() ? tapeFramesPerSlice() : NORMAL_FRAMES_PER_SLICE;
            for (int frameIndex = 0; frameIndex < framesPerSlice; frameIndex++) {
                if (frameIndex > 0 && !machine.board().tape().isPlaying()) {
                    break;
                }
                keyboardController.tick();
                long targetTState = machine.currentTState() + Radio86Machine.FRAME_T_STATES;
                while (machine.currentTState() < targetTState) {
                    machine.runInstruction();
                }
            }
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
            return "radio86-video-runner";
        }
    }

    private static Radio86AudioEngine tryStartAudio(Radio86Machine machine) {
        try {
            return Radio86AudioEngine.start(machine.board().audio());
        } catch (LineUnavailableException unavailable) {
            System.err.println("Audio disabled: " + unavailable.getMessage());
            return null;
        }
    }

    private static void writeFailureReport(
            Radio86Machine machine,
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
            body.append("inte=").append(machine.board().cpuInteOutputHigh()).append('\n');
            body.append("t=").append(machine.currentTState()).append('\n');
            body.append("shadow=").append(machine.board().memory().bootShadowEnabled()).append('\n');
            body.append("lastKey=").append(lastKeyEvent).append('\n');
            body.append("bytesAroundPc:\n");
            for (int address = pc - 16; address <= pc + 16; address++) {
                int normalized = address & 0xFFFF;
                body.append(hex16(normalized))
                        .append(": ")
                        .append(hex8(machine.board().cpuBus().readMemory(normalized)))
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

    private static String tapeStatus(Radio86Machine machine, DesktopLaunchConfig config) {
        if (config.loadedRadioTape() == null) {
            return "none";
        }
        if (machine.board().tape().isPlaying()) {
            return "play:" + machine.board().tape().currentHalfWaveIndex() + "/" + machine.board().tape().totalHalfWaves();
        }
        if (machine.board().tape().isAtEnd()) {
            return "eof";
        }
        return "stop";
    }

    private static int tapeFramesPerSlice() {
        return Math.max(1, Integer.getInteger("z8emu.radioTapeTurboFrames", 64));
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
}
