package dev.z8emu.app.desktop;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

final class DesktopRunnerSupport {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private DesktopRunnerSupport() {
    }

    static long frameDurationNanos(long cpuClockHz, int frameTStates) {
        return (frameTStates * NANOS_PER_SECOND) / cpuClockHz;
    }

    static void showFrame(JFrame frame, JComponent component) {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(component);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
        component.requestFocusInWindow();
    }

    static void attachWindowLifecycle(
            JFrame frame,
            AtomicBoolean running,
            Runnable onClose,
            Runnable onFocusGained,
            Runnable onFocusLost
    ) {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                running.set(false);
                onClose.run();
            }
        });
        frame.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                onFocusGained.run();
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                onFocusLost.run();
            }
        });
    }

    static void invokeUiUpdate(
            JComponent component,
            JFrame frame,
            Runnable updateAction
    ) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                updateAction.run();
                component.paintImmediately(0, 0, component.getWidth(), component.getHeight());
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException invocationFailure) {
            throw new IllegalStateException("Failed to update Swing UI", invocationFailure.getCause());
        }
    }
}
