package dev.z8emu.app.desktop;

import dev.z8emu.machine.radio86rk.device.Radio86AudioDevice;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

final class Radio86AudioEngine implements AutoCloseable {
    private static final int CHUNK_BYTES = 512;
    private static final int LINE_BUFFER_BYTES = 2048;
    private static final long IDLE_WAIT_NANOS = 1_000_000L;

    private final Radio86AudioDevice audio;
    private final SourceDataLine line;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread worker;

    private Radio86AudioEngine(Radio86AudioDevice audio, SourceDataLine line) {
        this.audio = audio;
        this.line = line;
        this.worker = new Thread(this::runLoop, "radio86-audio");
        this.worker.setDaemon(true);
    }

    static Radio86AudioEngine start(Radio86AudioDevice audio) throws LineUnavailableException {
        AudioFormat format = new AudioFormat(Radio86AudioDevice.SAMPLE_RATE, 16, 1, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format, LINE_BUFFER_BYTES);
        line.start();

        Radio86AudioEngine engine = new Radio86AudioEngine(audio, line);
        engine.worker.start();
        return engine;
    }

    private void runLoop() {
        byte[] chunk = new byte[CHUNK_BYTES];
        while (running.get()) {
            int copied = audio.drainAudio(chunk, 0, chunk.length);
            if (copied > 0) {
                line.write(chunk, 0, copied);
            } else {
                LockSupport.parkNanos(IDLE_WAIT_NANOS);
            }
        }
    }

    @Override
    public void close() {
        running.set(false);
        try {
            worker.join(500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        line.stop();
        line.flush();
        line.close();
    }
}
