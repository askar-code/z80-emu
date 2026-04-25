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
        int splitFrameLine = CpcGateArrayDevice.BORDER_TOP + splitDisplayLine;
        int hudOnTState = (splitFrameLine * 256) + 16;
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
        int hudFrameLine = CpcGateArrayDevice.BORDER_TOP + hudDisplayLine;
        int hudByteColumn = 30;

        machine.board().cpuBus().writeMemory(
                machine.board().crtc().displayMemoryAddress(hudDisplayLine, hudByteColumn),
                0x88
        );
        setPen(machine, 0, 20);
        setPen(machine, 1, 23);
        setPen(machine, 2, 19);
        setPen(machine, 3, 27);
        setMode(machine, 0);

        int hudModeTState = (hudFrameLine * 256) + 70;
        int pen1TState = (hudFrameLine * 256) + 101;
        int pen2TState = (hudFrameLine * 256) + 136;
        int pen3TState = (hudFrameLine * 256) + 171;
        machine.board().gateArray().onTStatesElapsed(hudModeTState, hudModeTState);
        machine.board().gateArray().writeRegister(0x81, machine.board().memory(), hudModeTState);
        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), pen1TState);
        machine.board().gateArray().writeRegister(0x4C, machine.board().memory(), pen1TState);
        machine.board().gateArray().writeRegister(0x02, machine.board().memory(), pen2TState);
        machine.board().gateArray().writeRegister(0x55, machine.board().memory(), pen2TState);
        machine.board().gateArray().writeRegister(0x03, machine.board().memory(), pen3TState);
        machine.board().gateArray().writeRegister(0x4B, machine.board().memory(), pen3TState);
        machine.board().gateArray().onTStatesElapsed(machine.frameTStates() - pen3TState, machine.frameTStates());

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(
                CpcGateArrayDevice.argbForHardwareColor(11),
                displayPixel(frame, hudByteColumn * 8, hudDisplayLine)
        );
    }

    @Test
    void gateArrayAlignsHudEventsWhenDisplayPhaseMovesDown() {
        CpcMachine machine = CpcMachine.withBlankRom();
        int hudDisplayLine = 193;
        int shiftedHudFrameLine = CpcGateArrayDevice.BORDER_TOP + 8 + hudDisplayLine;
        int hudByteColumn = 30;

        machine.board().cpuBus().writeMemory(
                machine.board().crtc().displayMemoryAddress(hudDisplayLine, hudByteColumn),
                0x88
        );
        setPen(machine, 0, 20);
        setPen(machine, 1, 23);
        setPen(machine, 2, 19);
        setPen(machine, 3, 27);
        setMode(machine, 0);

        int staleTopTState = (shiftedHudFrameLine - 8) * 256;
        machine.board().gateArray().onTStatesElapsed(staleTopTState, staleTopTState);
        machine.board().gateArray().writeRegister(0x81, machine.board().memory(), staleTopTState);
        machine.board().gateArray().writeRegister(0x80, machine.board().memory(), staleTopTState + 190);

        int hudModeTState = (shiftedHudFrameLine * 256) + 70;
        int pen1TState = (shiftedHudFrameLine * 256) + 101;
        int pen2TState = (shiftedHudFrameLine * 256) + 136;
        int pen3TState = (shiftedHudFrameLine * 256) + 171;
        machine.board().gateArray().onTStatesElapsed(hudModeTState - staleTopTState, hudModeTState);
        machine.board().gateArray().writeRegister(0x81, machine.board().memory(), hudModeTState);
        machine.board().gateArray().writeRegister(0x01, machine.board().memory(), pen1TState);
        machine.board().gateArray().writeRegister(0x4C, machine.board().memory(), pen1TState);
        machine.board().gateArray().writeRegister(0x02, machine.board().memory(), pen2TState);
        machine.board().gateArray().writeRegister(0x55, machine.board().memory(), pen2TState);
        machine.board().gateArray().writeRegister(0x03, machine.board().memory(), pen3TState);
        machine.board().gateArray().writeRegister(0x4B, machine.board().memory(), pen3TState);
        machine.board().gateArray().onTStatesElapsed(machine.frameTStates() - pen3TState, machine.frameTStates());

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(
                CpcGateArrayDevice.argbForHardwareColor(11),
                displayPixel(frame, hudByteColumn * 8, hudDisplayLine)
        );
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
    void gateArrayRaisesInterruptEvery52HsyncsUntilAcknowledged() {
        CpcMachine machine = CpcMachine.withBlankRom();

        machine.board().onTStatesElapsed((52 * 256) - 1, (52 * 256) - 1);

        assertFalse(machine.board().maskableInterruptLineActive(machine.currentTState()));

        machine.board().onTStatesElapsed(1, 52 * 256);

        assertTrue(machine.board().maskableInterruptLineActive(machine.currentTState()));

        machine.board().cpuBus().acknowledgeInterrupt();

        assertFalse(machine.board().maskableInterruptLineActive(machine.currentTState()));
    }

    @Test
    void gateArrayModeControlBit4ClearsInterruptRequestAndCounter() {
        CpcMachine machine = CpcMachine.withBlankRom();

        machine.board().onTStatesElapsed(52 * 256, 52 * 256);

        assertTrue(machine.board().maskableInterruptLineActive(machine.currentTState()));

        machine.board().cpuBus().writePort(0x7F00, 0x90);

        assertFalse(machine.board().maskableInterruptLineActive(machine.currentTState()));

        machine.board().onTStatesElapsed((52 * 256) - 1, (104 * 256) - 1);

        assertFalse(machine.board().maskableInterruptLineActive(machine.currentTState()));

        machine.board().onTStatesElapsed(1, 104 * 256);

        assertTrue(machine.board().maskableInterruptLineActive(machine.currentTState()));
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

        runUntilTState(machine, (16 * 256) + 4);

        assertEquals(0xFE, machine.board().cpuBus().readPort(0xF500));

        runUntilTState(machine, machine.frameTStates());

        assertEquals(0xFF, machine.board().cpuBus().readPort(0xF500));
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

    private static void runUntilTState(CpcMachine machine, long targetTState) {
        while (machine.currentTState() < targetTState) {
            machine.runInstruction();
        }
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
