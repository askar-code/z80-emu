package dev.z8emu.machine.radio86rk;

import dev.z8emu.machine.radio86rk.device.Radio86VideoDevice;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.platform.video.FrameBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Radio86MachineTest {
    @Test
    void topRomReadDisablesBootShadowAndRevealsRamAtAddressZero() {
        byte[] rom = new byte[Radio86Memory.ROM_SIZE_2K];
        rom[0x0000] = 0x3A;

        Radio86Machine machine = new Radio86Machine(rom);

        assertEquals(0x3A, machine.board().cpuBus().readMemory(0x0000));

        machine.board().cpuBus().readMemory(0xF000);
        machine.board().cpuBus().writeMemory(0x0000, 0x55);

        assertFalse(machine.board().memory().bootShadowEnabled());
        assertEquals(0x55, machine.board().cpuBus().readMemory(0x0000));
    }

    @Test
    void keyboardMatrixUsesActiveLowSelectedRows() {
        Radio86Machine machine = new Radio86Machine(new byte[Radio86Memory.ROM_SIZE_2K]);

        machine.board().keyboard().setKeyPressed(6, 0, true);
        machine.board().keyboard().setKeyPressed(8, 5, true);
        machine.board().cpuBus().writeMemory(0x8000, 0xBF);

        assertEquals(0xFE, machine.board().cpuBus().readMemory(0x8001));
        assertEquals(0xDF, machine.board().cpuBus().readMemory(0x8002));
    }

    @Test
    void rendersVisibleTextWindowFromVideoMemory() {
        Radio86Machine machine = new Radio86Machine(new byte[Radio86Memory.ROM_SIZE_2K]);

        initializeVideo(machine);

        machine.board().memory().writeLowMemory(Radio86Memory.VIDEO_MEMORY_START, 'W');
        FrameBuffer hiddenFrame = machine.board().renderVideoFrame();
        assertEquals(0xFF000000, hiddenFrame.pixels()[0]);

        machine.board().memory().writeLowMemory(
                Radio86Memory.VIDEO_MEMORY_START + Radio86VideoDevice.VISIBLE_OFFSET,
                'A'
        );

        FrameBuffer frame = machine.board().renderVideoFrame();
        int litPixels = 0;
        for (int y = 0; y < Radio86VideoDevice.CELL_HEIGHT; y++) {
            for (int x = 0; x < Radio86VideoDevice.CELL_WIDTH; x++) {
                int pixel = frame.pixels()[(y * frame.width()) + x];
                if (pixel == 0xFFA0A0A0) {
                    litPixels++;
                }
            }
        }

        assertEquals(true, litPixels > 0);
    }

    @Test
    void dmaAndCrtcProgrammingExposeScreenBufferConfiguration() {
        Radio86Machine machine = new Radio86Machine(new byte[Radio86Memory.ROM_SIZE_2K]);

        initializeVideo(machine);

        assertEquals(Radio86Memory.VIDEO_MEMORY_START, machine.board().dma().channelBaseAddress(2));
        assertEquals(Radio86Memory.VIDEO_MEMORY_LENGTH, machine.board().dma().channelTransferLength(2));
        assertEquals(0xA4, machine.board().dma().modeRegister());
        assertEquals(Radio86VideoDevice.TOTAL_COLUMNS, machine.board().video().charactersPerRow());
        assertEquals(Radio86VideoDevice.TOTAL_ROWS, machine.board().video().characterRowsPerFrame());
        assertEquals(true, machine.board().video().videoEnabled());
        assertEquals(true, machine.board().video().interruptsEnabled());
    }

    @Test
    void crtStatusUsesCommandStatusPortAndRaisesInterruptRequestAfterStartDisplay() {
        Radio86Machine machine = new Radio86Machine(new byte[Radio86Memory.ROM_SIZE_2K]);

        initializeVideo(machine);

        assertEquals(0x44, machine.board().cpuBus().readMemory(0xC001));

        machine.board().onTStatesElapsed(machine.frameTStates() - 1, machine.frameTStates() - 1);
        assertEquals(0, machine.board().video().frameCounter());
        assertEquals(0x44, machine.board().cpuBus().readMemory(0xC001));

        machine.board().onTStatesElapsed(1, machine.frameTStates());
        assertEquals(1, machine.board().video().frameCounter());
        assertEquals(0x64, machine.board().cpuBus().readMemory(0xC001));
        assertEquals(0x44, machine.board().cpuBus().readMemory(0xC001));

        machine.board().cpuBus().writeMemory(0xC001, 0x80);
        machine.board().cpuBus().writeMemory(0xC000, 0x34);
        machine.board().cpuBus().writeMemory(0xC000, 0x12);

        assertEquals(0x1234, machine.board().video().cursorAddress());
        assertFalse(machine.board().maskableInterruptLineActive(machine.currentTState()));
    }

    @Test
    void loadCursorRendersUnderlineAtProgrammedCell() {
        Radio86Machine machine = new Radio86Machine(new byte[Radio86Memory.ROM_SIZE_2K]);

        initializeVideo(machine);
        machine.board().cpuBus().writeMemory(0xC001, 0x80);
        machine.board().cpuBus().writeMemory(0xC000, 0x08);
        machine.board().cpuBus().writeMemory(0xC000, 0x03);

        FrameBuffer frame = machine.board().renderVideoFrame();
        int cursorX = Radio86VideoDevice.CELL_WIDTH;
        int cursorY = Radio86VideoDevice.CELL_HEIGHT - 1;

        assertEquals(0xFFA0A0A0, frame.pixels()[(cursorY * frame.width()) + cursorX]);
    }

    @Test
    void blinkingUnderlineCursorDisappearsAfterBlinkHalfPeriod() {
        Radio86Machine machine = new Radio86Machine(new byte[Radio86Memory.ROM_SIZE_2K]);

        initializeVideo(machine);
        machine.board().cpuBus().writeMemory(0xC001, 0x80);
        machine.board().cpuBus().writeMemory(0xC000, 0x08);
        machine.board().cpuBus().writeMemory(0xC000, 0x03);

        FrameBuffer visibleFrame = machine.board().renderVideoFrame();
        int cursorX = Radio86VideoDevice.CELL_WIDTH;
        int cursorY = Radio86VideoDevice.CELL_HEIGHT - 1;
        assertEquals(0xFFA0A0A0, visibleFrame.pixels()[(cursorY * visibleFrame.width()) + cursorX]);

        machine.board().onTStatesElapsed(machine.frameTStates() * 7, machine.frameTStates() * 7L);
        FrameBuffer hiddenFrame = machine.board().renderVideoFrame();
        assertEquals(0xFF000000, hiddenFrame.pixels()[(cursorY * hiddenFrame.width()) + cursorX]);
    }

    @Test
    void videoInterruptRequestIsPolledThroughCrtStatusRatherThanCpuIntr() {
        Radio86Machine machine = new Radio86Machine(new byte[Radio86Memory.ROM_SIZE_2K]);

        initializeVideo(machine);
        machine.board().onTStatesElapsed(machine.frameTStates(), machine.frameTStates());

        assertEquals(0x64, machine.board().cpuBus().readMemory(0xC001));
        assertFalse(machine.board().maskableInterruptLineActive(machine.currentTState()));
    }

    @Test
    void endOfRowControlCodeBlanksRemainingCells() {
        Radio86Machine machine = new Radio86Machine(new byte[Radio86Memory.ROM_SIZE_2K]);
        initializeVideo(machine);
        machine.board().memory().writeLowMemory(Radio86Memory.VIDEO_MEMORY_START + Radio86VideoDevice.VISIBLE_OFFSET, 'A');
        machine.board().memory().writeLowMemory(Radio86Memory.VIDEO_MEMORY_START + Radio86VideoDevice.VISIBLE_OFFSET + 1, 0xF0);
        machine.board().memory().writeLowMemory(Radio86Memory.VIDEO_MEMORY_START + Radio86VideoDevice.VISIBLE_OFFSET + 2, 'B');

        FrameBuffer frame = machine.board().renderVideoFrame();
        int secondCellX = Radio86VideoDevice.CELL_WIDTH;
        int thirdCellX = Radio86VideoDevice.CELL_WIDTH * 2;
        int litPixelsThirdCell = countLitPixels(frame, thirdCellX, 0);

        assertTrue(countLitPixels(frame, 0, 0) > 0);
        assertEquals(0, countLitPixels(frame, secondCellX, 0));
        assertEquals(0, litPixelsThirdCell);
    }

    @Test
    void inteAndTapeOutputGenerateAudioSamples() {
        Radio86Machine machine = new Radio86Machine(new byte[Radio86Memory.ROM_SIZE_2K]);
        byte[] pcm = new byte[256];

        machine.board().setCpuInteOutputHigh(true);
        machine.board().tape().setOutputHigh(true);
        machine.board().audio().setSpeakerHigh(true);
        machine.board().audio().setTapeOutputHigh(true);
        machine.board().audio().onTStatesElapsed(8_000);

        int copied = machine.board().audio().drainAudio(pcm, 0, pcm.length);

        assertTrue(copied > 0);
    }

    private void initializeVideo(Radio86Machine machine) {
        machine.board().cpuBus().writeMemory(0xC001, 0x00);
        machine.board().cpuBus().writeMemory(0xC000, 0x4D);
        machine.board().cpuBus().writeMemory(0xC000, 0x1D);
        machine.board().cpuBus().writeMemory(0xC000, 0x99);
        machine.board().cpuBus().writeMemory(0xC000, 0x93);
        machine.board().cpuBus().writeMemory(0xC001, 0x27);
        machine.board().cpuBus().writeMemory(0xE008, 0x80);
        machine.board().cpuBus().writeMemory(0xE004, 0xD0);
        machine.board().cpuBus().writeMemory(0xE004, 0x76);
        machine.board().cpuBus().writeMemory(0xE005, 0x23);
        machine.board().cpuBus().writeMemory(0xE005, 0x49);
        machine.board().cpuBus().writeMemory(0xE008, 0xA4);
    }

    private int countLitPixels(FrameBuffer frame, int startX, int startY) {
        int litPixels = 0;
        for (int y = 0; y < Radio86VideoDevice.CELL_HEIGHT; y++) {
            for (int x = 0; x < Radio86VideoDevice.CELL_WIDTH; x++) {
                int pixel = frame.pixels()[((startY + y) * frame.width()) + startX + x];
                if (pixel != 0xFF000000) {
                    litPixels++;
                }
            }
        }
        return litPixels;
    }
}
