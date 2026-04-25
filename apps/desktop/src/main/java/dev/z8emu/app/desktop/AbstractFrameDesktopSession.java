package dev.z8emu.app.desktop;

import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.machine.Machine;
import dev.z8emu.platform.video.FrameBuffer;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.JComponent;
import javax.swing.JFrame;

abstract class AbstractFrameDesktopSession<P extends JComponent> implements DesktopMachineSession {
    private final P component;
    private final PcmMonoSource audioSource;
    private final String audioThreadName;
    private final long frameDurationNanos;
    private PcmMonoAudioEngine audioEngine;

    protected AbstractFrameDesktopSession(
            P component,
            PcmMonoSource audioSource,
            String audioThreadName,
            long cpuClockHz,
            int frameTStates
    ) {
        this.component = component;
        this.audioSource = audioSource;
        this.audioThreadName = audioThreadName;
        this.frameDurationNanos = DesktopRunnerSupport.frameDurationNanos(cpuClockHz, frameTStates);
    }

    @Override
    public final void attachToFrame(JFrame frame) {
        attachMachine(frame);
        audioEngine = tryStartAudio(audioSource, audioThreadName);
    }

    @Override
    public final JComponent component() {
        return component;
    }

    @Override
    public String initialTitle() {
        return title(null);
    }

    @Override
    public final long frameDurationNanos() {
        return frameDurationNanos;
    }

    @Override
    public boolean turboActive() {
        return false;
    }

    @Override
    public final void presentFrame() {
        presentFrameBuffer(component, renderVideoFrame());
    }

    @Override
    public final void onFocusGained() {
        component.requestFocusInWindow();
    }

    @Override
    public final void onFocusLost() {
        releaseInputOnFocusLost();
    }

    @Override
    public final void close() {
        closeMachineResources();
        if (audioEngine != null) {
            audioEngine.close();
        }
    }

    @Override
    public void handleFailure(Throwable failure) {
        failure.printStackTrace(System.err);
    }

    protected final P displayComponent() {
        return component;
    }

    protected void attachMachine(JFrame frame) {
    }

    protected void releaseInputOnFocusLost() {
    }

    protected void closeMachineResources() {
    }

    protected abstract FrameBuffer renderVideoFrame();

    protected abstract void presentFrameBuffer(P component, FrameBuffer frame);

    protected final void runUntilTState(Machine machine, long targetTState) {
        while (machine.currentTState() < targetTState) {
            machine.runInstruction();
        }
    }

    protected final long nextFrameBoundaryTState(Machine machine, int frameTStates) {
        long current = machine.currentTState();
        long remainder = Math.floorMod(current, frameTStates);
        return remainder == 0
                ? current + frameTStates
                : current + frameTStates - remainder;
    }

    private static PcmMonoAudioEngine tryStartAudio(PcmMonoSource source, String threadName) {
        try {
            return PcmMonoAudioEngine.start(source, threadName);
        } catch (LineUnavailableException unavailable) {
            System.err.println("Audio disabled: " + unavailable.getMessage());
            return null;
        }
    }
}
