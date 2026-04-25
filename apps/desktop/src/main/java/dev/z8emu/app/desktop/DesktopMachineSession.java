package dev.z8emu.app.desktop;

import javax.swing.JComponent;

interface DesktopMachineSession {
    void attachToFrame(javax.swing.JFrame frame);

    JComponent component();

    String initialTitle();

    String title(Throwable failure);

    long frameDurationNanos();

    boolean turboActive();

    void runSlice();

    void presentFrame();

    void onFocusGained();

    void onFocusLost();

    void close();

    void handleFailure(Throwable failure);

    String threadName();
}
