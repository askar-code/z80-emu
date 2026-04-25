package dev.z8emu.machine.spectrum48k.tape;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TzxLoaderTest {
    @Test
    void parsesStandardSpeedDataBlock() throws Exception {
        byte[] tzx = tzx(
                block(0x10, le16(1_000), le16(3), bytes(0x00, 0x11, 0x22))
        );

        TapeFile tapeFile = TzxLoader.load(new ByteArrayInputStream(tzx));
        TapeBlock block = tapeFile.blocks().get(0);

        assertEquals(1, tapeFile.blocks().size());
        assertEquals(8_065, block.prefixPulseLengthsTStates().length);
        assertEquals(2_168, block.prefixPulseLengthsTStates()[0]);
        assertEquals(667, block.prefixPulseLengthsTStates()[8_063]);
        assertEquals(735, block.prefixPulseLengthsTStates()[8_064]);
        assertEquals(855, block.zeroBitPulseLengthTStates());
        assertEquals(1_710, block.oneBitPulseLengthTStates());
        assertEquals(8, block.usedBitsInLastByte());
        assertEquals(1_000, block.pauseAfterMillis());
        assertArrayEquals(bytes(0x00, 0x11, 0x22), block.data());
    }

    @Test
    void parsesTurboPureTonePulseSequencePureDataAndPauseBlocks() throws Exception {
        byte[] tzx = tzx(
                block(0x30, bytes(3), bytes('f', 'o', 'o')),
                block(0x11,
                        le16(2_000),
                        le16(500),
                        le16(600),
                        le16(700),
                        le16(1_400),
                        le16(10),
                        bytes(8),
                        le16(333),
                        le24(2),
                        bytes(0xAA, 0x55)),
                block(0x14, le16(400), le16(800), bytes(3), le16(250), le24(1), bytes(0xE0)),
                block(0x20, le16(0))
        );

        TapeFile tapeFile = TzxLoader.load(new ByteArrayInputStream(tzx));

        assertEquals(3, tapeFile.blocks().size());

        TapeBlock turbo = tapeFile.blocks().get(0);
        assertEquals(12, turbo.prefixPulseLengthsTStates().length);
        assertEquals(2_000, turbo.prefixPulseLengthsTStates()[0]);
        assertEquals(500, turbo.prefixPulseLengthsTStates()[10]);
        assertEquals(600, turbo.prefixPulseLengthsTStates()[11]);
        assertEquals(700, turbo.zeroBitPulseLengthTStates());
        assertEquals(1_400, turbo.oneBitPulseLengthTStates());
        assertEquals(8, turbo.usedBitsInLastByte());
        assertEquals(333, turbo.pauseAfterMillis());
        assertArrayEquals(bytes(0xAA, 0x55), turbo.data());

        TapeBlock pureData = tapeFile.blocks().get(1);
        assertEquals(0, pureData.prefixPulseLengthsTStates().length);
        assertEquals(400, pureData.zeroBitPulseLengthTStates());
        assertEquals(800, pureData.oneBitPulseLengthTStates());
        assertEquals(3, pureData.usedBitsInLastByte());
        assertEquals(3, pureData.totalDataBits());
        assertEquals(250, pureData.pauseAfterMillis());
        assertArrayEquals(bytes(0xE0), pureData.data());

        TapeBlock pause = tapeFile.blocks().get(2);
        assertTrue(pause.stopTapeAfterBlock());
        assertEquals(0, pause.pauseAfterMillis());
        assertFalse(pause.hasData());
    }

    @Test
    void mergesPureToneAndPulseSequenceIntoFollowingPureDataBlock() throws Exception {
        byte[] tzx = tzx(
                block(0x12, le16(1_000), le16(3)),
                block(0x13, bytes(3), le16(100), le16(200), le16(300)),
                block(0x14, le16(400), le16(800), bytes(3), le16(250), le24(1), bytes(0xE0))
        );

        TapeFile tapeFile = TzxLoader.load(new ByteArrayInputStream(tzx));
        TapeBlock block = tapeFile.blocks().get(0);

        assertEquals(1, tapeFile.blocks().size());
        assertArrayEquals(new int[]{1_000, 1_000, 1_000, 100, 200, 300}, block.prefixPulseLengthsTStates());
        assertEquals(400, block.zeroBitPulseLengthTStates());
        assertEquals(800, block.oneBitPulseLengthTStates());
        assertEquals(250, block.pauseAfterMillis());
        assertArrayEquals(bytes(0xE0), block.data());
    }

    @Test
    void parsesStopTapeIf48kModeBlockSeparatelyFromHardStop() throws Exception {
        byte[] tzx = tzx(
                block(0x2A, bytes(0, 0, 0, 0))
        );

        TapeFile tapeFile = TzxLoader.load(new ByteArrayInputStream(tzx));
        TapeBlock block = tapeFile.blocks().get(0);

        assertEquals(1, tapeFile.blocks().size());
        assertFalse(block.stopTapeAfterBlock());
        assertTrue(block.stopTapeIf48kMode());
        assertEquals(0, block.pauseAfterMillis());
    }

    private static byte[] tzx(byte[]... blocks) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes('Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1A, 0x01, 0x20));
        for (byte[] block : blocks) {
            out.write(block);
        }
        return out.toByteArray();
    }

    private static byte[] block(int id, byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(id);
        for (byte[] part : parts) {
            out.write(part);
        }
        return out.toByteArray();
    }

    private static byte[] le16(int value) {
        return bytes(value & 0xFF, (value >>> 8) & 0xFF);
    }

    private static byte[] le24(int value) {
        return bytes(value & 0xFF, (value >>> 8) & 0xFF, (value >>> 16) & 0xFF);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
