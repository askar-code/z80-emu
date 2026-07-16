package dev.z8emu.machine.c64.device;

import dev.z8emu.machine.c64.C64Memory;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64RasterSplitTest {
    private static final int BANK_THREE_BASE = 0xC000;
    private static final int BANK_TWO_BASE = 0x8000;
    private static final int MATRIX_OFFSET = 0x0400;
    private static final int SPRITE_POINTER_OFFSET = 0x07F8;
    private static final long LOCKED_RASTER_SPLIT_CRC = 0xC5205817L;

    @Test
    void backgroundSplitUsesTheBeamOnLineAndKeepsRasterIrqTiming() {
        Fixture fixture = fixture();
        configureText(fixture.video());
        fixture.video().writeRegister(0x12, 151);
        fixture.video().writeRegister(0x1A, 0x01);

        tickLines(fixture.video(), 151);

        assertEquals(151, fixture.video().rasterLine());
        assertTrue(fixture.video().interruptLineActive());
        assertEquals(0xF1, fixture.video().readRegister(0x19));
        fixture.video().writeRegister(0x21, 0x05);
        tickLines(fixture.video(), 160);

        FrameBuffer frame = fixture.video().renderFrame(0x00);

        for (int windowY = 0; windowY < 100; windowY++) {
            assertWindowPixel(frame, 100, windowY, 6);
        }
        for (int windowY = 100; windowY < 200; windowY++) {
            assertWindowPixel(frame, 100, windowY, 5);
        }
    }

    @Test
    void charsetSplitFetchesTheLiveD018Value() {
        Fixture fixture = fixture();
        configureText(fixture.video());
        fillMatrix(fixture.memory(), BANK_THREE_BASE, 0x01, 0x01);
        fillGlyph(fixture.memory(), BANK_THREE_BASE, 0x0000, 0x01, 0x80);
        fillGlyph(fixture.memory(), BANK_THREE_BASE, 0x0800, 0x01, 0x01);

        tickLines(fixture.video(), 151);
        fixture.video().writeRegister(0x18, 0x12);
        tickLines(fixture.video(), 160);

        FrameBuffer frame = fixture.video().renderFrame(0x00);

        assertWindowPixel(frame, 0, 99, 1);
        assertWindowPixel(frame, 7, 99, 6);
        assertWindowPixel(frame, 0, 100, 6);
        assertWindowPixel(frame, 7, 100, 1);
    }

    @Test
    void borderColorChangesPaintAllRasterBands() {
        Fixture fixture = fixture();
        configureText(fixture.video());

        tickLines(fixture.video(), 40);
        fixture.video().writeRegister(0x20, 0x03);
        tickLines(fixture.video(), 111);
        fixture.video().writeRegister(0x20, 0x04);
        tickLines(fixture.video(), 109);
        fixture.video().writeRegister(0x20, 0x05);
        tickLines(fixture.video(), 51);

        FrameBuffer frame = fixture.video().renderFrame(0x00);

        assertRasterPixel(frame, 0, 20, 2);
        assertRasterPixel(frame, 0, 39, 2);
        assertRasterPixel(frame, 0, 40, 3);
        assertRasterPixel(frame, 0, 150, 3);
        assertRasterPixel(frame, 0, 151, 4);
        assertRasterPixel(frame, 0, 259, 4);
        assertRasterPixel(frame, 0, 260, 5);
        assertRasterPixel(frame, 0, 286, 5);
    }

    @Test
    void denSplitSuppressesGraphicsSpritesAndNewCollisions() {
        Fixture fixture = fixture();
        configureText(fixture.video());
        fillMatrix(fixture.memory(), BANK_THREE_BASE, 0x01, 0x01);
        fillGlyph(fixture.memory(), BANK_THREE_BASE, 0x0000, 0x01, 0xFF);
        configureSprite(fixture, 0, 24, 170, 0x20, 3);
        configureSprite(fixture, 1, 24, 170, 0x21, 4);
        writeSpriteRow(fixture.memory(), BANK_THREE_BASE, 0x20, 0, 0x800000);
        writeSpriteRow(fixture.memory(), BANK_THREE_BASE, 0x21, 0, 0x800000);
        fixture.video().writeRegister(0x15, 0x03);

        tickLines(fixture.video(), 151);
        fixture.video().writeRegister(0x11, 0x0B);
        tickLines(fixture.video(), 160);

        FrameBuffer frame = fixture.video().renderFrame(0x00);

        assertWindowPixel(frame, 100, 99, 1);
        for (int windowY = 100; windowY < 200; windowY++) {
            assertWindowPixel(frame, 100, windowY, 2);
        }
        assertEquals(0x00, fixture.video().readRegister(0x1E));
        assertEquals(0x00, fixture.video().readRegister(0x1F));
    }

    @Test
    void yScrollSplitUsesTheLiveSourceRowAtTheSeam() {
        Fixture fixture = fixture();
        configureText(fixture.video());
        fillMatrix(fixture.memory(), BANK_THREE_BASE, 0x01, 0x01);
        for (int glyphRow = 0; glyphRow < C64VideoDevice.CELL_SIZE; glyphRow++) {
            fixture.memory().writeRam(BANK_THREE_BASE + 0x08 + glyphRow, 1 << (7 - glyphRow));
        }

        tickLines(fixture.video(), 151);
        fixture.video().writeRegister(0x11, 0x1D);
        tickLines(fixture.video(), 160);

        FrameBuffer frame = fixture.video().renderFrame(0x00);

        assertWindowPixel(frame, 0, 99, 6);
        assertWindowPixel(frame, 3, 99, 1);
        assertWindowPixel(frame, 2, 100, 1);
        assertWindowPixel(frame, 3, 100, 6);
        assertWindowPixel(frame, 0, 101, 6);
        assertWindowPixel(frame, 3, 101, 1);
    }

    @Test
    void spriteMultiplexUsesLiveCoordinatesPointersAndRamRows() {
        Fixture fixture = fixture();
        configureText(fixture.video());
        fixture.memory().writeRam(BANK_THREE_BASE + MATRIX_OFFSET + C64VideoDevice.TEXT_COLUMNS,
                0x01);
        fixture.memory().writeColorRam(C64VideoDevice.TEXT_COLUMNS, 0x01);
        fixture.memory().writeRam(BANK_THREE_BASE + 0x08 + 2, 0x80);
        int bottomCell = 15 * C64VideoDevice.TEXT_COLUMNS + 2;
        fixture.memory().writeRam(BANK_THREE_BASE + MATRIX_OFFSET + bottomCell, 0x02);
        fixture.memory().writeColorRam(bottomCell, 0x01);
        fixture.memory().writeRam(BANK_THREE_BASE + 0x10, 0x01);
        configureSprite(fixture, 0, 24, 60, 0x20, 3);
        writeSpriteRow(fixture.memory(), BANK_THREE_BASE, 0x20, 0, 0x800000);
        writeSpriteRow(fixture.memory(), BANK_THREE_BASE, 0x21, 0, 0x000001);
        fixture.video().writeRegister(0x15, 0x01);

        tickLines(fixture.video(), 151);
        assertEquals(0x01, fixture.video().readRegister(0x1F));
        assertEquals(0x00, fixture.video().readRegister(0x1F));
        fixture.video().writeRegister(0x01, 170);
        fixture.memory().writeRam(BANK_THREE_BASE + SPRITE_POINTER_OFFSET, 0x21);
        tickLines(fixture.video(), 160);

        FrameBuffer frame = fixture.video().renderFrame(0x00);

        assertWindowPixel(frame, 0, 10, 3);
        assertWindowPixel(frame, 23, 10, 6);
        assertWindowPixel(frame, 0, 120, 6);
        assertWindowPixel(frame, 23, 120, 3);
        assertEquals(0x01, fixture.video().readRegister(0x1F));
    }

    @Test
    void ciaPortSwitchChangesTheLiveVicBank() {
        Fixture fixture = fixture();
        configureText(fixture.video());
        fillMatrix(fixture.memory(), BANK_THREE_BASE, 0x01, 0x01);
        fillMatrix(fixture.memory(), BANK_TWO_BASE, 0x01, 0x01);
        fillGlyph(fixture.memory(), BANK_THREE_BASE, 0x0000, 0x01, 0x80);
        fillGlyph(fixture.memory(), BANK_TWO_BASE, 0x0000, 0x01, 0x01);

        tickLines(fixture.video(), 151);
        fixture.cia2().writeRegister(0x00, 0x01);
        tickLines(fixture.video(), 160);

        FrameBuffer frame = fixture.video().renderFrame(0x00);

        assertWindowPixel(frame, 0, 99, 1);
        assertWindowPixel(frame, 7, 99, 6);
        assertWindowPixel(frame, 0, 100, 6);
        assertWindowPixel(frame, 7, 100, 1);
    }

    @Test
    void staticScenesMatchSnapshotTailAndFullyProgressiveRendering() {
        for (Scene scene : Scene.values()) {
            Fixture fixture = fixture();
            seedScene(fixture, scene);
            applySceneRegisters(fixture, scene);
            int[] expected = fixture.video().renderFrame(0x00).pixels().clone();

            tickLines(fixture.video(), 200);
            assertArrayEquals(expected, fixture.video().renderFrame(0x00).pixels(),
                    scene + " snapshot tail");

            fixture.video().reset();
            applySceneRegisters(fixture, scene);
            tickLines(fixture.video(), 311);
            assertArrayEquals(expected, fixture.video().renderFrame(0x00).pixels(),
                    scene + " progressive");
        }
    }

    @Test
    void completedFramesAreReturnedWithoutRepaintingOrRelatching() {
        Fixture fixture = fixture();
        configureText(fixture.video());
        fillMatrix(fixture.memory(), BANK_THREE_BASE, 0x01, 0x01);
        fillGlyph(fixture.memory(), BANK_THREE_BASE, 0x0000, 0x01, 0xFF);
        configureSprite(fixture, 0, 24, 50, 0x20, 3);
        writeSpriteRow(fixture.memory(), BANK_THREE_BASE, 0x20, 0, 0x800000);
        fixture.video().writeRegister(0x15, 0x01);

        tickLines(fixture.video(), 312);
        assertEquals(0x01, fixture.video().readRegister(0x1F));
        fixture.video().writeRegister(0x20, 0x05);

        FrameBuffer completed = fixture.video().renderFrame(0x00);

        assertRasterPixel(completed, 0, 15, 2);
        assertEquals(0x00, fixture.video().readRegister(0x1F));
        tickLines(fixture.video(), 16);
        assertSame(completed, fixture.video().renderFrame(0x00));
        assertRasterPixel(completed, 0, 15, 5);
    }

    @Test
    void lockedCompositeScenePinsRasterBarsBackgroundSplitAndMultiplexing() {
        Fixture fixture = fixture();
        configureText(fixture.video());
        fillMatrix(fixture.memory(), BANK_THREE_BASE, 0x01, 0x01);
        fillGlyph(fixture.memory(), BANK_THREE_BASE, 0x0000, 0x01, 0x18);
        configureSprite(fixture, 0, 24, 60, 0x20, 3);
        writeSpriteRow(fixture.memory(), BANK_THREE_BASE, 0x20, 0, 0x800000);
        writeSpriteRow(fixture.memory(), BANK_THREE_BASE, 0x21, 0, 0x000001);
        fixture.video().writeRegister(0x15, 0x01);

        tickLines(fixture.video(), 40);
        fixture.video().writeRegister(0x20, 0x03);
        tickLines(fixture.video(), 111);
        fixture.video().writeRegister(0x20, 0x04);
        fixture.video().writeRegister(0x21, 0x05);
        fixture.video().writeRegister(0x01, 170);
        fixture.memory().writeRam(BANK_THREE_BASE + SPRITE_POINTER_OFFSET, 0x21);
        tickLines(fixture.video(), 109);
        fixture.video().writeRegister(0x20, 0x05);
        tickLines(fixture.video(), 51);

        FrameBuffer frame = fixture.video().renderFrame(0x00);

        assertEquals(LOCKED_RASTER_SPLIT_CRC, frameCrc32(frame));
    }

    @Test
    void cselOverlayPaintsBorderOverTheInvalidModeBlackout() {
        Fixture fixture = fixture();
        configureText(fixture.video());
        fixture.video().writeRegister(0x11, 0x7B);
        fixture.video().writeRegister(0x16, 0x00);

        FrameBuffer frame = fixture.video().renderFrame(0x00);

        assertWindowPixel(frame, 3, 100, 2);
        assertWindowPixel(frame, 314, 100, 2);
        assertWindowPixel(frame, 100, 100, 0);
    }

    private static Fixture fixture() {
        C64Memory memory = new C64Memory(
                new byte[C64Memory.BASIC_ROM_SIZE],
                new byte[C64Memory.KERNAL_ROM_SIZE],
                new byte[C64Memory.CHAR_ROM_SIZE]
        );
        C64CiaDevice cia2 = new C64CiaDevice();
        cia2.reset();
        cia2.writeRegister(0x02, 0x03);
        cia2.writeRegister(0x00, 0x00);
        return new Fixture(memory, cia2, new C64VideoDevice(memory, cia2));
    }

    private static void configureText(C64VideoDevice video) {
        video.writeRegister(0x11, 0x1B);
        video.writeRegister(0x16, 0x08);
        video.writeRegister(0x18, 0x10);
        video.writeRegister(0x20, 0x02);
        video.writeRegister(0x21, 0x06);
    }

    private static void fillMatrix(C64Memory memory, int bankBase, int screenCode, int color) {
        for (int cell = 0; cell < C64VideoDevice.TEXT_COLUMNS * C64VideoDevice.TEXT_ROWS; cell++) {
            memory.writeRam(bankBase + MATRIX_OFFSET + cell, screenCode);
            memory.writeColorRam(cell, color);
        }
    }

    private static void fillGlyph(C64Memory memory, int bankBase, int charsetOffset,
            int glyph, int bits) {
        for (int row = 0; row < C64VideoDevice.CELL_SIZE; row++) {
            memory.writeRam(bankBase + charsetOffset + glyph * C64VideoDevice.CELL_SIZE + row, bits);
        }
    }

    private static void configureSprite(Fixture fixture, int sprite, int x, int y,
            int pointer, int color) {
        configureSpriteRegisters(fixture.video(), sprite, x, y, color);
        fixture.memory().writeRam(BANK_THREE_BASE + SPRITE_POINTER_OFFSET + sprite, pointer);
    }

    private static void configureSpriteRegisters(C64VideoDevice video, int sprite, int x, int y,
            int color) {
        video.writeRegister(sprite * 2, x);
        video.writeRegister(sprite * 2 + 1, y);
        int xMsb = video.readRegister(0x10);
        xMsb = (xMsb & ~(1 << sprite)) | (((x >>> 8) & 0x01) << sprite);
        video.writeRegister(0x10, xMsb);
        video.writeRegister(0x27 + sprite, color);
    }

    private static void writeSpriteRow(C64Memory memory, int bankBase, int pointer,
            int sourceY, int bits) {
        int address = bankBase + pointer * 64 + sourceY * 3;
        memory.writeRam(address, bits >>> 16);
        memory.writeRam(address + 1, bits >>> 8);
        memory.writeRam(address + 2, bits);
    }

    private static void tickLines(C64VideoDevice video, int lines) {
        for (int line = 0; line < lines; line++) {
            video.onTStatesElapsed(C64VideoDevice.CYCLES_PER_LINE);
        }
    }

    private static void seedScene(Fixture fixture, Scene scene) {
        fillMatrix(fixture.memory(), BANK_THREE_BASE, 0x01, 0x01);
        for (int row = 0; row < C64VideoDevice.CELL_SIZE; row++) {
            fixture.memory().writeRam(BANK_THREE_BASE + 0x08 + row,
                    Integer.rotateRight(0x81, row) & 0xFF);
        }
        if (scene == Scene.MULTICOLOR_TEXT) {
            fillGlyph(fixture.memory(), BANK_THREE_BASE, 0x0000, 0x01, 0x1B);
            for (int cell = 0; cell < C64VideoDevice.TEXT_COLUMNS * C64VideoDevice.TEXT_ROWS; cell++) {
                fixture.memory().writeColorRam(cell, 0x0B);
            }
        }
        if (scene == Scene.HIRES_BITMAP) {
            for (int cell = 0; cell < C64VideoDevice.TEXT_COLUMNS * C64VideoDevice.TEXT_ROWS; cell++) {
                fixture.memory().writeRam(BANK_THREE_BASE + MATRIX_OFFSET + cell,
                        ((cell % 15) + 1) << 4 | ((cell / C64VideoDevice.TEXT_COLUMNS) % 15) + 1);
                for (int row = 0; row < C64VideoDevice.CELL_SIZE; row++) {
                    fixture.memory().writeRam(BANK_THREE_BASE + 0x2000
                            + cell * C64VideoDevice.CELL_SIZE + row, (cell + row) % 2 == 0 ? 0xAA : 0x55);
                }
            }
        }
        if (scene == Scene.EXTENDED_COLOR) {
            for (int cell = 0; cell < C64VideoDevice.TEXT_COLUMNS * C64VideoDevice.TEXT_ROWS; cell++) {
                fixture.memory().writeRam(BANK_THREE_BASE + MATRIX_OFFSET + cell,
                        ((cell & 0x03) << 6) | 0x01);
            }
        }
        if (scene == Scene.SPRITES_WITH_COLLISIONS) {
            fixture.memory().writeRam(BANK_THREE_BASE + SPRITE_POINTER_OFFSET, 0x20);
            fixture.memory().writeRam(BANK_THREE_BASE + SPRITE_POINTER_OFFSET + 1, 0x21);
            writeSpriteRow(fixture.memory(), BANK_THREE_BASE, 0x20, 0, 0xC00000);
            writeSpriteRow(fixture.memory(), BANK_THREE_BASE, 0x21, 0, 0x800000);
        }
    }

    private static void applySceneRegisters(Fixture fixture, Scene scene) {
        configureText(fixture.video());
        switch (scene) {
            case STANDARD_TEXT -> {
            }
            case MULTICOLOR_TEXT -> {
                fixture.video().writeRegister(0x16, 0x18);
                fixture.video().writeRegister(0x22, 0x02);
                fixture.video().writeRegister(0x23, 0x04);
            }
            case HIRES_BITMAP -> {
                fixture.video().writeRegister(0x11, 0x3B);
                fixture.video().writeRegister(0x18, 0x18);
            }
            case EXTENDED_COLOR -> {
                fixture.video().writeRegister(0x11, 0x5B);
                fixture.video().writeRegister(0x22, 0x03);
                fixture.video().writeRegister(0x23, 0x04);
                fixture.video().writeRegister(0x24, 0x05);
            }
            case SCROLLED -> {
                fixture.video().writeRegister(0x11, 0x1D);
                fixture.video().writeRegister(0x16, 0x0B);
            }
            case SPRITES_WITH_COLLISIONS -> {
                configureSpriteRegisters(fixture.video(), 0, 24, 60, 3);
                configureSpriteRegisters(fixture.video(), 1, 24, 60, 4);
                fixture.video().writeRegister(0x15, 0x03);
            }
            case NARROW_WINDOW -> {
                fixture.video().writeRegister(0x11, 0x13);
                fixture.video().writeRegister(0x16, 0x00);
            }
        }
    }

    private static void assertRasterPixel(FrameBuffer frame, int x, int rasterLine, int colorIndex) {
        int frameY = rasterLine - 15;
        assertEquals(C64VideoDevice.paletteArgb(colorIndex), frame.pixels()[frameY * frame.width() + x]);
    }

    private static void assertWindowPixel(FrameBuffer frame, int x, int y, int colorIndex) {
        int frameX = C64VideoDevice.BORDER_LEFT + x;
        int frameY = C64VideoDevice.BORDER_TOP + y;
        assertEquals(C64VideoDevice.paletteArgb(colorIndex),
                frame.pixels()[frameY * frame.width() + frameX]);
    }

    private static long frameCrc32(FrameBuffer frame) {
        CRC32 crc = new CRC32();
        for (int pixel : frame.pixels()) {
            crc.update(pixel >>> 24);
            crc.update(pixel >>> 16);
            crc.update(pixel >>> 8);
            crc.update(pixel);
        }
        return crc.getValue();
    }

    private record Fixture(C64Memory memory, C64CiaDevice cia2, C64VideoDevice video) {
    }

    private enum Scene {
        STANDARD_TEXT,
        MULTICOLOR_TEXT,
        HIRES_BITMAP,
        EXTENDED_COLOR,
        SCROLLED,
        SPRITES_WITH_COLLISIONS,
        NARROW_WINDOW
    }
}
