package dev.z8emu.app.desktop;

import dev.z8emu.platform.audio.PcmMonoSource;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

final class PcmMonoAudioEngine implements AutoCloseable {
    private static final int CHUNK_BYTES = 512;
    private static final int LINE_BUFFER_BYTES = 2048;
    private static final long IDLE_WAIT_NANOS = 1_000_000L;

    private final PcmMonoSource source;
    private final Output output;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Object outputLock = new Object();
    private final Thread worker;
    private boolean muted;

    private PcmMonoAudioEngine(PcmMonoSource source, Output output, String threadName) {
        this.source = Objects.requireNonNull(source, "source");
        this.output = Objects.requireNonNull(output, "output");
        this.worker = new Thread(this::runLoop, threadName);
        this.worker.setDaemon(true);
    }

    static PcmMonoAudioEngine start(PcmMonoSource source, String threadName) throws LineUnavailableException {
        AudioFormat format = new AudioFormat(source.sampleRate(), 16, 1, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format, LINE_BUFFER_BYTES);
        line.start();

        return start(source, new SourceDataLineOutput(line), threadName);
    }

    static PcmMonoAudioEngine start(PcmMonoSource source, Output output, String threadName) {
        PcmMonoAudioEngine engine = new PcmMonoAudioEngine(source, output, threadName);
        engine.worker.start();
        return engine;
    }

    /**
     * Discards all PCM while muted. Both transition edges clear already queued
     * samples so returning to realtime cannot replay turbo-generated audio.
     */
    void setMuted(boolean muted) {
        synchronized (outputLock) {
            if (!running.get() || this.muted == muted) {
                return;
            }
            if (muted) {
                this.muted = true;
                output.flush();
                discardSourceAudio();
            } else {
                discardSourceAudio();
                output.flush();
                this.muted = false;
            }
        }
        LockSupport.unpark(worker);
    }

    private void runLoop() {
        byte[] chunk = new byte[CHUNK_BYTES];

        while (running.get()) {
            int copied;
            synchronized (outputLock) {
                if (!running.get()) {
                    break;
                }
                copied = source.drainAudio(chunk, 0, chunk.length);
                if (copied > 0 && !muted) {
                    output.write(chunk, 0, copied);
                }
            }
            if (copied <= 0) {
                LockSupport.parkNanos(IDLE_WAIT_NANOS);
            }
        }
    }

    private void discardSourceAudio() {
        byte[] discarded = new byte[CHUNK_BYTES];
        while (source.drainAudio(discarded, 0, discarded.length) > 0) {
            // Drain to the current source edge. Clocked PCM sources are bounded,
            // and Spectrum produces no new samples while its runner changes mode.
        }
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        LockSupport.unpark(worker);
        try {
            worker.join(500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        synchronized (outputLock) {
            output.stop();
            output.flush();
            output.close();
        }
    }

    interface Output extends AutoCloseable {
        int write(byte[] data, int offset, int length);

        void stop();

        void flush();

        @Override
        void close();
    }

    private record SourceDataLineOutput(SourceDataLine line) implements Output {
        private SourceDataLineOutput {
            Objects.requireNonNull(line, "line");
        }

        @Override
        public int write(byte[] data, int offset, int length) {
            return line.write(data, offset, length);
        }

        @Override
        public void stop() {
            line.stop();
        }

        @Override
        public void flush() {
            line.flush();
        }

        @Override
        public void close() {
            line.close();
        }
    }
}
