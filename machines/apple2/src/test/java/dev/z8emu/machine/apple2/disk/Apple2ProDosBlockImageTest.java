package dev.z8emu.machine.apple2.disk;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Apple2ProDosBlockImageTest {
    @Test
    void readsBlocksFromProDosOrderedImage() {
        byte[] bytes = new byte[Apple2ProDosBlockImage.IMAGE_SIZE_800K];
        bytes[0] = 0x11;
        bytes[Apple2ProDosBlockImage.BLOCK_SIZE] = 0x22;
        bytes[Apple2ProDosBlockImage.BLOCK_SIZE + 1] = 0x33;

        Apple2ProDosBlockImage image = Apple2ProDosBlockImage.fromProDosOrderedBytes(bytes);

        assertEquals(Apple2ProDosBlockImage.BLOCK_SIZE, image.blockSize());
        assertEquals(Apple2ProDosBlockImage.BLOCK_COUNT_800K, image.blockCount());
        byte[] firstBlock = image.readBlock(0);
        byte[] secondBlock = image.readBlock(1);
        assertArrayEquals(new byte[]{0x11, 0x00, 0x00, 0x00}, firstBytes(firstBlock, 4));
        assertArrayEquals(new byte[]{0x22, 0x33, 0x00, 0x00}, firstBytes(secondBlock, 4));
    }

    @Test
    void readsVolumeNameAndRootDirectoryEntries() {
        byte[] bytes = new byte[Apple2ProDosBlockImage.IMAGE_SIZE_800K];
        int root = Apple2ProDosBlockImage.BLOCK_SIZE * 2;
        bytes[root + 4] = (byte) 0xF6;
        writeAscii(bytes, root + 5, "PRINCE");
        int entry = root + 43;
        bytes[entry] = (byte) 0x26;
        writeAscii(bytes, entry + 1, "PRODOS");
        bytes[entry + 0x10] = (byte) 0xFF;
        bytes[entry + 0x11] = 0x08;
        bytes[entry + 0x13] = 0x09;
        bytes[entry + 0x15] = 0x34;
        bytes[entry + 0x16] = 0x12;

        Apple2ProDosBlockImage image = Apple2ProDosBlockImage.fromProDosOrderedBytes(bytes);

        assertEquals("PRINCE", image.volumeName());
        List<Apple2ProDosBlockImage.DirectoryEntry> entries = image.rootDirectoryEntries();
        assertEquals(1, entries.size());
        Apple2ProDosBlockImage.DirectoryEntry prodos = entries.getFirst();
        assertEquals(2, prodos.storageType());
        assertEquals("PRODOS", prodos.name());
        assertEquals(0xFF, prodos.fileType());
        assertEquals(0x0008, prodos.keyBlock());
        assertEquals(0x0009, prodos.blocksUsed());
        assertEquals(0x001234, prodos.eof());
    }

    @Test
    void readsSeedlingFileData() {
        byte[] bytes = baseImageWithVolume("SEED");
        writeRootEntry(bytes, 0, 0x01, "HELLO", 0x06, 9, 1, 4);
        int dataBlock = Apple2ProDosBlockImage.BLOCK_SIZE * 9;
        bytes[dataBlock] = 0x41;
        bytes[dataBlock + 1] = 0x42;
        bytes[dataBlock + 2] = 0x43;
        bytes[dataBlock + 3] = 0x44;

        Apple2ProDosBlockImage image = Apple2ProDosBlockImage.fromProDosOrderedBytes(bytes);

        Apple2ProDosBlockImage.DirectoryEntry entry = image.findRootEntry("hello").orElseThrow();
        assertArrayEquals(new byte[]{0x41, 0x42, 0x43, 0x44}, image.readFileData(entry));
    }

    @Test
    void readsSaplingFileDataThroughSplitIndexBlockPointers() {
        byte[] bytes = baseImageWithVolume("SAP");
        writeRootEntry(bytes, 0, 0x02, "PRODOS", 0xFF, 8, 3, 1024);
        int indexBlock = Apple2ProDosBlockImage.BLOCK_SIZE * 8;
        bytes[indexBlock] = 20;
        bytes[indexBlock + 1] = 21;
        bytes[indexBlock + 0x100] = 0;
        bytes[indexBlock + 0x101] = 0;
        int firstDataBlock = Apple2ProDosBlockImage.BLOCK_SIZE * 20;
        int secondDataBlock = Apple2ProDosBlockImage.BLOCK_SIZE * 21;
        bytes[firstDataBlock] = 0x11;
        bytes[firstDataBlock + Apple2ProDosBlockImage.BLOCK_SIZE - 1] = 0x22;
        bytes[secondDataBlock] = 0x33;

        Apple2ProDosBlockImage image = Apple2ProDosBlockImage.fromProDosOrderedBytes(bytes);

        byte[] data = image.readFileData(image.findRootEntry("PRODOS").orElseThrow());
        assertEquals(1024, data.length);
        assertEquals(0x11, Byte.toUnsignedInt(data[0]));
        assertEquals(0x22, Byte.toUnsignedInt(data[511]));
        assertEquals(0x33, Byte.toUnsignedInt(data[512]));
    }

    @Test
    void readsSparseSaplingFileDataAsZeroesForUnallocatedBlocks() {
        byte[] bytes = baseImageWithVolume("SPARSE");
        writeRootEntry(bytes, 0, 0x02, "SPARSE", 0x06, 8, 2, 1024);
        int indexBlock = Apple2ProDosBlockImage.BLOCK_SIZE * 8;
        bytes[indexBlock] = 20;
        int firstDataBlock = Apple2ProDosBlockImage.BLOCK_SIZE * 20;
        bytes[firstDataBlock] = 0x55;

        Apple2ProDosBlockImage image = Apple2ProDosBlockImage.fromProDosOrderedBytes(bytes);

        byte[] data = image.readFileData(image.findRootEntry("SPARSE").orElseThrow());
        assertEquals(0x55, Byte.toUnsignedInt(data[0]));
        assertEquals(0x00, Byte.toUnsignedInt(data[512]));
    }

    @Test
    void readsTreeFileDataThroughMasterAndIndexBlocks() {
        byte[] bytes = baseImageWithVolume("TREE");
        writeRootEntry(bytes, 0, 0x03, "TREEFILE", 0x06, 8, 3, 513);
        int masterIndexBlock = Apple2ProDosBlockImage.BLOCK_SIZE * 8;
        bytes[masterIndexBlock] = 9;
        int indexBlock = Apple2ProDosBlockImage.BLOCK_SIZE * 9;
        bytes[indexBlock] = 20;
        bytes[indexBlock + 1] = 21;
        int firstDataBlock = Apple2ProDosBlockImage.BLOCK_SIZE * 20;
        int secondDataBlock = Apple2ProDosBlockImage.BLOCK_SIZE * 21;
        bytes[firstDataBlock] = 0x66;
        bytes[secondDataBlock] = 0x77;

        Apple2ProDosBlockImage image = Apple2ProDosBlockImage.fromProDosOrderedBytes(bytes);

        byte[] data = image.readFileData(image.findRootEntry("TREEFILE").orElseThrow());
        assertEquals(513, data.length);
        assertEquals(0x66, Byte.toUnsignedInt(data[0]));
        assertEquals(0x77, Byte.toUnsignedInt(data[512]));
    }

    @Test
    void rejectsUnsupportedSizesAndBlockNumbers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Apple2ProDosBlockImage.fromProDosOrderedBytes(new byte[Apple2DosDiskImage.IMAGE_SIZE])
        );

        Apple2ProDosBlockImage image = Apple2ProDosBlockImage.fromProDosOrderedBytes(
                new byte[Apple2ProDosBlockImage.IMAGE_SIZE_800K]
        );

        assertThrows(IllegalArgumentException.class, () -> image.readBlock(-1));
        assertThrows(IllegalArgumentException.class, () -> image.readBlock(Apple2ProDosBlockImage.BLOCK_COUNT_800K));
    }

    private static byte[] firstBytes(byte[] bytes, int count) {
        byte[] head = new byte[count];
        System.arraycopy(bytes, 0, head, 0, count);
        return head;
    }

    private static byte[] baseImageWithVolume(String name) {
        byte[] bytes = new byte[Apple2ProDosBlockImage.IMAGE_SIZE_800K];
        int root = Apple2ProDosBlockImage.BLOCK_SIZE * 2;
        bytes[root + 4] = (byte) (0xF0 | name.length());
        writeAscii(bytes, root + 5, name);
        return bytes;
    }

    private static void writeRootEntry(
            byte[] bytes,
            int entryIndex,
            int storageType,
            String name,
            int fileType,
            int keyBlock,
            int blocksUsed,
            int eof
    ) {
        int root = Apple2ProDosBlockImage.BLOCK_SIZE * 2;
        int entry = root + 4 + (39 * (entryIndex + 1));
        bytes[entry] = (byte) ((storageType << 4) | name.length());
        writeAscii(bytes, entry + 1, name);
        bytes[entry + 0x10] = (byte) fileType;
        writeWord(bytes, entry + 0x11, keyBlock);
        writeWord(bytes, entry + 0x13, blocksUsed);
        bytes[entry + 0x15] = (byte) eof;
        bytes[entry + 0x16] = (byte) (eof >>> 8);
        bytes[entry + 0x17] = (byte) (eof >>> 16);
    }

    private static void writeWord(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    private static void writeAscii(byte[] target, int offset, String value) {
        for (int i = 0; i < value.length(); i++) {
            target[offset + i] = (byte) value.charAt(i);
        }
    }
}
