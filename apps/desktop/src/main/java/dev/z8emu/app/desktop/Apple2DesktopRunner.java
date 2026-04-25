package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.Apple2Machine;
import dev.z8emu.machine.apple2.Apple2Memory;
import dev.z8emu.platform.video.FrameBuffer;
import javax.swing.JFrame;

final class Apple2DesktopRunner {
    private Apple2DesktopRunner() {
    }

    static void open(Apple2Machine machine, DesktopLaunchConfig config) {
        DesktopWindowRunner.open(new Session(machine, config));
    }

    private static final class Session extends AbstractFrameDesktopSession<FrameDisplayPanel> {
        private final Apple2Machine machine;
        private final DesktopLaunchConfig config;
        private Apple2KeyboardController keyboardController;

        private Session(Apple2Machine machine, DesktopLaunchConfig config) {
            super(
                    createPanel(machine),
                    machine.board().audio(),
                    "apple2-audio",
                    machine.cpuClockHz(),
                    machine.frameTStates()
            );
            this.machine = machine;
            this.config = config;
        }

        @Override
        protected void attachMachine(JFrame frame) {
            keyboardController = Apple2KeyboardController.bind(frame, displayComponent(), machine.board().keyboard());
        }

        @Override
        public String title(Throwable failure) {
            String base = "z8-emu " + machine.board().modelName();
            String status = "source=" + config.sourceLabel()
                    + "  pc=0x" + hex16(machine.cpu().registers().pc())
                    + "  t=" + machine.currentTState()
                    + "  text=0x" + hex16(Apple2Memory.TEXT_PAGE_1_START)
                    + "  key=" + keyboardController.lastEvent()
                    + "  cpu=6502";

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
            return "apple2-video-runner";
        }

        private static FrameDisplayPanel createPanel(Apple2Machine machine) {
            FrameBuffer initialFrame = machine.board().renderVideoFrame();
            return new FrameDisplayPanel(initialFrame.width(), initialFrame.height(), 2);
        }

        private static String hex16(int value) {
            return "%04X".formatted(value & 0xFFFF);
        }
    }
}
