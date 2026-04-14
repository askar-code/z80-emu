package dev.z8emu.app.desktop;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import javax.swing.JFrame;

final class DesktopWindowRunner {
    private DesktopWindowRunner() {
    }

    static void open(DesktopMachineSession session) {
        JFrame frame = new JFrame();
        session.attachToFrame(frame);
        DesktopRunnerSupport.showFrame(frame, session.component());

        AtomicBoolean running = new AtomicBoolean(true);
        DesktopRunnerSupport.attachWindowLifecycle(
                frame,
                running,
                session::close,
                session::onFocusGained,
                session::onFocusLost
        );

        session.presentFrame();
        frame.setTitle(session.initialTitle());

        Thread runner = new Thread(() -> runLoop(session, frame, running), session.threadName());
        runner.setDaemon(true);
        runner.start();
    }

    private static void runLoop(
            DesktopMachineSession session,
            JFrame frame,
            AtomicBoolean running
    ) {
        Throwable failure = null;
        long nextFrameDeadline = System.nanoTime();
        long frameDurationNanos = session.frameDurationNanos();

        while (running.get()) {
            try {
                if (failure == null) {
                    session.runSlice();
                }
            } catch (Throwable t) {
                failure = t;
                session.handleFailure(t);
            }

            session.presentFrame();
            Throwable currentFailure = failure;
            if (!session.turboActive()) {
                DesktopRunnerSupport.invokeUiUpdate(
                        session.component(),
                        frame,
                        () -> frame.setTitle(session.title(currentFailure))
                );
            } else {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    session.component().repaint();
                    frame.setTitle(session.title(currentFailure));
                });
            }

            if (failure != null) {
                return;
            }

            if (!session.turboActive()) {
                nextFrameDeadline += frameDurationNanos;
                long remaining = nextFrameDeadline - System.nanoTime();
                if (remaining > 0) {
                    LockSupport.parkNanos(remaining);
                } else if (remaining < -frameDurationNanos) {
                    nextFrameDeadline = System.nanoTime();
                }
            } else {
                nextFrameDeadline = System.nanoTime();
            }
        }
    }
}
