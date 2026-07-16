package dev.z8emu.machine.c64.device;

import dev.z8emu.machine.c64.C64Memory;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64VideoDeviceTest {
    private static final long MCM_OFF_REFERENCE_CRC = 0x4538AE0EL;
    private static final long LOCKED_COMPOSITE_SCENE_CRC = 0xFF91FF85L;
    private static final int VIC_BANK_BASE = 0xC000;
    private static final int MATRIX_ADDRESS = 0xC400;
    private static final int SPRITE_POINTER_ADDRESS = 0xC7F8;

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
    void mcmOffRenderingMatchesPhaseTwoReference() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = new C64VideoDevice(memory);
        video.writeRegister(0x20, 0x02);
        video.writeRegister(0x11, 0x10);
        video.writeRegister(0x18, 0x10);
        video.writeRegister(0x21, 0x06);
        memory.writeRam(0xC400, 0x01);
        memory.writeRam(0xC008, 0xA0);
        memory.writeColorRam(0, 0x01);

        assertEquals(MCM_OFF_REFERENCE_CRC, frameCrc32(video.renderFrame(0x00)));
    }

    @Test
    void spriteGeometryUsesFrozenCoordinatesAndClipsToTextWindow() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = bankThreeVideo(memory);
        configureSprite(memory, video, 0, 24, 50, 0x20, 5);
        video.writeRegister(0x15, 0x01);
        writeSpriteByte(memory, 0x20, 0, 0x80);
        writeSpriteByte(memory, 0x20, 2, 0x01);
        writeSpriteByte(memory, 0x20, 60, 0x80);
        writeSpriteByte(memory, 0x20, 62, 0x01);

        FrameBuffer frame = video.renderFrame(0x00);

        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(5));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 23, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(5));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP + 20,
                C64VideoDevice.paletteArgb(5));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 23, C64VideoDevice.BORDER_TOP + 20,
                C64VideoDevice.paletteArgb(5));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 1, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(6));

        configureSprite(memory, video, 0, 343, 50, 0x20, 5);
        writeSpriteByte(memory, 0x20, 0, 0xC0);
        frame = video.renderFrame(0x00);
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 319, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(5));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 318, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(6));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 320, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(2));

        configureSprite(memory, video, 0, 300, 50, 0x20, 5);
        frame = video.renderFrame(0x00);
        assertPixel(frame, 308, C64VideoDevice.BORDER_TOP, C64VideoDevice.paletteArgb(5));
        assertEquals(0x2C, video.readRegister(0x00));
        assertEquals(0x01, video.readRegister(0x10) & 0x01);

        configureSprite(memory, video, 0, 24, 49, 0x20, 5);
        writeSpriteByte(memory, 0x20, 3, 0x80);
        frame = video.renderFrame(0x00);
        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP - 1,
                C64VideoDevice.paletteArgb(2));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(5));

        configureSprite(memory, video, 0, 24, 249, 0x20, 5);
        frame = video.renderFrame(0x00);
        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP + 199,
                C64VideoDevice.paletteArgb(5));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP + 200,
                C64VideoDevice.paletteArgb(2));
    }

    @Test
    void spriteExpansionDoublesHiresPixelsAndRows() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = bankThreeVideo(memory);
        configureSprite(memory, video, 0, 24, 50, 0x20, 5);
        writeSpriteByte(memory, 0x20, 0, 0x80);
        video.writeRegister(0x15, 0x01);
        video.writeRegister(0x17, 0x01);
        video.writeRegister(0x1D, 0x01);

        FrameBuffer frame = video.renderFrame(0x00);

        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(5));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 1, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(5));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP + 1,
                C64VideoDevice.paletteArgb(5));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 1, C64VideoDevice.BORDER_TOP + 1,
                C64VideoDevice.paletteArgb(5));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + 2, C64VideoDevice.BORDER_TOP,
                C64VideoDevice.paletteArgb(6));
        assertPixel(frame, C64VideoDevice.BORDER_LEFT, C64VideoDevice.BORDER_TOP + 2,
                C64VideoDevice.paletteArgb(6));
    }

    @Test
    void multicolorSpriteUsesSharedAndIndividualColorsWithFrozenPairWidths() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = bankThreeVideo(memory);
        configureSprite(memory, video, 0, 24, 50, 0x20, 3);
        writeSpriteByte(memory, 0x20, 0, 0x1B);
        video.writeRegister(0x15, 0x01);
        video.writeRegister(0x1C, 0x01);
        video.writeRegister(0x25, 0x04);
        video.writeRegister(0x26, 0x05);

        FrameBuffer frame = video.renderFrame(0x00);

        assertWindowPixel(frame, 0, 0, 6);
        assertWindowPixel(frame, 1, 0, 6);
        assertWindowPixel(frame, 2, 0, 4);
        assertWindowPixel(frame, 3, 0, 4);
        assertWindowPixel(frame, 4, 0, 3);
        assertWindowPixel(frame, 5, 0, 3);
        assertWindowPixel(frame, 6, 0, 5);
        assertWindowPixel(frame, 7, 0, 5);

        video.writeRegister(0x1D, 0x01);
        frame = video.renderFrame(0x00);
        assertWindowPixel(frame, 3, 0, 6);
        assertWindowPixel(frame, 4, 0, 4);
        assertWindowPixel(frame, 7, 0, 4);
        assertWindowPixel(frame, 8, 0, 3);
        assertWindowPixel(frame, 11, 0, 3);
        assertWindowPixel(frame, 12, 0, 5);
        assertWindowPixel(frame, 15, 0, 5);
        assertWindowPixel(frame, 16, 0, 6);
    }

    @Test
    void spritePriorityHidesOnlyBehindTextForegroundAndStillCollides() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = bankThreeVideo(memory);
        memory.writeRam(MATRIX_ADDRESS, 0x01);
        memory.writeRam(VIC_BANK_BASE + 0x08, 0x80);
        memory.writeColorRam(0, 0x01);
        configureSprite(memory, video, 0, 24, 50, 0x20, 2);
        writeSpriteByte(memory, 0x20, 0, 0xC0);
        video.writeRegister(0x15, 0x01);
        video.writeRegister(0x1B, 0x01);

        FrameBuffer frame = video.renderFrame(0x00);

        assertWindowPixel(frame, 0, 0, 1);
        assertWindowPixel(frame, 1, 0, 2);
        assertEquals(0x01, video.readRegister(0x1F));
        assertEquals(0x00, video.readRegister(0x1F));

        video.writeRegister(0x1B, 0x00);
        frame = video.renderFrame(0x00);
        assertWindowPixel(frame, 0, 0, 2);
    }

    @Test
    void spriteSpriteCollisionIsReadClearAndRequiresOpaqueEnabledOverlap() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = bankThreeVideo(memory);
        configureSprite(memory, video, 0, 24, 50, 0x20, 2);
        configureSprite(memory, video, 3, 24, 50, 0x23, 5);
        writeSpriteByte(memory, 0x20, 0, 0x80);
        writeSpriteByte(memory, 0x23, 0, 0x80);
        video.writeRegister(0x15, 0x09);

        video.renderFrame(0x00);

        assertEquals(0x09, video.readRegister(0x1E));
        assertEquals(0x00, video.readRegister(0x1E));
        video.writeRegister(0x1E, 0xFF);
        assertEquals(0x00, video.readRegister(0x1E));
        video.writeRegister(0x1F, 0xFF);
        assertEquals(0x00, video.readRegister(0x1F));

        configureSprite(memory, video, 3, 25, 50, 0x23, 5);
        video.renderFrame(0x00);
        assertEquals(0x00, video.readRegister(0x1E));

        configureSprite(memory, video, 3, 24, 50, 0x23, 5);
        video.writeRegister(0x15, 0x01);
        video.renderFrame(0x00);
        assertEquals(0x00, video.readRegister(0x1E));

        video.writeRegister(0x15, 0x09);
        video.renderFrame(0x00);
        video.reset();
        assertEquals(0x00, video.readRegister(0x1E));
        assertEquals(0x00, video.readRegister(0x1F));
    }

    @Test
    void disabledDisplaySuppressesSpritesAndTheirCollisions() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = bankThreeVideo(memory);
        configureSprite(memory, video, 0, 24, 50, 0x20, 2);
        configureSprite(memory, video, 1, 24, 50, 0x21, 5);
        writeSpriteByte(memory, 0x20, 0, 0x80);
        writeSpriteByte(memory, 0x21, 0, 0x80);
        video.writeRegister(0x15, 0x03);
        video.writeRegister(0x11, 0x00);

        FrameBuffer frame = video.renderFrame(0x00);

        for (int pixel : frame.pixels()) {
            assertEquals(C64VideoDevice.paletteArgb(2), pixel);
        }
        assertEquals(0x00, video.readRegister(0x1E));
        assertEquals(0x00, video.readRegister(0x1F));
    }

    @Test
    void spriteDataCollisionUsesHiresAndMulticolorForegroundRules() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = bankThreeVideo(memory);
        memory.writeRam(MATRIX_ADDRESS, 0x01);
        memory.writeColorRam(0, 0x01);
        configureSprite(memory, video, 0, 24, 50, 0x20, 2);
        writeSpriteByte(memory, 0x20, 0, 0x80);
        video.writeRegister(0x15, 0x01);

        video.renderFrame(0x00);
        assertEquals(0x00, video.readRegister(0x1F));

        memory.writeRam(VIC_BANK_BASE + 0x08, 0x80);
        video.renderFrame(0x00);
        assertEquals(0x01, video.readRegister(0x1F));

        video.writeRegister(0x16, 0x10);
        memory.writeColorRam(0, 0x0B);
        memory.writeRam(VIC_BANK_BASE + 0x08, 0x40);
        video.renderFrame(0x00);
        assertEquals(0x00, video.readRegister(0x1F));

        memory.writeRam(VIC_BANK_BASE + 0x08, 0x80);
        video.renderFrame(0x00);
        assertEquals(0x01, video.readRegister(0x1F));
    }

    @Test
    void lowerSpriteIndexPaintsOnTop() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = bankThreeVideo(memory);
        configureSprite(memory, video, 0, 24, 50, 0x20, 2);
        configureSprite(memory, video, 1, 24, 50, 0x21, 5);
        writeSpriteByte(memory, 0x20, 0, 0x80);
        writeSpriteByte(memory, 0x21, 0, 0x80);
        video.writeRegister(0x15, 0x03);

        FrameBuffer frame = video.renderFrame(0x00);

        assertWindowPixel(frame, 0, 0, 2);
    }

    @Test
    void multicolorTextUsesPairColorsAndLeavesLowColorCellsInHires() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = bankThreeVideo(memory);
        video.writeRegister(0x16, 0x10);
        video.writeRegister(0x22, 0x02);
        video.writeRegister(0x23, 0x04);
        memory.writeRam(MATRIX_ADDRESS, 0x01);
        memory.writeRam(MATRIX_ADDRESS + 1, 0x02);
        memory.writeRam(VIC_BANK_BASE + 0x08, 0x1B);
        memory.writeRam(VIC_BANK_BASE + 0x10, 0x80);
        memory.writeColorRam(0, 0x0B);
        memory.writeColorRam(1, 0x07);

        FrameBuffer frame = video.renderFrame(0x00);

        assertWindowPixel(frame, 0, 0, 6);
        assertWindowPixel(frame, 1, 0, 6);
        assertWindowPixel(frame, 2, 0, 2);
        assertWindowPixel(frame, 3, 0, 2);
        assertWindowPixel(frame, 4, 0, 4);
        assertWindowPixel(frame, 5, 0, 4);
        assertWindowPixel(frame, 6, 0, 3);
        assertWindowPixel(frame, 7, 0, 3);
        assertWindowPixel(frame, 8, 0, 7);
        assertWindowPixel(frame, 9, 0, 6);
    }

    @Test
    void spriteColorRegistersKeepTheirUnusedReadBits() {
        C64VideoDevice video = videoWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        video.writeRegister(0x25, 0x01);
        video.writeRegister(0x26, 0x02);
        video.writeRegister(0x27, 0x03);
        video.writeRegister(0x2E, 0x04);

        assertEquals(0xF1, video.readRegister(0x25));
        assertEquals(0xF2, video.readRegister(0x26));
        assertEquals(0xF3, video.readRegister(0x27));
        assertEquals(0xF4, video.readRegister(0x2E));
    }

    @Test
    void lockedCompositeSpriteAndMulticolorTextScene() {
        C64Memory memory = memoryWithChargen(new byte[C64Memory.CHAR_ROM_SIZE]);
        C64VideoDevice video = bankThreeVideo(memory);
        video.writeRegister(0x16, 0x10);
        video.writeRegister(0x22, 0x05);
        video.writeRegister(0x23, 0x07);
        memory.writeRam(MATRIX_ADDRESS, 0x01);
        memory.writeRam(VIC_BANK_BASE + 0x08, 0x1B);
        memory.writeColorRam(0, 0x0B);

        configureSprite(memory, video, 0, 24, 60, 0x20, 2);
        writeSpriteByte(memory, 0x20, 0, 0x80);
        configureSprite(memory, video, 1, 40, 58, 0x21, 3);
        writeSpriteByte(memory, 0x21, 0, 0x6C);
        configureSprite(memory, video, 2, 24, 50, 0x22, 4);
        writeSpriteByte(memory, 0x22, 0, 0x88);
        configureSprite(memory, video, 3, 24, 60, 0x23, 8);
        writeSpriteByte(memory, 0x23, 0, 0x80);
        video.writeRegister(0x15, 0x0F);
        video.writeRegister(0x1B, 0x04);
        video.writeRegister(0x1C, 0x02);
        video.writeRegister(0x25, 0x09);
        video.writeRegister(0x26, 0x0A);

        FrameBuffer frame = video.renderFrame(0x00);

        assertEquals(LOCKED_COMPOSITE_SCENE_CRC, frameCrc32(frame));
        assertEquals(0x09, video.readRegister(0x1E));
        assertEquals(0x00, video.readRegister(0x1E));
        assertEquals(0x04, video.readRegister(0x1F));
        assertEquals(0x00, video.readRegister(0x1F));
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
    void paletteMatchesTheFrozenColodoreSpotChecks() {
        assertEquals(0xFF000000, C64VideoDevice.paletteArgb(0));
        assertEquals(0xFFFFFFFF, C64VideoDevice.paletteArgb(1));
        assertEquals(0xFF2E2C9B, C64VideoDevice.paletteArgb(6));
        assertEquals(0xFF706DEB, C64VideoDevice.paletteArgb(14));
    }

    private static C64VideoDevice videoWithChargen(byte[] chargenRom) {
        return new C64VideoDevice(memoryWithChargen(chargenRom));
    }

    private static C64VideoDevice bankThreeVideo(C64Memory memory) {
        C64VideoDevice video = new C64VideoDevice(memory);
        video.writeRegister(0x11, 0x10);
        video.writeRegister(0x18, 0x10);
        video.writeRegister(0x20, 0x02);
        video.writeRegister(0x21, 0x06);
        return video;
    }

    private static void configureSprite(C64Memory memory, C64VideoDevice video, int sprite,
            int x, int y, int pointer, int color) {
        video.writeRegister(sprite * 2, x);
        video.writeRegister(sprite * 2 + 1, y);
        int xMsb = video.readRegister(0x10);
        xMsb = (xMsb & ~(1 << sprite)) | (((x >>> 8) & 0x01) << sprite);
        video.writeRegister(0x10, xMsb);
        video.writeRegister(0x27 + sprite, color);
        memory.writeRam(SPRITE_POINTER_ADDRESS + sprite, pointer);
    }

    private static void writeSpriteByte(C64Memory memory, int pointer, int offset, int value) {
        memory.writeRam(VIC_BANK_BASE + pointer * 64 + offset, value);
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

    private static void assertWindowPixel(FrameBuffer frame, int x, int y, int colorIndex) {
        assertPixel(frame, C64VideoDevice.BORDER_LEFT + x, C64VideoDevice.BORDER_TOP + y,
                C64VideoDevice.paletteArgb(colorIndex));
    }

    private static long frameCrc32(FrameBuffer frame) {
        CRC32 crc = new CRC32();
        for (int pixel : frame.pixels()) {
            crc.update((pixel >>> 24) & 0xFF);
            crc.update((pixel >>> 16) & 0xFF);
            crc.update((pixel >>> 8) & 0xFF);
            crc.update(pixel & 0xFF);
        }
        return crc.getValue();
    }
}
