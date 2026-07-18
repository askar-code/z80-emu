package dev.z8emu.machine.spectrum128k;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end CPU OUT -> Spectrum bus -> AY -> board mixer -> PCM regression. */
class Spectrum128AyPcmRegressionTest {
    private static final int PROGRAM_ADDRESS = 0x8000;

    @Test
    void cpuDrivenTwoNotePhraseHasStableMixedPcm() {
        Spectrum128Machine machine = new Spectrum128Machine(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE]
        );
        byte[] program = twoNotePhrase();
        for (int offset = 0; offset < program.length; offset++) {
            machine.board().memory().write(PROGRAM_ADDRESS + offset, program[offset] & 0xFF);
        }
        machine.cpu().registers().setPc(PROGRAM_ADDRESS);

        int instructions = 0;
        while (!machine.cpu().isHalted() && instructions < 20_000) {
            machine.runInstruction();
            instructions++;
        }

        assertTrue(machine.cpu().isHalted(), "the embedded music program must reach HALT");
        assertEquals(0x60, machine.board().ay().registerValue(0));
        assertEquals(0x01, machine.board().ay().registerValue(1));
        assertEquals(0x3E, machine.board().ay().registerValue(7));
        assertEquals(0x00, machine.board().ay().registerValue(8));

        long drainTailAt = machine.currentTState() + 35_000;
        while (machine.currentTState() < drainTailAt) {
            machine.runInstruction();
        }

        byte[] pcm = new byte[8_192];
        int copied = machine.board().audio().drainAudio(pcm, 0, pcm.length);
        assertEquals(3_196, copied, "phrase duration must produce a stable number of 44.1 kHz samples");
        assertEquals(0, sampleAt(pcm, 0), "register setup begins with silence");

        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        int transitions = 0;
        int previous = sampleAt(pcm, 0);
        for (int offset = 0; offset < copied; offset += Short.BYTES) {
            int sample = sampleAt(pcm, offset);
            minimum = Math.min(minimum, sample);
            maximum = Math.max(maximum, sample);
            if (sample != previous) {
                transitions++;
            }
            previous = sample;
        }
        assertTrue(minimum < -1_000, "AC-coupled tone must have a negative swing");
        assertTrue(maximum > 1_000, "AY channel A must produce a positive swing");
        assertTrue(transitions > 100, "two programmed tone periods must produce a non-flat waveform");

        CRC32 crc = new CRC32();
        crc.update(pcm, 0, copied);
        assertEquals(0xA1C662D5L, crc.getValue(),
                () -> "mixed PCM CRC=" + Long.toHexString(crc.getValue()));
    }

    private static byte[] twoNotePhrase() {
        ByteArrayOutputStream program = new ByteArrayOutputStream();
        emit(program, 0xF3); // DI
        writeAy(program, 0, 0x20); // tone A period 0x120
        writeAy(program, 1, 0x01);
        writeAy(program, 7, 0x3E); // tone A only, no noise
        writeAy(program, 8, 0x0F); // fixed maximum amplitude
        delay(program, 0x0600);
        writeAy(program, 0, 0x60); // second note, period 0x160
        delay(program, 0x0800);
        writeAy(program, 8, 0x00); // silence through the same CPU I/O path
        emit(program, 0x76); // HALT
        return program.toByteArray();
    }

    private static void writeAy(ByteArrayOutputStream program, int register, int value) {
        emit(program,
                0x01, 0xFD, 0xFF, // LD BC,FFFD (register select)
                0x3E, register,   // LD A,register
                0xED, 0x79,       // OUT (C),A
                0x01, 0xFD, 0xBF, // LD BC,BFFD (register data)
                0x3E, value,      // LD A,value
                0xED, 0x79        // OUT (C),A
        );
    }

    private static void delay(ByteArrayOutputStream program, int iterations) {
        emit(program,
                0x11, iterations & 0xFF, (iterations >>> 8) & 0xFF, // LD DE,iterations
                0x1B,       // loop: DEC DE
                0x7A,       // LD A,D
                0xB3,       // OR E
                0x20, 0xFB  // JR NZ,loop
        );
    }

    private static void emit(ByteArrayOutputStream target, int... bytes) {
        for (int value : bytes) {
            target.write(value & 0xFF);
        }
    }

    private static int sampleAt(byte[] pcm, int offset) {
        return (short) ((pcm[offset] & 0xFF) | (pcm[offset + 1] << 8));
    }
}
