package dev.z8emu.machine.cpc;

import dev.z8emu.machine.cpc.device.CpcGateArrayDevice;
import dev.z8emu.machine.cpc.device.CpcKeyboardDevice;
import dev.z8emu.machine.cpc.memory.CpcMemory;
import dev.z8emu.platform.video.FrameBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpcMachineTest {
    @Test
    void resetMapsLowerRomUpperRomAndBaseRamBanks() {
        byte[] rom = combinedRom(0x11, 0x22, 0x77);

        CpcMachine machine = new CpcMachine(rom);

        assertEquals(0x11, machine.board().cpuBus().readMemory(0x0000));
        assertEquals(0x22, machine.board().cpuBus().readMemory(0xC000));
        assertTrue(machine.board().memory().lowerRomEnabled());
        assertTrue(machine.board().memory().upperRomEnabled());
        assertEquals(0, machine.board().memory().selectedUpperRomIndex());
        assertEquals(0, machine.board().memory().visibleRamBankIndexForSlot(0));
        assertEquals(1, machine.board().memory().visibleRamBankIndexForSlot(1));
        assertEquals(2, machine.board().memory().visibleRamBankIndexForSlot(2));
        assertEquals(3, machine.board().memory().visibleRamBankIndexForSlot(3));
    }

    @Test
    void romWritesGoToUnderlyingRamAndBecomeVisibleWhenRomIsDisabled() {
        byte[] rom = combinedRom(0x11, 0x22, 0x77);
        CpcMachine machine = new CpcMachine(rom);

        machine.board().cpuBus().writeMemory(0x0000, 0x55);
        machine.board().cpuBus().writeMemory(0xC000, 0x66);

        assertEquals(0x11, machine.board().cpuBus().readMemory(0x0000));
        assertEquals(0x22, machine.board().cpuBus().readMemory(0xC000));

        machine.board().cpuBus().writePort(0x7F00, 0x8C);

        assertFalse(machine.board().memory().lowerRomEnabled());
        assertFalse(machine.board().memory().upperRomEnabled());
        assertEquals(0x55, machine.board().cpuBus().readMemory(0x0000));
        assertEquals(0x66, machine.board().cpuBus().readMemory(0xC000));
    }

    @Test
    void upperRomSelectPortSwitchesVisibleUpperRom() {
        byte[] rom = combinedRom(0x11, 0x22, 0x77);
        CpcMachine machine = new CpcMachine(rom);

        machine.board().cpuBus().writePort(0xDF00, 0x07);

        assertEquals(7, machine.board().memory().selectedUpperRomIndex());
        assertEquals(0x77, machine.board().cpuBus().readMemory(0xC000));
    }

    @Test
    void ramConfigurationSelectsCpc6128VisibleBanks() {
        CpcMachine machine = CpcMachine.withBlankRom();

        machine.board().cpuBus().writePort(0x7F00, 0xC2);

        assertEquals(2, machine.board().memory().ramConfiguration());
        assertEquals(4, machine.board().memory().visibleRamBankIndexForSlot(0));
        assertEquals(5, machine.board().memory().visibleRamBankIndexForSlot(1));
        assertEquals(6, machine.board().memory().visibleRamBankIndexForSlot(2));
        assertEquals(7, machine.board().memory().visibleRamBankIndexForSlot(3));

        machine.board().cpuBus().writeMemory(0x4000, 0x5A);

        assertEquals(0x5A, machine.board().memory().ramBank(5).read(0));
    }

    @Test
    void runsZ80InstructionAgainstCpcBus() {
        byte[] rom = new byte[CpcMemory.ROM_SIZE];
        rom[0] = 0x3E;
        rom[1] = 0x42;
        CpcMachine machine = new CpcMachine(rom);

        int tStates = machine.runInstruction();

        assertEquals(7, tStates);
        assertEquals(0x42, machine.cpu().registers().a());
        assertEquals(0x0002, machine.cpu().registers().pc());
        assertEquals(7, machine.currentTState());
    }

    @Test
    void cpcFrameTimingAlignsWithSixGateArrayInterruptPeriods() {
        CpcMachine machine = CpcMachine.withBlankRom();

        assertEquals(52 * 256 * 6, machine.frameTStates());
    }

    @Test
    void cpcFrameGeometryCentersActiveDisplayInVisibleFrame() {
        assertEquals(272, CpcGateArrayDevice.FRAME_HEIGHT);
        assertEquals(36, CpcGateArrayDevice.BORDER_TOP);
        assertEquals(36, CpcGateArrayDevice.BORDER_BOTTOM);
    }

    @Test
    void gateArrayRendersBorderAndMode1PixelsFromScreenMemory() {
        CpcMachine machine = CpcMachine.withBlankRom();

        setPen(machine, 0, 20);
        setPen(machine, 1, 11);
        setBorder(machine, 21);
        setMode(machine, 1);
        machine.board().cpuBus().writeMemory(0xC000, 0x80);

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(CpcGateArrayDevice.FRAME_WIDTH, frame.width());
        assertEquals(CpcGateArrayDevice.FRAME_HEIGHT, frame.height());
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(21), pixel(frame, 0, 0));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(11), displayPixel(frame, 0, 0));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(11), displayPixel(frame, 1, 0));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(20), displayPixel(frame, 2, 0));
    }

    @Test
    void gateArrayOnlyDecodesPortsWithA15LowAndA14High() {
        CpcMachine machine = CpcMachine.withBlankRom();

        machine.board().cpuBus().writePort(0x3000, 0x01);
        machine.board().cpuBus().writePort(0x3000, 0x4B);

        assertEquals(20, machine.board().gateArray().hardwareInkForPen(1));

        machine.board().cpuBus().writePort(0x7F00, 0x01);
        machine.board().cpuBus().writePort(0x7F00, 0x4B);

        assertEquals(11, machine.board().gateArray().hardwareInkForPen(1));
    }

    @Test
    void gateArrayRendersMode0AndMode2PixelsAtACommonDisplayWidth() {
        CpcMachine machine = CpcMachine.withBlankRom();
        setPen(machine, 0, 20);
        setPen(machine, 1, 11);
        machine.board().cpuBus().writeMemory(0xC000, 0x80);

        setMode(machine, 0);
        FrameBuffer mode0 = machine.board().renderVideoFrame();
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(11), displayPixel(mode0, 0, 0));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(11), displayPixel(mode0, 3, 0));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(20), displayPixel(mode0, 4, 0));

        setMode(machine, 2);
        FrameBuffer mode2 = machine.board().renderVideoFrame();
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(11), displayPixel(mode2, 0, 0));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(20), displayPixel(mode2, 1, 0));
    }

    @Test
    void gateArrayCapturesRasterModeAndPaletteChangesPerFrameLine() {
        CpcMachine machine = CpcMachine.withBlankRom();
        int splitDisplayLine = 4;
        int splitFrameLine = 72 + splitDisplayLine;
        // Mode latches at HSYNC (line origin); write at the boundary so the mode
        // applies to this line while the pen write stays mid-line-captured.
        int hudOnTState = splitFrameLine * 256;
        int hudOffTState = (splitFrameLine * 256) + 200;

        machine.board().cpuBus().writeMemory(machine.board().crtc().displayMemoryAddress(0, 0), 0x80);
        machine.board().cpuBus().writeMemory(
                machine.board().crtc().displayMemoryAddress(splitDisplayLine, 0),
                0x80
        );
        setPen(machine, 0, 20);
        setPen(machine, 1, 11);
        setMode(machine, 0);

        machine.board().gateArray().onTStatesElapsed(hudOnTState, hudOnTState);
        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), hudOnTState);
        machine.board().gateArray().writeRegister(0x4C, machine.board().memory(), hudOnTState);
        machine.board().gateArray().writeRegister(0x81, machine.board().memory(), hudOnTState);
        machine.board().gateArray().onTStatesElapsed(hudOffTState - hudOnTState, hudOffTState);
        machine.board().gateArray().writeRegister(0x80, machine.board().memory(), hudOffTState);
        machine.board().gateArray().onTStatesElapsed(machine.frameTStates() - hudOffTState, machine.frameTStates());

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(CpcGateArrayDevice.argbForHardwareColor(11), displayPixel(frame, 3, 0));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(12), displayPixel(frame, 0, splitDisplayLine));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(20), displayPixel(frame, 3, splitDisplayLine));
    }

    @Test
    void gateArraySamplesHudTextAfterLatePaletteWrites() {
        CpcMachine machine = CpcMachine.withBlankRom();
        int hudDisplayLine = 193;
        int hudFrameLine = 72 + hudDisplayLine;
        int hudByteColumn = 78;

        machine.board().cpuBus().writeMemory(
                machine.board().crtc().displayMemoryAddress(hudDisplayLine, hudByteColumn),
                0x88
        );
        setPen(machine, 0, 20);
        setPen(machine, 1, 23);
        setPen(machine, 2, 19);
        setPen(machine, 3, 27);
        setMode(machine, 0);

        writeHudPaletteSequence(machine, hudFrameLine, 0, 0);

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(
                CpcGateArrayDevice.argbForHardwareColor(11),
                displayPixel(frame, hudByteColumn * 8, hudDisplayLine)
        );
    }

    @Test
    void gateArraySamplesDisplayEventsFromSteadyCrtcDisplayTop() {
        CpcMachine upperSideMachine = CpcMachine.withBlankRom();
        upperSideMachine.board().cpuBus().writeMemory(
                upperSideMachine.board().crtc().displayMemoryAddress(0, 0),
                0x80
        );
        setPen(upperSideMachine, 0, 20);
        setPen(upperSideMachine, 1, 20);
        setMode(upperSideMachine, 1);
        long rowZeroFirstSample = (72 * 256L) + 16;
        upperSideMachine.board().gateArray().writeRegister(0x01, upperSideMachine.board().memory(), rowZeroFirstSample);
        upperSideMachine.board().gateArray().writeRegister(0x4B, upperSideMachine.board().memory(), rowZeroFirstSample);
        advanceGateArray(upperSideMachine, rowZeroFirstSample, upperSideMachine.frameTStates());

        assertEquals(
                CpcGateArrayDevice.argbForHardwareColor(11),
                displayPixel(upperSideMachine.board().renderVideoFrame(), 0, 0)
        );

        CpcMachine nextLineMachine = CpcMachine.withBlankRom();
        nextLineMachine.board().cpuBus().writeMemory(nextLineMachine.board().crtc().displayMemoryAddress(0, 0), 0x80);
        nextLineMachine.board().cpuBus().writeMemory(nextLineMachine.board().crtc().displayMemoryAddress(1, 0), 0x80);
        setPen(nextLineMachine, 0, 20);
        setPen(nextLineMachine, 1, 20);
        setMode(nextLineMachine, 1);
        long rowOneBoundary = 73 * 256L;
        nextLineMachine.board().gateArray().writeRegister(0x01, nextLineMachine.board().memory(), rowOneBoundary);
        nextLineMachine.board().gateArray().writeRegister(0x4B, nextLineMachine.board().memory(), rowOneBoundary);
        advanceGateArray(nextLineMachine, rowOneBoundary, nextLineMachine.frameTStates());

        FrameBuffer nextLineFrame = nextLineMachine.board().renderVideoFrame();
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(20), displayPixel(nextLineFrame, 0, 0));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(11), displayPixel(nextLineFrame, 0, 1));
    }

    @Test
    void gateArrayLatchesShiftedCrtcDisplayTopAfterSecondVsyncSwap() {
        CpcMachine machine = CpcMachine.withBlankRom();
        machine.board().crtc().selectRegister(7);
        machine.board().crtc().writeSelectedRegister(28);
        machine.board().cpuBus().writeMemory(machine.board().crtc().displayMemoryAddress(0, 0), 0x80);
        setPen(machine, 0, 20);
        setPen(machine, 1, 20);
        setMode(machine, 1);

        long transitionalProbe = (72 * 256L) + 16;
        advanceGateArray(machine, 0, transitionalProbe);
        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), transitionalProbe);
        machine.board().gateArray().writeRegister(0x4B, machine.board().memory(), transitionalProbe);

        long transitionalDecoy = 80 * 256L;
        advanceGateArray(machine, transitionalProbe, transitionalDecoy);
        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), transitionalDecoy);
        machine.board().gateArray().writeRegister(0x46, machine.board().memory(), transitionalDecoy);

        long transitionalPassEnd = 75_776;
        advanceGateArray(machine, transitionalDecoy, transitionalPassEnd);

        assertEquals(transitionalPassEnd, machine.board().gateArray().completedPassLengthTStates());
        assertEquals(
                CpcGateArrayDevice.argbForHardwareColor(11),
                displayPixel(machine.board().renderVideoFrame(), 0, 0)
        );

        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), transitionalPassEnd);
        machine.board().gateArray().writeRegister(0x54, machine.board().memory(), transitionalPassEnd);

        long secondPassProbe = transitionalPassEnd + (88 * 256L) + 16;
        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), secondPassProbe);
        machine.board().gateArray().writeRegister(0x4B, machine.board().memory(), secondPassProbe);
        long secondPassEnd = transitionalPassEnd + machine.frameTStates();
        advanceGateArray(machine, secondPassProbe, secondPassEnd);

        assertEquals(machine.frameTStates(), machine.board().gateArray().completedPassLengthTStates());
        assertEquals(
                CpcGateArrayDevice.argbForHardwareColor(11),
                displayPixel(machine.board().renderVideoFrame(), 0, 0)
        );
    }

    @Test
    void midPassCrtcStartAddressChangeDoesNotMoveDisplayTop() {
        CpcMachine machine = CpcMachine.withBlankRom();
        setPen(machine, 0, 20);
        setPen(machine, 1, 20);
        setMode(machine, 1);
        advanceGateArray(machine, 0, 10_000);
        machine.board().crtc().selectRegister(12);
        machine.board().crtc().writeSelectedRegister(0x31);
        machine.board().cpuBus().writeMemory(machine.board().crtc().displayMemoryAddress(0, 0), 0x80);
        machine.board().cpuBus().writeMemory(machine.board().crtc().displayMemoryAddress(1, 0), 0x80);

        long rowOneBoundary = 73 * 256L;
        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), rowOneBoundary);
        machine.board().gateArray().writeRegister(0x4B, machine.board().memory(), rowOneBoundary);
        advanceGateArray(machine, rowOneBoundary, machine.frameTStates());

        FrameBuffer frame = machine.board().renderVideoFrame();
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(20), displayPixel(frame, 0, 0));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(11), displayPixel(frame, 0, 1));
    }

    @Test
    void gateArrayMapsBorderRowsToFixedPassLines() {
        CpcMachine machine = CpcMachine.withBlankRom();
        setBorder(machine, 20);
        int changedFrameBufferRow = 10;
        long borderInkTState = ((36 + changedFrameBufferRow) * 256L) + 8;
        machine.board().gateArray().writeRegister(0x10, machine.board().memory(), borderInkTState);
        machine.board().gateArray().writeRegister(0x4B, machine.board().memory(), borderInkTState);
        advanceGateArray(machine, borderInkTState, machine.frameTStates());

        FrameBuffer frame = machine.board().renderVideoFrame();
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(20), pixel(frame, 0, changedFrameBufferRow - 1));
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(11), pixel(frame, 0, changedFrameBufferRow));
    }

    @Test
    void gateArrayFallsBackToFixedPassesWhenVsyncNeverStarts() {
        CpcMachine machine = CpcMachine.withBlankRom();
        machine.board().crtc().selectRegister(7);
        machine.board().crtc().writeSelectedRegister(100);
        machine.board().cpuBus().writeMemory(machine.board().crtc().displayMemoryAddress(0, 0), 0x80);
        setPen(machine, 0, 20);
        setPen(machine, 1, 20);
        setMode(machine, 1);

        long firstWrapProbe = (72 * 256L) + 16;
        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), firstWrapProbe);
        machine.board().gateArray().writeRegister(0x4B, machine.board().memory(), firstWrapProbe);
        long secondWrapProbe = (384 * 256L) + 16;
        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), secondWrapProbe);
        machine.board().gateArray().writeRegister(0x4C, machine.board().memory(), secondWrapProbe);

        long firstForcedSwap = 2L * machine.frameTStates();
        advanceGateArray(machine, secondWrapProbe, firstForcedSwap);

        assertEquals(firstForcedSwap, machine.board().gateArray().completedPassLengthTStates());
        assertEquals(
                CpcGateArrayDevice.argbForHardwareColor(11),
                displayPixel(machine.board().renderVideoFrame(), 0, 0)
        );

        long secondForcedSwap = firstForcedSwap + machine.frameTStates();
        advanceGateArray(machine, firstForcedSwap, secondForcedSwap);

        assertEquals(machine.frameTStates(), machine.board().gateArray().completedPassLengthTStates());
    }

    private static void advanceGateArray(CpcMachine machine, long fromTState, long targetTState) {
        machine.board().gateArray().onTStatesElapsed((int) (targetTState - fromTState), targetTState);
    }

    @Test
    void crtcStartAddressControlsFirstDisplayedByte() {
        CpcMachine machine = CpcMachine.withBlankRom();
        setPen(machine, 0, 20);
        setPen(machine, 1, 11);
        setMode(machine, 1);
        machine.board().cpuBus().writeMemory(0xC000, 0x00);
        machine.board().cpuBus().writeMemory(0xC002, 0x80);

        FrameBuffer defaultFrame = machine.board().renderVideoFrame();
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(20), displayPixel(defaultFrame, 0, 0));

        machine.board().cpuBus().writePort(0xBC00, 12);
        machine.board().cpuBus().writePort(0xBD00, 0x30);
        machine.board().cpuBus().writePort(0xBC00, 13);
        machine.board().cpuBus().writePort(0xBD00, 0x01);

        FrameBuffer shiftedFrame = machine.board().renderVideoFrame();
        assertEquals(0xC002, machine.board().crtc().screenStartByteAddress());
        assertEquals(CpcGateArrayDevice.argbForHardwareColor(11), displayPixel(shiftedFrame, 0, 0));
    }

    @Test
    void gateArrayFreeRunsFromThePostVsyncSyncPhaseUntilAcknowledged() {
        CpcMachine machine = CpcMachine.withBlankRom();
        long[] interruptTStates = {
                13_824, 27_136, 40_448, 53_760, 67_072, 80_384, 93_696, 107_008
        };
        long cursor = 0;
        int firstFrameInterrupts = 0;

        for (long interruptTState : interruptTStates) {
            advanceGateArray(machine, cursor, interruptTState - 1);
            assertFalse(machine.board().gateArray().maskableInterruptLineActive(), "before t=" + interruptTState);

            advanceGateArray(machine, interruptTState - 1, interruptTState);
            assertTrue(machine.board().gateArray().maskableInterruptLineActive(), "at t=" + interruptTState);
            if (interruptTState < machine.frameTStates()) {
                firstFrameInterrupts++;
            }
            machine.board().gateArray().acknowledgeInterrupt();
            cursor = interruptTState;
        }

        assertEquals(5, firstFrameInterrupts);
    }

    @Test
    void gateArrayResyncsInterruptCounterTwoHsyncsAfterVsync() {
        CpcMachine belowThreshold = CpcMachine.withBlankRom();
        advanceGateArray(belowThreshold, 0, 67_072);
        assertTrue(belowThreshold.board().gateArray().maskableInterruptLineActive());
        advanceGateArray(belowThreshold, 67_072, 77_312);
        belowThreshold.board().gateArray().acknowledgeInterrupt();
        advanceGateArray(belowThreshold, 77_312, 80_384);
        assertFalse(belowThreshold.board().gateArray().maskableInterruptLineActive());
        advanceGateArray(belowThreshold, 80_384, 93_695);
        assertFalse(belowThreshold.board().gateArray().maskableInterruptLineActive());
        advanceGateArray(belowThreshold, 93_695, 93_696);
        assertTrue(belowThreshold.board().gateArray().maskableInterruptLineActive());

        CpcMachine aboveThreshold = CpcMachine.withBlankRom();
        advanceGateArray(aboveThreshold, 0, 68_096);
        aboveThreshold.board().gateArray().writeRegister(0x90, aboveThreshold.board().memory(), 68_096);
        assertFalse(aboveThreshold.board().gateArray().maskableInterruptLineActive());
        advanceGateArray(aboveThreshold, 68_096, 80_383);
        assertFalse(aboveThreshold.board().gateArray().maskableInterruptLineActive());
        advanceGateArray(aboveThreshold, 80_383, 80_384);
        assertTrue(aboveThreshold.board().gateArray().maskableInterruptLineActive());
        aboveThreshold.board().gateArray().acknowledgeInterrupt();
        advanceGateArray(aboveThreshold, 80_384, 93_695);
        assertFalse(aboveThreshold.board().gateArray().maskableInterruptLineActive());
        advanceGateArray(aboveThreshold, 93_695, 93_696);
        assertTrue(aboveThreshold.board().gateArray().maskableInterruptLineActive());

        CpcMachine coincidentWrap = CpcMachine.withBlankRom();
        long cursor = 0;
        for (long interruptTState : new long[] {13_824, 27_136, 40_448, 53_760, 67_072}) {
            advanceGateArray(coincidentWrap, cursor, interruptTState);
            assertTrue(coincidentWrap.board().gateArray().maskableInterruptLineActive());
            coincidentWrap.board().gateArray().acknowledgeInterrupt();
            cursor = interruptTState;
        }
        advanceGateArray(coincidentWrap, cursor, 80_383);
        assertFalse(coincidentWrap.board().gateArray().maskableInterruptLineActive());
        advanceGateArray(coincidentWrap, 80_383, 80_384);
        assertTrue(coincidentWrap.board().gateArray().maskableInterruptLineActive());
        coincidentWrap.board().gateArray().acknowledgeInterrupt();
        assertFalse(coincidentWrap.board().gateArray().maskableInterruptLineActive());
        advanceGateArray(coincidentWrap, 80_384, 93_696);
        assertTrue(coincidentWrap.board().gateArray().maskableInterruptLineActive());
    }

    @Test
    void gateArrayAcknowledgeClearsCounterBitFive() {
        CpcMachine machine = CpcMachine.withBlankRom();
        long counterFortyTState = 42 * 256L;
        advanceGateArray(machine, 0, counterFortyTState);
        assertFalse(machine.board().gateArray().maskableInterruptLineActive());

        machine.board().gateArray().acknowledgeInterrupt();

        long nextInterruptTState = counterFortyTState + (44 * 256L);
        advanceGateArray(machine, counterFortyTState, nextInterruptTState - 1);
        assertFalse(machine.board().gateArray().maskableInterruptLineActive());
        advanceGateArray(machine, nextInterruptTState - 1, nextInterruptTState);
        assertTrue(machine.board().gateArray().maskableInterruptLineActive());
    }

    @Test
    void gateArrayVsyncToVsyncSpans79872TStates() {
        CpcMachine machine = CpcMachine.withBlankRom();
        long firstVsyncTState = -1;
        long secondVsyncTState = -1;
        long previousTState = 0;

        for (int line = 1; line <= 624; line++) {
            long lineEndTState = line * 256L;
            advanceGateArray(machine, previousTState, lineEndTState);
            if (machine.board().crtc().vsyncStartedThisTick()) {
                if (firstVsyncTState < 0) {
                    firstVsyncTState = lineEndTState;
                } else {
                    secondVsyncTState = lineEndTState;
                    break;
                }
            }
            previousTState = lineEndTState;
        }

        assertEquals(79_872, firstVsyncTState);
        assertEquals(79_872, secondVsyncTState - firstVsyncTState);
    }

    @Test
    void gateArrayModeControlBit4ClearsInterruptRequestAndCounter() {
        CpcMachine machine = CpcMachine.withBlankRom();

        machine.board().onTStatesElapsed(13_824, 13_824);

        assertTrue(machine.board().maskableInterruptLineActive(13_824));

        machine.board().cpuBus().writePort(0x7F00, 0x90);

        assertFalse(machine.board().maskableInterruptLineActive(13_824));

        machine.board().onTStatesElapsed(27_135 - 13_824, 27_135);

        assertFalse(machine.board().maskableInterruptLineActive(27_135));

        machine.board().onTStatesElapsed(1, 27_136);

        assertTrue(machine.board().maskableInterruptLineActive(27_136));
    }

    @Test
    void ppiPsgProtocolReadsSelectedKeyboardLine() {
        CpcMachine machine = CpcMachine.withBlankRom();
        machine.board().keyboard().setKeyPressed(8, 5, true);
        machine.board().keyboard().setKeyPressed(8, 3, true);

        assertEquals(0xD7, readKeyboardLine(machine, 8));
        assertEquals(14, machine.board().ay().selectedRegister());
        assertEquals(8, machine.board().ppi().selectedKeyboardLine());
        assertEquals(0xFF, readKeyboardLine(machine, 7));
    }

    @Test
    void ppiReturnsUnpressedValueForUnconnectedKeyboardLines() {
        CpcMachine machine = CpcMachine.withBlankRom();

        assertEquals(0xFF, readKeyboardLine(machine, 11));
    }

    @Test
    void ppiControlBitSetResetUpdatesPortC() {
        CpcMachine machine = CpcMachine.withBlankRom();

        machine.board().cpuBus().writePort(0xF600, 0x00);
        machine.board().cpuBus().writePort(0xF700, 0x07);

        assertEquals(0x08, machine.board().ppi().portCOutput());

        machine.board().cpuBus().writePort(0xF700, 0x06);

        assertEquals(0x00, machine.board().ppi().portCOutput());
    }

    @Test
    void ppiPortBExposesFrameVsyncBit() {
        CpcMachine machine = CpcMachine.withBlankRom();

        assertEquals(0xFF, machine.board().cpuBus().readPort(0xF500));

        advanceGateArray(machine, 0, 15 * 256L);
        assertEquals(0xFF, machine.board().cpuBus().readPort(0xF500));

        advanceGateArray(machine, 15 * 256L, 16 * 256L);
        assertEquals(0xFE, machine.board().cpuBus().readPort(0xF500));

        advanceGateArray(machine, 16 * 256L, 312 * 256L);
        assertEquals(0xFF, machine.board().cpuBus().readPort(0xF500));

        advanceGateArray(machine, 312 * 256L, (328 * 256L) - 1);
        assertEquals(0xFF, machine.board().cpuBus().readPort(0xF500));

        advanceGateArray(machine, (328 * 256L) - 1, 328 * 256L);
        assertEquals(0xFE, machine.board().cpuBus().readPort(0xF500));
    }

    @Test
    void ppiWritesSelectedPsgRegister() {
        CpcMachine machine = CpcMachine.withBlankRom();

        machine.board().cpuBus().writePort(0xF400, 7);
        machine.board().cpuBus().writePort(0xF600, 0xC0);
        machine.board().cpuBus().writePort(0xF400, 0x3F);
        machine.board().cpuBus().writePort(0xF600, 0x80);

        assertEquals(7, machine.board().ay().selectedRegister());
        assertEquals(0x3F, machine.board().ay().registerValue(7));
    }

    @Test
    void joystick0IsExposedOnKeyboardLine9() {
        CpcMachine machine = CpcMachine.withBlankRom();

        machine.board().keyboard().setJoystick0Pressed(CpcKeyboardDevice.Joystick0Input.FIRE1, true);
        machine.board().keyboard().setJoystick0Pressed(CpcKeyboardDevice.Joystick0Input.LEFT, true);

        assertEquals(0xDB, readKeyboardLine(machine, 9));
    }

    @Test
    void ayToneGenerationProducesAudioSamples() {
        CpcMachine machine = CpcMachine.withBlankRom();

        writeAy(machine, 0, 0x20);
        writeAy(machine, 1, 0x01);
        writeAy(machine, 7, 0x3E);
        writeAy(machine, 8, 0x0F);

        machine.board().onTStatesElapsed(40_000, 40_000);

        byte[] pcm = new byte[512];
        int copied = machine.board().audio().drainAudio(pcm, 0, pcm.length);

        assertTrue(copied > 0);
        assertTrue(hasNonZeroSample(pcm, copied));
    }

    private static byte[] combinedRom(int lowerByte, int upper0Byte, int upper7Byte) {
        byte[] rom = new byte[CpcMemory.ROM_IMAGE_SIZE_OS_BASIC_AMSDOS];
        fillRange(rom, 0, CpcMemory.ROM_SIZE, lowerByte);
        fillRange(rom, CpcMemory.ROM_SIZE, CpcMemory.ROM_SIZE * 2, upper0Byte);
        fillRange(rom, CpcMemory.ROM_SIZE * 2, CpcMemory.ROM_SIZE * 3, upper7Byte);
        return rom;
    }

    private static void fillRange(byte[] bytes, int start, int end, int value) {
        for (int i = start; i < end; i++) {
            bytes[i] = (byte) value;
        }
    }

    private static void setPen(CpcMachine machine, int pen, int hardwareColor) {
        machine.board().cpuBus().writePort(0x7F00, pen & 0x0F);
        machine.board().cpuBus().writePort(0x7F00, 0x40 | (hardwareColor & 0x1F));
    }

    private static void setBorder(CpcMachine machine, int hardwareColor) {
        machine.board().cpuBus().writePort(0x7F00, 0x10);
        machine.board().cpuBus().writePort(0x7F00, 0x40 | (hardwareColor & 0x1F));
    }

    private static void setMode(CpcMachine machine, int mode) {
        machine.board().cpuBus().writePort(0x7F00, 0x80 | (mode & 0x03));
    }

    private static void writeHudPaletteSequence(
            CpcMachine machine,
            int hudFrameLine,
            long frameBase,
            long alreadyElapsedTStates
    ) {
        long hudModeTState = frameBase + (hudFrameLine * 256L) + 70;
        long pen1TState = frameBase + (hudFrameLine * 256L) + 101;
        long pen2TState = frameBase + (hudFrameLine * 256L) + 136;
        long pen3TState = frameBase + (hudFrameLine * 256L) + 171;
        machine.board().gateArray().onTStatesElapsed(
                (int) (hudModeTState - alreadyElapsedTStates),
                hudModeTState
        );
        machine.board().gateArray().writeRegister(0x81, machine.board().memory(), hudModeTState);
        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), pen1TState);
        machine.board().gateArray().writeRegister(0x4C, machine.board().memory(), pen1TState);
        machine.board().gateArray().writeRegister(0x02, machine.board().memory(), pen2TState);
        machine.board().gateArray().writeRegister(0x55, machine.board().memory(), pen2TState);
        machine.board().gateArray().writeRegister(0x03, machine.board().memory(), pen3TState);
        machine.board().gateArray().writeRegister(0x4B, machine.board().memory(), pen3TState);
        long frameEnd = frameBase + machine.frameTStates();
        advanceGateArray(machine, pen3TState, frameEnd);
    }

    private static int readKeyboardLine(CpcMachine machine, int line) {
        machine.board().cpuBus().writePort(0xF700, 0x82);
        machine.board().cpuBus().writePort(0xF400, 14);
        machine.board().cpuBus().writePort(0xF600, 0xC0);
        machine.board().cpuBus().writePort(0xF600, 0x00);
        machine.board().cpuBus().writePort(0xF700, 0x92);
        machine.board().cpuBus().writePort(0xF600, 0x40 | (line & 0x0F));
        return machine.board().cpuBus().readPort(0xF400);
    }

    private static void writeAy(CpcMachine machine, int register, int value) {
        machine.board().cpuBus().writePort(0xF400, register);
        machine.board().cpuBus().writePort(0xF600, 0xC0);
        machine.board().cpuBus().writePort(0xF400, value);
        machine.board().cpuBus().writePort(0xF600, 0x80);
    }


    private static boolean hasNonZeroSample(byte[] pcm, int length) {
        for (int i = 0; i + 1 < length; i += 2) {
            if (decodeSample(pcm, i) != 0) {
                return true;
            }
        }
        return false;
    }

    private static int decodeSample(byte[] pcm, int offset) {
        return (short) (((pcm[offset + 1] & 0xFF) << 8) | (pcm[offset] & 0xFF));
    }

    private static int displayPixel(FrameBuffer frame, int x, int y) {
        return pixel(frame, CpcGateArrayDevice.BORDER_LEFT + x, CpcGateArrayDevice.BORDER_TOP + y);
    }

    private static int pixel(FrameBuffer frame, int x, int y) {
        return frame.pixels()[(y * frame.width()) + x];
    }
}
