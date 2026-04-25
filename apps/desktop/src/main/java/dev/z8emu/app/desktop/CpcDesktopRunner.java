package dev.z8emu.app.desktop;

import dev.z8emu.machine.cpc.CpcMachine;
import dev.z8emu.platform.video.FrameBuffer;
import javax.swing.JFrame;

final class CpcDesktopRunner {
    private CpcDesktopRunner() {
    }

    static void open(CpcMachine machine, DesktopLaunchConfig config) {
        DesktopWindowRunner.open(new Session(machine, config));
    }

    private static final class Session extends AbstractFrameDesktopSession<FrameDisplayPanel> {
        private final CpcMachine machine;
        private final DesktopLaunchConfig config;
        private CpcKeyboardController keyboardController;

        private Session(CpcMachine machine, DesktopLaunchConfig config) {
            super(
                    createPanel(machine),
                    machine.board().audio(),
                    "cpc-audio",
                    machine.cpuClockHz(),
                    machine.frameTStates()
            );
            this.machine = machine;
            this.config = config;
        }

        @Override
        protected void attachMachine(JFrame frame) {
            keyboardController = CpcKeyboardController.bind(frame, displayComponent(), machine.board().keyboard());
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
        public void runSlice() {
            long targetTState = machine.currentTState() + machine.frameTStates();
            runUntilTState(machine, targetTState);
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
        public String threadName() {
            return "cpc-video-runner";
        }

        private String diskStatus() {
            return config.loadedMedia(DesktopLaunchConfig.LoadedCpcDisk.class)
                    .map(DesktopLaunchConfig.LoadedCpcDisk::sourceLabel)
                    .orElse("none");
        }

        private static FrameDisplayPanel createPanel(CpcMachine machine) {
            FrameBuffer initialFrame = machine.board().renderVideoFrame();
            return new FrameDisplayPanel(initialFrame.width(), initialFrame.height(), 1, 2);
        }
    }
}
