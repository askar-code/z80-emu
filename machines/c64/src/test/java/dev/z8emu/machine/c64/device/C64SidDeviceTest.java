package dev.z8emu.machine.c64.device;

import dev.z8emu.machine.c64.C64ModelConfig;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64SidDeviceTest {
    private static final long PAL_CLOCK_HZ = C64ModelConfig.pal().cpuClockHz();
    private static final int VOICE_3_BASE = 0x0E;
    private static final int CONTROL_SAW = 0x20;
    private static final int CONTROL_PULSE = 0x40;
    private static final int CONTROL_NOISE = 0x80;
    private static final int GATE = 0x01;
    private static final int TEST = 0x08;
    private static final int CONTROL_TRIANGLE = 0x10;
    private static final long SAW_PCM_CRC = 0x052A2733L;
    private static final long NOISE_PCM_CRC = 0x5576D685L;
    private static final long PULSE_DECAY_PCM_CRC = 0x40AADD4CL;

    @Test
    void registerReadSemanticsAndInertFilterWritesAreFrozen() {
        C64SidDevice sid = newSid();
        for (int register = 0; register <= 0x18; register++) {
            sid.writeRegister(register, 0xFF);
            assertEquals(0x00, sid.readRegister(register), "write-only register " + register);
        }

        assertEquals(0xFF, sid.readRegister(0x19));
        assertEquals(0xFF, sid.readRegister(0x1A));
        assertEquals(0xFF, sid.readRegister(0x1D));
        assertEquals(0xFF, sid.readRegister(0x1E));
        assertEquals(0xFF, sid.readRegister(0x1F));

        C64SidDevice baseline = configuredSaw(0x0F);
        C64SidDevice filtered = configuredSaw(0xFF);
        filtered.writeRegister(0x15, 0xFF);
        filtered.writeRegister(0x16, 0xFF);
        filtered.writeRegister(0x17, 0xFF);
        baseline.onTStatesElapsed(10_000);
        filtered.onTStatesElapsed(10_000);

        assertArrayEquals(drain(baseline, 512), drain(filtered, 512));
    }

    @Test
    void pulseWidthHighRegisterIsMaskedToTwelveBitsBySynthesis() {
        C64SidDevice masked = configuredPulse(0x0F);
        C64SidDevice highBitsSet = configuredPulse(0xFF);
        masked.onTStatesElapsed(10_000);
        highBitsSet.onTStatesElapsed(10_000);

        assertArrayEquals(drain(masked, 512), drain(highBitsSet, 512));
    }

    @Test
    void sawScriptHasDeterministicPcmCrc() {
        assertEquals(SAW_PCM_CRC, scriptedCrc(CONTROL_SAW));
    }

    @Test
    void noiseScriptHasDeterministicPcmCrc() {
        assertEquals(NOISE_PCM_CRC, scriptedCrc(CONTROL_NOISE));
    }

    @Test
    void sawToneProducesNonZeroTransitioningSamples() {
        C64SidDevice sid = configuredSaw(0x0F);
        sid.onTStatesElapsed(100_000);
        byte[] pcm = new byte[4_096];
        int copied = sid.drainAudio(pcm, 0, pcm.length);

        assertTrue(copied > 0);
        assertTrue(hasNonZeroSample(pcm, copied));
        assertTrue(hasSampleTransition(pcm, copied));
    }

    @Test
    void voiceThreeEnvelopeAttacksSustainsAndReleases() {
        C64SidDevice sid = newSid();
        writeFrequency(sid);
        sid.writeRegister(VOICE_3_BASE + 5, 0x00);
        sid.writeRegister(VOICE_3_BASE + 6, 0x80);
        sid.writeRegister(VOICE_3_BASE + 4, CONTROL_SAW | GATE);

        sid.onTStatesElapsed(10_000);

        assertEquals(0x88, sid.readRegister(0x1C));

        sid.writeRegister(VOICE_3_BASE + 4, CONTROL_SAW);
        sid.onTStatesElapsed(10_000);

        assertEquals(0x00, sid.readRegister(0x1C));
    }

    @Test
    void oscillatorThreeReadsWaveformAndTestZerosSaw() {
        C64SidDevice sid = newSid();
        writeFrequency(sid);
        sid.writeRegister(VOICE_3_BASE + 4, CONTROL_SAW);
        sid.onTStatesElapsed(1_000);
        int first = sid.readRegister(0x1B);
        sid.onTStatesElapsed(1_000);

        assertNotEquals(first, sid.readRegister(0x1B));

        sid.writeRegister(VOICE_3_BASE + 4, CONTROL_SAW | TEST);

        assertEquals(0x00, sid.readRegister(0x1B));
    }

    @Test
    void noiseRemainsNonConstantAfterEnvelopeParksAtSustain() {
        C64SidDevice sid = configuredVoiceThree(CONTROL_NOISE, 0x0F);
        sid.onTStatesElapsed(10_000);
        drain(sid, sid.availableAudioBytes());
        assertEquals(0xFF, sid.readRegister(0x1C));

        sid.onTStatesElapsed(20_000);
        byte[] pcm = drain(sid, 512);

        assertTrue(hasSampleTransition(pcm, pcm.length));
    }

    @Test
    void volumeZeroSettlesToSilenceAfterDcBlockerTail() {
        C64SidDevice sid = configuredSaw(0x0F);
        sid.onTStatesElapsed(50_000);
        sid.writeRegister(0x18, 0x00);
        sid.onTStatesElapsed(200_000);
        byte[] discarded = new byte[8_192];
        assertEquals(discarded.length, sid.drainAudio(discarded, 0, discarded.length));
        byte[] tail = new byte[512];
        assertEquals(tail.length, sid.drainAudio(tail, 0, tail.length));

        for (int offset = 0; offset < tail.length; offset += 2) {
            assertTrue(Math.abs(decodeSample(tail, offset)) <= 1);
        }
    }

    @Test
    void pulseDecayScriptHasDeterministicPcmCrc() {
        C64SidDevice sid = newSid();
        writeFrequency(sid);
        sid.writeRegister(VOICE_3_BASE + 2, 0x00);
        sid.writeRegister(VOICE_3_BASE + 3, 0x08);
        sid.writeRegister(VOICE_3_BASE + 5, 0x04);
        sid.writeRegister(VOICE_3_BASE + 6, 0x00);
        sid.writeRegister(0x18, 0x0F);
        sid.writeRegister(VOICE_3_BASE + 4, CONTROL_PULSE | GATE);
        sid.onTStatesElapsed(100_000);
        byte[] pcm = new byte[4_096];
        assertEquals(pcm.length, sid.drainAudio(pcm, 0, pcm.length));
        CRC32 crc = new CRC32();
        crc.update(pcm);
        assertEquals(PULSE_DECAY_PCM_CRC, crc.getValue());
    }

    @Test
    void oscillatorThreeTriangleReadbackDescendsInSecondHalfPeriod() {
        C64SidDevice sid = newSid();
        writeFrequency(sid);
        sid.writeRegister(VOICE_3_BASE + 4, CONTROL_TRIANGLE);

        boolean doubleDescent = false;
        int previous = sid.readRegister(0x1B);
        int previousDelta = 0;
        for (int step = 0; step < 200 && !doubleDescent; step++) {
            sid.onTStatesElapsed(100);
            int current = sid.readRegister(0x1B);
            int delta = current - previous;
            if (delta < 0 && previousDelta < 0) {
                doubleDescent = true;
            }
            previousDelta = delta;
            previous = current;
        }

        assertTrue(doubleDescent);
    }

    @Test
    void envelopeAttackRampIsObservableMidFlight() {
        C64SidDevice sid = configuredVoiceThree(CONTROL_SAW, 0x0F);
        sid.onTStatesElapsed(1_000);
        int level = sid.readRegister(0x1C);
        assertTrue(level > 0 && level < 0xFF);
    }

    private static long scriptedCrc(int waveform) {
        C64SidDevice sid = configuredVoiceThree(waveform, 0x0F);
        sid.onTStatesElapsed(100_000);
        byte[] pcm = new byte[4_096];
        assertEquals(pcm.length, sid.drainAudio(pcm, 0, pcm.length));
        CRC32 crc = new CRC32();
        crc.update(pcm);
        return crc.getValue();
    }

    private static C64SidDevice configuredSaw(int modeAndVolume) {
        return configuredVoiceThree(CONTROL_SAW, modeAndVolume);
    }

    private static C64SidDevice configuredVoiceThree(int waveform, int modeAndVolume) {
        C64SidDevice sid = newSid();
        writeFrequency(sid);
        sid.writeRegister(VOICE_3_BASE + 5, 0x00);
        sid.writeRegister(VOICE_3_BASE + 6, 0xF0);
        sid.writeRegister(0x18, modeAndVolume);
        sid.writeRegister(VOICE_3_BASE + 4, waveform | GATE);
        return sid;
    }

    private static C64SidDevice configuredPulse(int pulseHigh) {
        C64SidDevice sid = newSid();
        writeFrequency(sid);
        sid.writeRegister(VOICE_3_BASE + 2, 0x00);
        sid.writeRegister(VOICE_3_BASE + 3, pulseHigh);
        sid.writeRegister(VOICE_3_BASE + 5, 0x00);
        sid.writeRegister(VOICE_3_BASE + 6, 0xF0);
        sid.writeRegister(0x18, 0x0F);
        sid.writeRegister(VOICE_3_BASE + 4, CONTROL_PULSE | GATE);
        return sid;
    }

    private static void writeFrequency(C64SidDevice sid) {
        sid.writeRegister(VOICE_3_BASE, 0x45);
        sid.writeRegister(VOICE_3_BASE + 1, 0x1D);
    }

    private static C64SidDevice newSid() {
        C64SidDevice sid = new C64SidDevice(PAL_CLOCK_HZ);
        sid.reset();
        return sid;
    }

    private static byte[] drain(C64SidDevice sid, int length) {
        byte[] pcm = new byte[length];
        assertEquals(length, sid.drainAudio(pcm, 0, pcm.length));
        return pcm;
    }

    private static boolean hasNonZeroSample(byte[] pcm, int length) {
        for (int i = 0; i + 1 < length; i += 2) {
            if (decodeSample(pcm, i) != 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSampleTransition(byte[] pcm, int length) {
        if (length < 4) {
            return false;
        }

        int previous = decodeSample(pcm, 0);
        for (int i = 2; i + 1 < length; i += 2) {
            int current = decodeSample(pcm, i);
            if (current != previous) {
                return true;
            }
            previous = current;
        }
        return false;
    }

    private static int decodeSample(byte[] pcm, int offset) {
        return (short) (((pcm[offset + 1] & 0xFF) << 8) | (pcm[offset] & 0xFF));
    }
}
