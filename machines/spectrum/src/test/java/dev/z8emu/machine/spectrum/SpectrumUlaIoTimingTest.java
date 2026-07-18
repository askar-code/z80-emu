package dev.z8emu.machine.spectrum;

import dev.z8emu.machine.spectrum128k.Spectrum128Board;
import dev.z8emu.machine.spectrum48k.Spectrum48kBoard;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.time.TStateCounter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumUlaIoTimingTest {
    @Test
    void ulaSelectorDecodesA0RatherThanOnlyLowByteFe() {
        assertTrue(SpectrumUlaDevice.portSelector().matches(0x00FE));
        assertTrue(SpectrumUlaDevice.portSelector().matches(0x12FC));
        assertTrue(SpectrumUlaDevice.portSelector().matches(0xFF00));
        assertFalse(SpectrumUlaDevice.portSelector().matches(0x00FF));
        assertFalse(SpectrumUlaDevice.portSelector().matches(0x12FD));
    }

    @Test
    void routesMirroredEvenPortsToUlaOn48k() {
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new TStateCounter()
        );

        board.cpuBus().writePort(0x12FC, 0x15);
        assertEquals(5, board.ula().borderColor());

        board.cpuBus().writePort(0x12FD, 0x02);
        assertEquals(5, board.ula().borderColor(), "Odd neighbor must remain unmapped");

        board.keyboard().setKeyPressed(1, 0, true);
        assertEquals(0xFE, board.cpuBus().readPort(0xFDFC));
        assertEquals(0xFF, board.cpuBus().readPort(0xFDFD));
    }

    @Test
    void even128kDeviceMirrorsFanOutToUlaAndSelectedDevice() {
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new TStateCounter()
        );

        board.cpuBus().writePort(0x7FFC, 0x0D);
        assertEquals(5, board.ula().borderColor());
        assertEquals(5, board.machineState().topRamBankIndex());
        assertEquals(7, board.machineState().activeScreenBankIndex());

        board.cpuBus().writePort(0xFFFC, 0x00);
        assertEquals(0, board.ay().selectedRegister());
        board.cpuBus().writePort(0xBFFC, 0x4D);
        assertEquals(5, board.ula().borderColor());
        assertEquals(0x4D, board.ay().registerValue(0));

        assertEquals(
                0x0D,
                board.cpuBus().readPort(0xFFFC),
                "Two partially decoded devices drive a read overlap as a wired AND"
        );
        assertEquals(0x4D, board.cpuBus().readPort(0xFFFD));
    }

    @Test
    void highPortContentionTracksCurrentlyPaged128kBank() {
        TStateCounter clock = new TStateCounter();
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );
        clock.advance(board.modelConfig().contentionStartTState());

        assertEquals(0, board.cpuBus().readPortWaitStates(0xC001, 0));

        board.cpuBus().writePort(0x7FFD, 0x03);
        assertEquals(12, board.cpuBus().readPortWaitStates(0xC001, 0));

        board.cpuBus().writePort(0x7FFD, 0x04);
        assertEquals(0, board.cpuBus().readPortWaitStates(0xC001, 0));
    }

    @Test
    void spectrum48kBoardSynchronizesUlaToAbsoluteMachineTime() {
        TStateCounter clock = new TStateCounter();
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );

        board.cpuBus().writePort(0x00FE, 0x02, 20);
        advance(board, clock, board.modelConfig().frameTStates() - 1);

        assertEquals(0, board.ula().frameCounter());

        advance(board, clock, 1);
        assertEquals(1, board.ula().frameCounter());
    }

    @Test
    void spectrum128kBoardDoesNotDoubleAdvanceAfterMidInstructionPagingWrite() {
        TStateCounter clock = new TStateCounter();
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );

        board.cpuBus().writePort(0x7FFD, 0x00, 20);
        advance(board, clock, board.modelConfig().frameTStates() - 1);

        assertEquals(0, board.ula().frameCounter());

        advance(board, clock, 1);
        assertEquals(1, board.ula().frameCounter());
    }

    private static void advance(SpectrumBoard board, TStateCounter clock, int tStates) {
        clock.advance(tStates);
        board.onTStatesElapsed(tStates, clock.value());
    }
}
