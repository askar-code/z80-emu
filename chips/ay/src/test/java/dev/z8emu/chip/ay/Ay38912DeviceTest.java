package dev.z8emu.chip.ay;

import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Ay38912DeviceTest {
    private static final long SOURCE_CLOCK_HZ = 3_500_000L;
    private static final int VECTOR_TSTATES = 350_000;
    private static final long[] PCM_VECTOR_CRCS = {
            234_175_172L,
            3_329_800_152L,
            88_664_775L,
            4_130_271_722L, 4_130_271_722L, 4_130_271_722L, 4_130_271_722L,
            3_368_288_555L, 3_368_288_555L, 3_368_288_555L, 3_368_288_555L,
            302_733_965L,
            4_130_271_722L,
            263_481_288L,
            1_758_547_930L,
            3_212_411_199L,
            3_133_592_553L,
            3_511_764_888L,
            3_368_288_555L,
    };
    private static final int[] FIXED_AMPLITUDE_LEVELS = {
            0, 32, 48, 72,
            108, 162, 243, 364,
            546, 819, 1_228, 1_842,
            2_763, 4_145, 6_217, 9_326,
    };

    @Test
    void masksRegistersAndRestartsEnvelopeThroughTheSelectedRegisterInterface() {
        Ay38912Device ay = new Ay38912Device(SOURCE_CLOCK_HZ);
        ay.reset();

        writeRegister(ay, 1, 0xFF);
        writeRegister(ay, 6, 0xFF);
        writeRegister(ay, 8, 0xFF);
        writeRegister(ay, 13, 0xFF);
        ay.selectRegister(0x21);

        assertEquals(0x0F, ay.registerValue(1));
        assertEquals(0x1F, ay.registerValue(6));
        assertEquals(0x1F, ay.registerValue(8));
        assertEquals(0x0F, ay.registerValue(13));
        assertEquals(1, ay.selectedRegister());
    }

    @Test
    void toneNoiseMixerAndEveryEnvelopeShapeHaveDeterministicPcmVectors() {
        long[] actual = new long[19];
        actual[0] = toneVector();
        actual[1] = noiseVector();
        actual[2] = mixerConstantVector();
        for (int shape = 0; shape < 16; shape++) {
            actual[3 + shape] = envelopeVector(shape);
        }

        assertArrayEquals(PCM_VECTOR_CRCS, actual);
    }

    @Test
    void fixedAmplitudeCurveProducesTheExpectedMonotonicFirstSampleLevels() {
        int[] actual = new int[16];
        for (int amplitude = 0; amplitude < actual.length; amplitude++) {
            Ay38912Device ay = freshDevice();
            writeRegister(ay, 7, 0x3F);
            writeRegister(ay, 8, amplitude);
            ay.onTStatesElapsed(80);
            byte[] pcm = new byte[2];
            assertEquals(2, ay.drainAudio(pcm, 0, pcm.length));
            actual[amplitude] = (short) (Byte.toUnsignedInt(pcm[0]) | (pcm[1] << 8));
        }

        assertArrayEquals(FIXED_AMPLITUDE_LEVELS, actual);
    }

    private static long toneVector() {
        Ay38912Device ay = freshDevice();
        writeRegister(ay, 0, 0x10);
        writeRegister(ay, 1, 0x00);
        writeRegister(ay, 7, 0x3E);
        writeRegister(ay, 8, 0x0F);
        return pcmCrc(ay);
    }

    private static long noiseVector() {
        Ay38912Device ay = freshDevice();
        writeRegister(ay, 6, 0x01);
        writeRegister(ay, 7, 0x37);
        writeRegister(ay, 8, 0x0F);
        return pcmCrc(ay);
    }

    private static long mixerConstantVector() {
        Ay38912Device ay = freshDevice();
        writeRegister(ay, 7, 0x3F);
        writeRegister(ay, 8, 0x0F);
        return pcmCrc(ay);
    }

    private static long envelopeVector(int shape) {
        Ay38912Device ay = freshDevice();
        writeRegister(ay, 7, 0x3F);
        writeRegister(ay, 8, 0x10);
        writeRegister(ay, 11, 0x01);
        writeRegister(ay, 12, 0x00);
        writeRegister(ay, 13, shape);
        return pcmCrc(ay);
    }

    private static Ay38912Device freshDevice() {
        Ay38912Device ay = new Ay38912Device(SOURCE_CLOCK_HZ);
        ay.reset();
        return ay;
    }

    private static void writeRegister(Ay38912Device ay, int register, int value) {
        ay.selectRegister(register);
        ay.writeSelectedRegister(value);
    }

    private static long pcmCrc(Ay38912Device ay) {
        ay.onTStatesElapsed(VECTOR_TSTATES);
        byte[] pcm = new byte[ay.availableAudioBytes()];
        assertEquals(pcm.length, ay.drainAudio(pcm, 0, pcm.length));
        CRC32 crc = new CRC32();
        crc.update(pcm);
        return crc.getValue();
    }
}
