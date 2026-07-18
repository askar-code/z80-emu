package dev.z8emu.machine.spectrum;

import dev.z8emu.machine.spectrum128k.Spectrum128Board;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.Spectrum48kBoard;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.time.TStateCounter;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectrumUlaDisplayTimingTest {
    private static final int FIRST_DISPLAY_PIXEL =
            (SpectrumUlaDevice.BORDER_TOP * SpectrumUlaDevice.FRAME_WIDTH)
                    + SpectrumUlaDevice.BORDER_LEFT;

    @Test
    void pixelAndAttributeBytesAreFetchedOnAdjacentTStates() {
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new TStateCounter()
        );
        board.memory().write(0x4000, 0x80);
        board.memory().write(0x5800, 0x02);

        board.ula().syncToTState(SpectrumUlaDevice.FLOATING_BUS_DISPLAY_START_48K, board.memory());
        board.memory().write(0x4000, 0x00);
        board.memory().write(0x5800, 0x47);
        board.ula().syncToTState(SpectrumUlaDevice.T_STATES_PER_FRAME, board.memory());

        FrameBuffer frame = board.renderVideoFrame();
        assertEquals(
                0xFFFFFFFF,
                frame.pixels()[FIRST_DISPLAY_PIXEL],
                "pixel byte must be the old value fetched at T, attribute the new value fetched at T+1"
        );
    }

    @Test
    void cpuScreenWriteIsAppliedAfterUlaCapturesEarlierFetches() {
        TStateCounter clock = new TStateCounter();
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );
        board.memory().write(0x4000, 0x80);
        board.memory().write(0x5800, 0x47);

        clock.advance(SpectrumUlaDevice.FLOATING_BUS_DISPLAY_START_48K + 5);
        int waitStates = board.cpuBus().writeMemoryWaitStates(0x4000, 0x00, 0);
        assertEquals(6, waitStates);
        board.cpuBus().writeMemory(0x4000, 0x00);
        advanceToFrameEnd(board, clock);

        assertEquals(
                0xFFFFFFFF,
                board.renderVideoFrame().pixels()[FIRST_DISPLAY_PIXEL],
                "write completes after the first ULA fetch and must not change the captured byte"
        );
    }

    @Test
    void cpuScreenWriteCompletedBeforeFetchIsVisibleInSameFrame() {
        TStateCounter clock = new TStateCounter();
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );
        board.memory().write(0x4000, 0x80);
        board.memory().write(0x5800, 0x47);

        clock.advance(SpectrumUlaDevice.FLOATING_BUS_DISPLAY_START_48K - 5);
        int waitStates = board.cpuBus().writeMemoryWaitStates(0x4000, 0x00, 0);
        assertEquals(0, waitStates);
        board.cpuBus().writeMemory(0x4000, 0x00);
        advanceToFrameEnd(board, clock);

        assertEquals(0xFF000000, board.renderVideoFrame().pixels()[FIRST_DISPLAY_PIXEL]);
    }

    @Test
    void waitStateQueryDoesNotAdvanceUlaOrCaptureDisplayMemory() {
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new TStateCounter()
        );
        board.memory().write(0x4000, 0x80);
        board.memory().write(0x5800, 0x47);

        board.cpuBus().writeMemoryWaitStates(
                0x4000,
                0x00,
                SpectrumUlaDevice.FLOATING_BUS_DISPLAY_START_48K + 5
        );
        board.memory().write(0x4000, 0x00);
        board.ula().syncToTState(SpectrumUlaDevice.T_STATES_PER_FRAME, board.memory());

        assertEquals(0xFF000000, board.renderVideoFrame().pixels()[FIRST_DISPLAY_PIXEL]);
    }

    @Test
    void directWriteAtExactFetchTStateCapturesOldValueBeforeMutation() {
        TStateCounter clock = new TStateCounter();
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );
        board.memory().write(0x4000, 0x80);
        board.memory().write(0x5800, 0x47);

        clock.advance(SpectrumUlaDevice.FLOATING_BUS_DISPLAY_START_48K);
        board.cpuBus().writeMemory(0x4000, 0x00);
        advanceToFrameEnd(board, clock);

        assertEquals(0xFFFFFFFF, board.renderVideoFrame().pixels()[FIRST_DISPLAY_PIXEL]);
    }

    @Test
    void cpuWriteToMapped128kShadowScreenUsesTheSameFetchOrdering() {
        TStateCounter clock = new TStateCounter();
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                clock
        );
        board.memory().ramBank(7).write(0x0000, 0x80);
        board.memory().ramBank(7).write(0x1800, 0x47);
        board.cpuBus().writePort(0x7FFD, 0x0F);

        clock.advance(SpectrumUlaDevice.FLOATING_BUS_DISPLAY_START_128K + 5);
        int waitStates = board.cpuBus().writeMemoryWaitStates(0xC000, 0x00, 0);
        assertEquals(6, waitStates);
        board.cpuBus().writeMemory(0xC000, 0x00);
        advanceToFrameEnd(board, clock);

        assertEquals(0xFFFFFFFF, board.renderVideoFrame().pixels()[FIRST_DISPLAY_PIXEL]);
    }

    @Test
    void flashPhaseHasStableWholeFrameCrcs() {
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new TStateCounter()
        );
        for (int address = 0x4000; address < 0x5800; address++) {
            board.memory().write(address, (address * 37) ^ (address >>> 3));
        }
        for (int address = 0x5800; address < 0x5B00; address++) {
            board.memory().write(address, 0xE1);
        }

        board.ula().syncToTState(SpectrumUlaDevice.T_STATES_PER_FRAME, board.memory());
        long normalCrc = crc32(board.renderVideoFrame());
        board.ula().syncToTState(17L * SpectrumUlaDevice.T_STATES_PER_FRAME, board.memory());
        long invertedCrc = crc32(board.renderVideoFrame());
        board.ula().syncToTState(33L * SpectrumUlaDevice.T_STATES_PER_FRAME, board.memory());

        assertEquals(3_578_032_197L, normalCrc);
        assertEquals(89_626_363L, invertedCrc);
        assertEquals(normalCrc, crc32(board.renderVideoFrame()));
    }

    @Test
    void normalAndShadowScreensHaveStableWholeFrameCrcs() {
        TStateCounter clock = new TStateCounter();
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                clock
        );
        for (int offset = 0; offset < 0x1800; offset++) {
            board.memory().ramBank(5).write(offset, 0xAA);
            board.memory().ramBank(7).write(offset, 0x55);
        }
        for (int offset = 0x1800; offset < 0x1B00; offset++) {
            board.memory().ramBank(5).write(offset, 0x47);
            board.memory().ramBank(7).write(offset, 0x42);
        }

        advance(board, clock, board.modelConfig().frameTStates());
        long normalScreenCrc = crc32(board.renderVideoFrame());

        board.cpuBus().writePort(0x7FFD, 0x08);
        advance(board, clock, board.modelConfig().frameTStates());
        long shadowScreenCrc = crc32(board.renderVideoFrame());

        assertEquals(645_088_005L, normalScreenCrc);
        assertEquals(2_427_709_875L, shadowScreenCrc);
    }

    @Test
    void borderEventsMapFromThePhysicalTopLeftPixelInsteadOfCenteringTheCrop() {
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new TStateCounter()
        );
        board.ula().reset();

        int displayTopLeft = SpectrumUlaDevice.FIRST_DISPLAY_PIXEL_TSTATE_48K;
        int visibleFrameStart = displayTopLeft
                - (SpectrumUlaDevice.BORDER_TOP * board.modelConfig().tStatesPerScanline())
                - (SpectrumUlaDevice.BORDER_LEFT / 2);
        int firstDisplayLineLeftBorder = displayTopLeft - (SpectrumUlaDevice.BORDER_LEFT / 2);

        board.ula().writePortFe(2, visibleFrameStart, board.beeper(), board.memory());
        board.ula().writePortFe(4, visibleFrameStart + 10, board.beeper(), board.memory());
        board.ula().writePortFe(1, firstDisplayLineLeftBorder, board.beeper(), board.memory());
        board.ula().syncToTState(board.modelConfig().frameTStates(), board.memory());

        FrameBuffer frame = board.renderVideoFrame();
        assertEquals(0xFFCD0000, frame.pixels()[0]);
        assertEquals(0xFFCD0000, frame.pixels()[23]);
        assertEquals(0xFF00CD00, frame.pixels()[24]);
        int lastTopBorderRow = (SpectrumUlaDevice.BORDER_TOP - 1) * SpectrumUlaDevice.FRAME_WIDTH;
        assertEquals(0xFF00CD00, frame.pixels()[lastTopBorderRow]);
        int firstDisplayRow = SpectrumUlaDevice.BORDER_TOP * SpectrumUlaDevice.FRAME_WIDTH;
        assertEquals(0xFF0000CD, frame.pixels()[firstDisplayRow]);
        assertEquals(2_486_451_741L, crc32(frame));
    }

    private static void advanceToFrameEnd(Spectrum48kBoard board, TStateCounter clock) {
        int remaining = SpectrumUlaDevice.T_STATES_PER_FRAME - (int) clock.value();
        clock.advance(remaining);
        board.onTStatesElapsed(remaining, clock.value());
    }

    private static void advanceToFrameEnd(Spectrum128Board board, TStateCounter clock) {
        int remaining = board.modelConfig().frameTStates() - (int) clock.value();
        clock.advance(remaining);
        board.onTStatesElapsed(remaining, clock.value());
    }

    private static void advance(Spectrum128Board board, TStateCounter clock, int tStates) {
        clock.advance(tStates);
        board.onTStatesElapsed(tStates, clock.value());
    }

    private static long crc32(FrameBuffer frame) {
        CRC32 crc = new CRC32();
        for (int pixel : frame.pixels()) {
            crc.update(pixel >>> 24);
            crc.update(pixel >>> 16);
            crc.update(pixel >>> 8);
            crc.update(pixel);
        }
        return crc.getValue();
    }
}
