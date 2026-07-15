package dev.z8emu.app.desktop;

import dev.z8emu.machine.c64.C64Machine;
import dev.z8emu.platform.video.FrameBuffer;
import javax.swing.JFrame;

final class C64DesktopRunner {
    private C64DesktopRunner() {
    }

    static void open(C64Machine machine, DesktopLaunchConfig config) {
        DesktopWindowRunner.open(new Session(machine, config));
    }

    private static final class Session extends AbstractFrameDesktopSession {
        private final C64Machine machine;
        private final DesktopLaunchConfig config;
        private C64KeyboardController keyboardController;

        private Session(C64Machine machine, DesktopLaunchConfig config) {
            super(
                    createPanel(machine),
                    null,
                    "c64-audio",
                    machine.cpuClockHz(),
                    machine.frameTStates()
            );
            this.machine = machine;
            this.config = config;
        }

        @Override
        protected void attachMachine(JFrame frame) {
            keyboardController = C64KeyboardController.bind(
                    frame,
                    displayComponent(),
                    machine.board().keyboard()
            );
            bindKeyboardController(keyboardController);
        }

        @Override
        protected String statusTitle() {
            return "z8-emu " + machine.board().modelConfig().modelName()
                    + "  source=" + config.sourceLabel()
                    + "  cpu=6510";
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
        public String threadName() {
            return "c64-video-runner";
        }

        private static FrameDisplayPanel createPanel(C64Machine machine) {
            FrameBuffer initialFrame = machine.board().renderVideoFrame();
            return new FrameDisplayPanel(initialFrame.width(), initialFrame.height(), 2);
        }
    }
}
