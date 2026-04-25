package dev.z8emu.platform.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClockedPcmMonoSourceTest {
    @Test
    void keepsFractionalClockRemainderBetweenTicks() {
        TestSource source = new TestSource(10, 4, 8, (short) 0x1111);

        source.onTStatesElapsed(2);
        assertEquals(0, source.availableAudioBytes());

        source.onTStatesElapsed(1);
        assertEquals(2, source.availableAudioBytes());
    }

    @Test
    void drainsLittleEndianPcm16Samples() {
        TestSource source = new TestSource(1, 1, 4, (short) 0x1234, (short) 0xFEDC);
        byte[] target = new byte[4];

        source.onTStatesElapsed(2);

        assertEquals(4, source.drainAudio(target, 0, target.length));
        assertArrayEquals(new byte[]{0x34, 0x12, (byte) 0xDC, (byte) 0xFE}, target);
    }

    @Test
    void discardsOldestWholeSamplesWhenBufferIsFull() {
        TestSource source = new TestSource(1, 1, 4, (short) 0x1111, (short) 0x2222, (short) 0x3333);
        byte[] target = new byte[4];

        source.onTStatesElapsed(3);

        assertEquals(4, source.drainAudio(target, 0, target.length));
        assertArrayEquals(new byte[]{0x22, 0x22, 0x33, 0x33}, target);
    }

    @Test
    void resetPcmAudioClearsBufferedBytesAndRemainder() {
        TestSource source = new TestSource(10, 4, 8, (short) 0x1111);

        source.onTStatesElapsed(2);
        source.reset();
        source.onTStatesElapsed(1);

        assertEquals(0, source.availableAudioBytes());
    }

    @Test
    void rejectsInvalidClockAndBufferSettings() {
        assertThrows(IllegalArgumentException.class, () -> new TestSource(0, 4, 8, (short) 0));
        assertThrows(IllegalArgumentException.class, () -> new TestSource(10, 0, 8, (short) 0));
        assertThrows(IllegalArgumentException.class, () -> new TestSource(10, 4, 1, (short) 0));
        assertThrows(IllegalArgumentException.class, () -> new TestSource(10, 4, 3, (short) 0));
    }

    private static final class TestSource extends ClockedPcmMonoSource {
        private final short[] samples;
        private int sampleIndex;

        private TestSource(long sourceClockHz, int sampleRate, int bufferCapacityBytes, short... samples) {
            super(sourceClockHz, sampleRate, bufferCapacityBytes);
            this.samples = samples;
        }

        @Override
        public synchronized void reset() {
            sampleIndex = 0;
            resetPcmAudio();
        }

        @Override
        protected short nextPcmSample() {
            if (sampleIndex >= samples.length) {
                return 0;
            }
            return samples[sampleIndex++];
        }
    }
}
