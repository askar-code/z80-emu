package dev.z8emu.machine.spectrum128k;

import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Spectrum128MachineTest {
    @Test
    void defaultsToRom0Bank0AndScreenBank5() {
        byte[] rom0 = new byte[Spectrum128Machine.ROM_BANK_SIZE];
        byte[] rom1 = new byte[Spectrum128Machine.ROM_BANK_SIZE];
        rom0[0] = 0x11;
        rom1[0] = 0x22;

        Spectrum128Machine machine = new Spectrum128Machine(rom0, rom1);

        assertEquals(0x11, machine.board().memory().read(0x0000));
        assertEquals(0, machine.board().machineState().selectedRomIndex());
        assertEquals(0, machine.board().machineState().topRamBankIndex());
        assertEquals(5, machine.board().machineState().activeScreenBankIndex());
    }

    @Test
    void writeTo7ffdPagesRomTopBankAndShadowScreen() {
        byte[] rom0 = new byte[Spectrum128Machine.ROM_BANK_SIZE];
        byte[] rom1 = new byte[Spectrum128Machine.ROM_BANK_SIZE];
        rom0[0] = 0x11;
        rom1[0] = 0x22;

        Spectrum128Machine machine = new Spectrum128Machine(rom0, rom1);
        machine.board().cpuBus().writePort(0x7FFD, 0x1F);

        assertEquals(0x22, machine.board().memory().read(0x0000));
        assertEquals(1, machine.board().machineState().selectedRomIndex());
        assertEquals(7, machine.board().machineState().topRamBankIndex());
        assertEquals(7, machine.board().machineState().activeScreenBankIndex());
    }

    @Test
    void pagingLockPreventsFurtherChanges() {
        Spectrum128Machine machine = new Spectrum128Machine(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE]
        );

        machine.board().cpuBus().writePort(0x7FFD, 0x20);
        machine.board().cpuBus().writePort(0x7FFD, 0x1F);

        assertEquals(0, machine.board().machineState().selectedRomIndex());
        assertEquals(0, machine.board().machineState().topRamBankIndex());
        assertEquals(5, machine.board().machineState().activeScreenBankIndex());
        assertEquals(true, machine.board().machineState().pagingLocked());
    }

    @Test
    void activeScreenBankControlsDisplayedMemory() {
        Spectrum128Machine machine = new Spectrum128Machine(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE]
        );

        machine.board().memory().ramBank(5).write(0, 0xAA);
        machine.board().memory().ramBank(7).write(0, 0x55);

        assertEquals(0xAA, machine.board().memory().readDisplayMemory(0x4000));

        machine.board().cpuBus().writePort(0x7FFD, 0x08);

        assertEquals(0x55, machine.board().memory().readDisplayMemory(0x4000));
    }

    @Test
    void screenBankPagingKeepsBytesFetchedBeforePortWriteEvent() {
        TStateCounter clock = new TStateCounter();
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                clock
        );
        board.memory().ramBank(5).write(0x0000, 0x80);
        board.memory().ramBank(5).write(0x1800, 0x47);
        board.memory().ramBank(7).write(0x0000, 0x00);
        board.memory().ramBank(7).write(0x1800, 0x47);

        advance(board, clock, 14_360);
        board.cpuBus().writePort(0x7FFD, 0x08, 20);
        advance(board, clock, 100);
        advance(board, clock, board.modelConfig().frameTStates() - (int) clock.value());

        FrameBuffer frame = board.renderVideoFrame();
        int firstDisplayPixel = (SpectrumUlaDevice.BORDER_TOP * frame.width()) + SpectrumUlaDevice.BORDER_LEFT;

        assertEquals(7, board.machineState().activeScreenBankIndex());
        assertEquals(0xFFFFFFFF, frame.pixels()[firstDisplayPixel]);
    }

    @Test
    void ayRegisterPortSelectsWritesAndReadsBackRegisters() {
        Spectrum128Machine machine = new Spectrum128Machine(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE]
        );

        machine.board().cpuBus().writePort(0xFFFD, 0x08);
        machine.board().cpuBus().writePort(0xBFFD, 0x1F);

        assertEquals(0x08, machine.board().ay().selectedRegister());
        assertEquals(0x1F, machine.board().cpuBus().readPort(0xFFFD));
        assertEquals(0x1F, machine.board().ay().registerValue(8));

        machine.board().cpuBus().writePort(0xFFFD, 0x01);
        machine.board().cpuBus().writePort(0xBFFD, 0xFF);

        assertEquals(0x0F, machine.board().cpuBus().readPort(0xFFFD));
    }

    @Test
    void ayToneGenerationProducesAudioSamples() {
        Spectrum128Machine machine = new Spectrum128Machine(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE]
        );

        writeAy(machine, 0, 0x20);
        writeAy(machine, 1, 0x01);
        writeAy(machine, 7, 0x3E);
        writeAy(machine, 8, 0x0F);

        machine.board().onTStatesElapsed(35_000, 35_000);

        byte[] pcm = new byte[512];
        int copied = machine.board().audio().drainAudio(pcm, 0, pcm.length);

        assertTrue(copied > 0);
        assertTrue(hasNonZeroSample(pcm, copied));
        assertTrue(hasSampleTransition(pcm, copied));
    }

    @Test
    void stopTapeIf48kModeBlockDoesNotPauseSpectrum128Playback() {
        Spectrum128Machine machine = new Spectrum128Machine(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE]
        );
        machine.board().tape().load(new dev.z8emu.machine.spectrum48k.tape.TapeFile(List.of(
                dev.z8emu.machine.spectrum48k.tape.TapeBlock.stopTapeIf48kModeBlock(),
                dev.z8emu.machine.spectrum48k.tape.TapeBlock.pauseBlock(100, false)
        )));

        machine.board().tape().play();
        machine.board().onTStatesElapsed(1, 1);

        assertTrue(machine.board().tape().isPlaying());
        assertEquals(2, machine.board().tape().currentBlockIndex());
        assertFalse(machine.board().tape().isAtEnd());
    }

    private static void writeAy(Spectrum128Machine machine, int register, int value) {
        machine.board().cpuBus().writePort(0xFFFD, register);
        machine.board().cpuBus().writePort(0xBFFD, value);
    }

    private static void advance(Spectrum128Board board, TStateCounter clock, int tStates) {
        clock.advance(tStates);
        board.onTStatesElapsed(tStates, clock.value());
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
