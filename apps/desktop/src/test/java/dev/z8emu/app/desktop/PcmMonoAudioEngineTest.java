package dev.z8emu.app.desktop;

import dev.z8emu.platform.audio.PcmMonoSource;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PcmMonoAudioEngineTest {
    @Test
    void muteDiscardsTurboPcmAndResumesOnlyWithFreshRealtimeAudio() {
        FakeSource source = new FakeSource();
        FakeOutput output = new FakeOutput();

        try (PcmMonoAudioEngine engine = PcmMonoAudioEngine.start(source, output, "pcm-mute-test")) {
            byte[] beforeTurbo = samples(0x10, 64);
            source.offer(beforeTurbo);
            output.awaitAcceptedBytes(beforeTurbo.length);
            assertArrayEquals(beforeTurbo, output.queuedBytes());

            engine.setMuted(true);
            engine.setMuted(true);
            assertEquals(0, output.queuedByteCount(), "entering mute must flush the realtime line");
            assertEquals(1, output.flushCount(), "repeated session-state synchronization is idempotent");
            int acceptedBeforeTurbo = output.acceptedByteCount();

            source.offer(samples(0x40, 4_096));
            source.awaitEmpty();
            assertEquals(acceptedBeforeTurbo, output.acceptedByteCount(), "turbo PCM must be discarded");
            assertEquals(0, output.queuedByteCount());

            source.offer(samples(0x70, 1_024));
            engine.setMuted(false);
            engine.setMuted(false);
            assertEquals(0, source.availableBytes(), "unmute must discard the last turbo backlog");
            assertEquals(0, output.queuedByteCount(), "unmute starts from an empty output line");

            byte[] afterTurbo = samples(0x20, 96);
            source.offer(afterTurbo);
            output.awaitAcceptedBytes(acceptedBeforeTurbo + afterTurbo.length);

            assertArrayEquals(afterTurbo, output.queuedBytes());
            assertEquals(acceptedBeforeTurbo + afterTurbo.length, output.acceptedByteCount());
            assertEquals(2, output.flushCount(), "both transition edges must flush queued audio");
            assertTrue(output.maxWriteLength() <= 512, "engine writes must stay chunk-bounded");
        }
    }

    @Test
    void ordinaryUnmutedAudioDoesNotFlushOrDiscardSamples() {
        FakeSource source = new FakeSource();
        FakeOutput output = new FakeOutput();

        try (PcmMonoAudioEngine engine = PcmMonoAudioEngine.start(source, output, "pcm-normal-test")) {
            engine.setMuted(false);
            byte[] normal = samples(0x30, 1_500);
            source.offer(normal);
            output.awaitAcceptedBytes(normal.length);
            engine.setMuted(false);

            assertArrayEquals(normal, output.queuedBytes());
            assertEquals(0, output.flushCount());
            assertTrue(output.maxWriteLength() <= 512);
        }
    }

    private static byte[] samples(int start, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (start + i);
        }
        return bytes;
    }

    private static final class FakeSource implements PcmMonoSource {
        private final ArrayDeque<Byte> bytes = new ArrayDeque<>();

        @Override
        public int sampleRate() {
            return DEFAULT_SAMPLE_RATE;
        }

        @Override
        public synchronized int drainAudio(byte[] target, int offset, int length) {
            int copied = Math.min(length, bytes.size());
            for (int i = 0; i < copied; i++) {
                target[offset + i] = bytes.removeFirst();
            }
            if (copied > 0) {
                notifyAll();
            }
            return copied;
        }

        synchronized void offer(byte[] offered) {
            for (byte value : offered) {
                bytes.addLast(value);
            }
            notifyAll();
        }

        synchronized int availableBytes() {
            return bytes.size();
        }

        synchronized void awaitEmpty() {
            await(() -> bytes.isEmpty(), "PCM source was not drained while muted");
        }

        private void await(Check check, String failureMessage) {
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (!check.matches()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    fail(failureMessage);
                }
                try {
                    wait(Math.max(1L, Math.min(50L, Duration.ofNanos(remaining).toMillis())));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    fail("Interrupted while waiting for PCM state", interrupted);
                }
            }
        }
    }

    private static final class FakeOutput implements PcmMonoAudioEngine.Output {
        private final ByteArrayOutputStream accepted = new ByteArrayOutputStream();
        private final ByteArrayOutputStream queued = new ByteArrayOutputStream();
        private int flushCount;
        private int maxWriteLength;

        @Override
        public synchronized int write(byte[] data, int offset, int length) {
            accepted.write(data, offset, length);
            queued.write(data, offset, length);
            maxWriteLength = Math.max(maxWriteLength, length);
            notifyAll();
            return length;
        }

        @Override
        public void stop() {
        }

        @Override
        public synchronized void flush() {
            queued.reset();
            flushCount++;
        }

        @Override
        public void close() {
        }

        synchronized void awaitAcceptedBytes(int expected) {
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (accepted.size() < expected) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    fail("Expected at least " + expected + " accepted PCM bytes, got " + accepted.size());
                }
                try {
                    wait(Math.max(1L, Math.min(50L, Duration.ofNanos(remaining).toMillis())));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    fail("Interrupted while waiting for PCM output", interrupted);
                }
            }
        }

        synchronized byte[] queuedBytes() {
            return queued.toByteArray();
        }

        synchronized int queuedByteCount() {
            return queued.size();
        }

        synchronized int acceptedByteCount() {
            return accepted.size();
        }

        synchronized int flushCount() {
            return flushCount;
        }

        synchronized int maxWriteLength() {
            return maxWriteLength;
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean matches();
    }
}
