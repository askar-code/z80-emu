package dev.z8emu.machine.apple2.disk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Apple2ProDosBlockImage implements Apple2BlockDevice {
    public static final int BLOCK_SIZE = 512;
    public static final int BLOCK_COUNT_800K = 1600;
    public static final int IMAGE_SIZE_800K = BLOCK_SIZE * BLOCK_COUNT_800K;

    private static final int ROOT_DIRECTORY_BLOCK = 2;
    private static final int DIRECTORY_LINK_BYTES = 4;
    private static final int DIRECTORY_ENTRY_SIZE = 39;
    private static final int DIRECTORY_ENTRIES_PER_BLOCK = 13;
    private static final int NAME_MAX_LENGTH = 15;
    private static final int VOLUME_DIRECTORY_STORAGE_TYPE = 0x0F;
    private static final int DELETED_STORAGE_TYPE = 0x00;
    private static final int SEEDLING_STORAGE_TYPE = 0x01;
    private static final int SAPLING_STORAGE_TYPE = 0x02;
    private static final int TREE_STORAGE_TYPE = 0x03;

    private final byte[] bytes;

    private Apple2ProDosBlockImage(byte[] bytes) {
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public static Apple2ProDosBlockImage fromProDosOrderedBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != IMAGE_SIZE_800K) {
            throw new IllegalArgumentException(
                    "Apple II ProDOS 800 KB block image must be exactly %d bytes".formatted(IMAGE_SIZE_800K)
            );
        }
        return new Apple2ProDosBlockImage(bytes);
    }

    public String volumeName() {
        byte[] root = readBlock(ROOT_DIRECTORY_BLOCK);
        int storageTypeAndNameLength = Byte.toUnsignedInt(root[DIRECTORY_LINK_BYTES]);
        if ((storageTypeAndNameLength >>> 4) != VOLUME_DIRECTORY_STORAGE_TYPE) {
            throw new IllegalStateException("ProDOS root directory is missing a volume header");
        }
        return readName(root, DIRECTORY_LINK_BYTES);
    }

    public List<DirectoryEntry> rootDirectoryEntries() {
        List<DirectoryEntry> entries = new ArrayList<>();
        int block = ROOT_DIRECTORY_BLOCK;
        int visited = 0;
        while (block != 0) {
            if (visited++ >= BLOCK_COUNT_800K) {
                throw new IllegalStateException("ProDOS directory block chain loops at block " + block);
            }
            byte[] data = readBlock(block);
            for (int entryIndex = 0; entryIndex < DIRECTORY_ENTRIES_PER_BLOCK; entryIndex++) {
                int offset = DIRECTORY_LINK_BYTES + (entryIndex * DIRECTORY_ENTRY_SIZE);
                int storageAndName = Byte.toUnsignedInt(data[offset]);
                int storageType = storageAndName >>> 4;
                if (storageType == DELETED_STORAGE_TYPE || storageType == VOLUME_DIRECTORY_STORAGE_TYPE) {
                    continue;
                }
                entries.add(new DirectoryEntry(
                        storageType,
                        readName(data, offset),
                        unsignedByte(data, offset + 0x10),
                        littleEndianWord(data, offset + 0x11),
                        littleEndianWord(data, offset + 0x13),
                        littleEndian24(data, offset + 0x15)
                ));
            }
            block = littleEndianWord(data, 2);
        }
        return List.copyOf(entries);
    }

    public Optional<DirectoryEntry> findRootEntry(String name) {
        Objects.requireNonNull(name, "name");
        return rootDirectoryEntries().stream()
                .filter(entry -> entry.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public byte[] readFileData(DirectoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        byte[] data = new byte[entry.eof()];
        int blocksToRead = (entry.eof() + BLOCK_SIZE - 1) / BLOCK_SIZE;
        for (int logicalBlock = 0; logicalBlock < blocksToRead; logicalBlock++) {
            int sourceBlock = dataBlockFor(entry, logicalBlock);
            int targetOffset = logicalBlock * BLOCK_SIZE;
            int byteCount = Math.min(BLOCK_SIZE, data.length - targetOffset);
            if (sourceBlock == 0) {
                Arrays.fill(data, targetOffset, targetOffset + byteCount, (byte) 0x00);
            } else {
                byte[] block = readBlock(sourceBlock);
                System.arraycopy(block, 0, data, targetOffset, byteCount);
            }
        }
        return data;
    }

    @Override
    public int blockSize() {
        return BLOCK_SIZE;
    }

    @Override
    public int blockCount() {
        return BLOCK_COUNT_800K;
    }

    @Override
    public byte[] readBlock(int block) {
        if (block < 0 || block >= BLOCK_COUNT_800K) {
            throw new IllegalArgumentException("Apple II ProDOS block out of range: " + block);
        }
        int offset = block * BLOCK_SIZE;
        return Arrays.copyOfRange(bytes, offset, offset + BLOCK_SIZE);
    }

    private int dataBlockFor(DirectoryEntry entry, int logicalBlock) {
        return switch (entry.storageType()) {
            case SEEDLING_STORAGE_TYPE -> {
                if (logicalBlock != 0) {
                    throw new IllegalArgumentException("Seedling file has only one data block");
                }
                yield entry.keyBlock();
            }
            case SAPLING_STORAGE_TYPE -> indexBlockPointer(entry.keyBlock(), logicalBlock);
            case TREE_STORAGE_TYPE -> {
                int indexBlock = indexBlockPointer(entry.keyBlock(), logicalBlock >>> 8);
                yield indexBlock == 0 ? 0 : indexBlockPointer(indexBlock, logicalBlock & 0xFF);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported ProDOS file storage type for data read: " + entry.storageType()
            );
        };
    }

    private int indexBlockPointer(int indexBlock, int offset) {
        if (offset < 0 || offset > 0xFF) {
            throw new IllegalArgumentException("ProDOS index block pointer offset out of range: " + offset);
        }
        byte[] block = readBlock(indexBlock);
        return unsignedByte(block, offset) | (unsignedByte(block, offset + 0x100) << 8);
    }

    private static String readName(byte[] block, int entryOffset) {
        int length = block[entryOffset] & 0x0F;
        if (length > NAME_MAX_LENGTH) {
            throw new IllegalStateException("ProDOS name length out of range: " + length);
        }
        StringBuilder name = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            name.append((char) (block[entryOffset + 1 + i] & 0x7F));
        }
        return name.toString();
    }

    private static int unsignedByte(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset]);
    }

    private static int littleEndianWord(byte[] data, int offset) {
        return unsignedByte(data, offset) | (unsignedByte(data, offset + 1) << 8);
    }

    private static int littleEndian24(byte[] data, int offset) {
        return unsignedByte(data, offset)
                | (unsignedByte(data, offset + 1) << 8)
                | (unsignedByte(data, offset + 2) << 16);
    }

    public record DirectoryEntry(
            int storageType,
            String name,
            int fileType,
            int keyBlock,
            int blocksUsed,
            int eof
    ) {
        public boolean isStandardFile() {
            return storageType >= SEEDLING_STORAGE_TYPE && storageType <= TREE_STORAGE_TYPE;
        }
    }
}
