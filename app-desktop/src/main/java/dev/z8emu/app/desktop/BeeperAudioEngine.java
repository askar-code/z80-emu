package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

final class BeeperAudioEngine implements AutoCloseable {
    private static final int CHUNK_BYTES = 512;
    private static final int LINE_BUFFER_BYTES = 2048;
    private static final long IDLE_WAIT_NANOS = 1_000_000L;

    private final BeeperDevice beeper;
    private final SourceDataLine line;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread worker;

    private BeeperAudioEngine(BeeperDevice beeper, SourceDataLine line) {
        this.beeper = beeper;
        this.line = line;
        this.worker = new Thread(this::runLoop, "spectrum-audio");
        this.worker.setDaemon(true);
    }

    static BeeperAudioEngine start(BeeperDevice beeper) throws LineUnavailableException {
        AudioFormat format = new AudioFormat(BeeperDevice.SAMPLE_RATE, 16, 1, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format, LINE_BUFFER_BYTES);
        line.start();

        BeeperAudioEngine engine = new BeeperAudioEngine(beeper, line);
        engine.worker.start();
        return engine;
    }

    private void runLoop() {
        byte[] chunk = new byte[CHUNK_BYTES];

        while (running.get()) {
            int copied = beeper.drainAudio(chunk, 0, chunk.length);
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
