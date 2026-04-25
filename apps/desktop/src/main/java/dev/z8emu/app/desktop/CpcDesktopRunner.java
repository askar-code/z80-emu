package dev.z8emu.app.desktop;

import dev.z8emu.machine.cpc.CpcMachine;
import dev.z8emu.platform.video.FrameBuffer;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.JFrame;

final class CpcDesktopRunner {
    private CpcDesktopRunner() {
    }

    static void open(CpcMachine machine, DesktopLaunchConfig config) {
        DesktopWindowRunner.open(new Session(machine, config));
    }

    private static final class Session implements DesktopMachineSession {
        private final CpcMachine machine;
        private final DesktopLaunchConfig config;
        private final FrameDisplayPanel panel;
        private CpcKeyboardController keyboardController;
        private PcmMonoAudioEngine audioEngine;

        private Session(CpcMachine machine, DesktopLaunchConfig config) {
            this.machine = machine;
            this.config = config;
            FrameBuffer initialFrame = machine.board().renderVideoFrame();
            this.panel = new FrameDisplayPanel(initialFrame.width(), initialFrame.height(), 1, 2);
        }

        @Override
        public void attachToFrame(JFrame frame) {
            keyboardController = CpcKeyboardController.bind(frame, panel, machine.board().keyboard());
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
                    + "  disk=" + diskStatus()
                    + "  cpu=z80";

            if (failure == null) {
                return base + "  " + status;
            }

            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            return base + "  " + status + "  stopped: " + message;
        }

        @Override
        public long frameDurationNanos() {
            return DesktopRunnerSupport.frameDurationNanos(machine.cpuClockHz(), machine.frameTStates());
        }

        @Override
        public boolean turboActive() {
            return false;
        }

        @Override
        public void runSlice() {
            long targetTState = machine.currentTState() + machine.frameTStates();
            while (machine.currentTState() < targetTState) {
                machine.runInstruction();
            }
        }

        @Override
        public void presentFrame() {
            FrameBuffer frame = machine.board().renderVideoFrame();
            panel.present(frame);
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
            if (keyboardController != null) {
                keyboardController.close();
            }
            if (audioEngine != null) {
                audioEngine.close();
            }
        }

        @Override
        public void handleFailure(Throwable failure) {
            failure.printStackTrace(System.err);
        }

        @Override
        public String threadName() {
            return "cpc-video-runner";
        }

        private String diskStatus() {
            return config.loadedCpcDisk() == null ? "none" : config.loadedCpcDisk().sourceLabel();
        }
    }

    private static PcmMonoAudioEngine tryStartAudio(CpcMachine machine) {
        try {
            return PcmMonoAudioEngine.start(machine.board().audio(), "cpc-audio");
        } catch (LineUnavailableException unavailable) {
            System.err.println("Audio disabled: " + unavailable.getMessage());
            return null;
        }
    }
}
