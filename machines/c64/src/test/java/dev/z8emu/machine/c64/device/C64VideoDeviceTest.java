package dev.z8emu.machine.c64.device;

import dev.z8emu.machine.c64.C64Memory;
import dev.z8emu.platform.video.FrameBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64VideoDeviceTest {
    @Test
    void rasterProgressesAcrossAllPalLinesAndWraps() {
        C64VideoDevice video = videoWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);

        video.onTStatesElapsed(C64VideoDevice.CYCLES_PER_LINE);
        assertEquals(1, video.rasterLine());

        video.onTStatesElapsed(
                C64VideoDevice.CYCLES_PER_LINE * C64VideoDevice.LINES_PER_FRAME
                        - C64VideoDevice.CYCLES_PER_LINE - 1
        );
        assertEquals(311, video.rasterLine());
        assertEquals(0x37, video.readRegister(0x12));
        assertEquals(0x80, video.readRegister(0x11) & 0x80);

        video.onTStatesElapsed(1);
        assertEquals(0, video.rasterLine());
        assertEquals(0, video.readRegister(0x12));
        assertEquals(0, video.readRegister(0x11) & 0x80);
    }

    @Test
    void rasterRegisterReadsExposeLineThreeHundred() {
        C64VideoDevice video = videoWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);

        video.onTStatesElapsed(300 * C64VideoDevice.CYCLES_PER_LINE);

        assertEquals(0x2C, video.readRegister(0x12));
        assertEquals(0x80, video.readRegister(0x11) & 0x80);
    }

    @Test
    void rasterInterruptLatchesUsesMaskAndRequiresExplicitAcknowledgement() {
        C64VideoDevice video = videoWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        video.writeRegister(0x12, 100);
        video.writeRegister(0x11, 0x00);
        video.writeRegister(0x1A, 0x01);

        video.onTStatesElapsed(100 * C64VideoDevice.CYCLES_PER_LINE);

        assertEquals(0xF1, video.readRegister(0x19));
        assertTrue(video.interruptLineActive());
        video.writeRegister(0x19, 0x01);
        assertEquals(0x70, video.readRegister(0x19));
        assertFalse(video.interruptLineActive());

        video.reset();
        video.writeRegister(0x12, 100);
        video.onTStatesElapsed(100 * C64VideoDevice.CYCLES_PER_LINE);
        assertEquals(0x71, video.readRegister(0x19));
        assertFalse(video.interruptLineActive());
    }

    @Test
    void rasterCompareSupportsTheNinthBit() {
        C64VideoDevice video = videoWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        video.writeRegister(0x12, 300 & 0xFF);
        video.writeRegister(0x11, 0x80);
        video.writeRegister(0x1A, 0x01);

        video.onTStatesElapsed(300 * C64VideoDevice.CYCLES_PER_LINE);

        assertEquals(0xF1, video.readRegister(0x19));
        assertTrue(video.interruptLineActive());
    }

    @Test
    void disabledDisplayIsAllBorderAndEnabledDisplayDrawsText() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = new C64VideoDevice(memory);
        video.writeRegister(0x20, 0x02);

        FrameBuffer disabled = video.renderFrame(0x00);

        for (int pixel : disabled.pixels()) {
            assertEquals(C64VideoDevice.paletteArgb(2), pixel);
        }

        video.writeRegister(0x11, 0x10);
        video.writeRegister(0x18, 0x10);
        video.writeRegister(0x21, 0x06);
        memory.writeRam(0xC400, 0x01);
        memory.writeRam(0xC008, 0xA0);
        memory.writeColorRam(0, 0x01);

        FrameBuffer enabled = video.renderFrame(0x00);

        assertSame(disabled, enabled);
        assertPixel(enabled, 0, 0, C64VideoDevice.paletteArgb(2));
        assertPixel(enabled, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(1));
        assertPixel(enabled, C64VideoDevice.BORDER_LEFT + 1, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(6));
        assertPixel(enabled, C64VideoDevice.BORDER_LEFT + 2, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(1));
        assertPixel(enabled, C64VideoDevice.BORDER_LEFT + 3, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(6));
    }

    @Test
    void characterRomShadowsRamInVicBankZero() {
        byte[] chargenRom = new byte[C64Memory.CHAR_ROM_SIZE];
        chargenRom[0x08] = (byte) 0x80;
        C64Memory memory = memoryWithChargen(chargenRom);
        C64VideoDevice video = new C64VideoDevice(memory);
        video.writeRegister(0x11, 0x10);
        video.writeRegister(0x18, 0x14);
        video.writeRegister(0x21, 0x06);
        memory.writeRam(0x0400, 0x01);
        memory.writeRam(0x1008, 0x40);
        memory.writeColorRam(0, 0x01);

        FrameBuffer frame = video.renderFrame(0x03);

        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(1));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 1, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(6));
    }

    @Test
    void d018SelectsIndependentMatrixAndCharsetOffsets() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = new C64VideoDevice(memory);
        video.writeRegister(0x11, 0x10);
        video.writeRegister(0x18, 0x16);
        video.writeRegister(0x21, 0x06);
        memory.writeRam(0xC400, 0x02);
        memory.writeRam(0xD810, 0x40);
        memory.writeColorRam(0, 0x01);

        FrameBuffer frame = video.renderFrame(0x00);

        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(6));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 1, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(1));
    }

    @Test
    void registerReadsApplyFrozenUnusedBitAndStubRules() {
        C64VideoDevice video = videoWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        video.writeRegister(0x16, 0x15);
        video.writeRegister(0x18, 0xA4);
        video.writeRegister(0x1A, 0x05);
        video.writeRegister(0x20, 0x06);

        assertEquals(0xD5, video.readRegister(0x16));
        assertEquals(0xA5, video.readRegister(0x18));
        assertEquals(0xF5, video.readRegister(0x1A));
        assertEquals(0xF6, video.readRegister(0x20));
        assertEquals(0x00, video.readRegister(0x13));
        assertEquals(0x00, video.readRegister(0x14));
        assertEquals(0x00, video.readRegister(0x1E));
        assertEquals(0x00, video.readRegister(0x1F));
        assertEquals(0xFF, video.readRegister(0x2F));
        assertEquals(0xFF, video.readRegister(0x3F));
    }

    @Test
    void paletteMatchesTheFrozenPeptoSpotChecks() {
        assertEquals(0xFF000000, C64VideoDevice.paletteArgb(0));
        assertEquals(0xFFFFFFFF, C64VideoDevice.paletteArgb(1));
        assertEquals(0xFF352879, C64VideoDevice.paletteArgb(6));
        assertEquals(0xFF6C5EB5, C64VideoDevice.paletteArgb(14));
    }

    private static C64VideoDevice videoWithChargen(byte[] chargenRom) {
        return new C64VideoDevice(memoryWithChargen(chargenRom));
    }

    private static C64Memory memoryWithChargen(byte[] chargenRom) {
        return new C64Memory(
                new byte[C64Memory.BASIC_ROM_SIZE],
                new byte[C64Memory.KERNAL_ROM_SIZE],
                chargenRom
        );
    }

    private static void assertPixel(FrameBuffer frame, int x, int y, int expected) {
        assertEquals(expected, frame.pixels()[y * frame.width() + x]);
    }
}
