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
import javax.swing.JFrame;

final class Radio86DesktopRunner {
    private static final int NORMAL_FRAMES_PER_SLICE = 1;

    private Radio86DesktopRunner() {
    }

    static void open(Radio86Machine machine, DesktopLaunchConfig config) {
        DesktopWindowRunner.open(new Session(machine, config));
    }

    private static final class Session extends AbstractFrameDesktopSession<FrameDisplayPanel> {
        private final Radio86Machine machine;
        private final DesktopLaunchConfig config;

        private Radio86KeyboardController keyboardController;

        private Session(Radio86Machine machine, DesktopLaunchConfig config) {
            super(
                    createPanel(machine),
                    machine.board().audio(),
                    "radio86-audio",
                    machine.cpuClockHz(),
                    machine.frameTStates()
            );
            this.machine = machine;
            this.config = config;
        }

        @Override
        protected void attachMachine(JFrame frame) {
            config.loadedMedia(DesktopLaunchConfig.LoadedRadioTape.class)
                    .ifPresent(loadedTape -> machine.board().tape().load(loadedTape.tapeFile()));
            keyboardController = Radio86KeyboardController.bind(
                    frame,
                    displayComponent(),
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
                long targetTState = machine.currentTState() + machine.frameTStates();
                runUntilTState(machine, targetTState);
            }
        }

        @Override
        protected FrameBuffer renderVideoFrame() {
            return machine.board().renderVideoFrame();
        }

        @Override
        protected void presentFrameBuffer(FrameDisplayPanel component, FrameBuffer frame) {
            component.present(frame);
        }

        @Override
        protected void releaseInputOnFocusLost() {
            if (keyboardController != null) {
                keyboardController.releaseAllKeys();
            }
        }

        @Override
        protected void closeMachineResources() {
            if (keyboardController != null) {
                keyboardController.close();
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

        private static FrameDisplayPanel createPanel(Radio86Machine machine) {
            FrameBuffer initialFrame = machine.board().renderVideoFrame();
            return new FrameDisplayPanel(initialFrame.width(), initialFrame.height(), 2);
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
        if (config.loadedMedia(DesktopLaunchConfig.LoadedRadioTape.class).isEmpty()) {
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
