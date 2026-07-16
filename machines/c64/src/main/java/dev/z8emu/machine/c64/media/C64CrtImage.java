package dev.z8emu.machine.c64.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public final class C64CrtImage {
    private static final byte[] HEADER_MAGIC = "C64 CARTRIDGE   ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CHIP_MAGIC = "CHIP".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_SIZE = 0x40;
    private static final int CHIP_HEADER_SIZE = 0x10;
    private static final int EASYFLASH_HARDWARE_TYPE = 32;
    private static final int BANK_COUNT = 64;
    private static final int BANK_IMAGE_SIZE = 0x4000;
    private static final int CHIP_SIZE = 0x2000;

    private final byte[] rom;
    private final int bankCount;
    private final String name;
    private final int exromLine;
    private final int gameLine;

    private C64CrtImage(byte[] rom, int bankCount, String name, int exromLine, int gameLine) {
        this.rom = rom;
        this.bankCount = bankCount;
        this.name = name;
        this.exromLine = exromLine;
        this.gameLine = gameLine;
    }

    public static C64CrtImage parse(byte[] fileBytes) {
        Objects.requireNonNull(fileBytes, "fileBytes");
        if (fileBytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException("C64 CRT header is truncated");
        }
        if (!matches(fileBytes, 0, HEADER_MAGIC)) {
            throw new IllegalArgumentException("C64 CRT header magic is invalid");
        }
        long headerLength = unsignedInt(fileBytes, 0x10);
        if (headerLength < HEADER_SIZE) {
            throw new IllegalArgumentException("C64 CRT header length must be at least 64 bytes");
        }
        if (headerLength > fileBytes.length) {
            throw new IllegalArgumentException("C64 CRT extended header is truncated");
        }
        int hardwareType = unsignedShort(fileBytes, 0x16);
        if (hardwareType != EASYFLASH_HARDWARE_TYPE) {
            throw new IllegalArgumentException("Unsupported C64 CRT hardware type: " + hardwareType);
        }

        byte[] rom = new byte[BANK_COUNT * BANK_IMAGE_SIZE];
        Arrays.fill(rom, (byte) 0xFF);
        int highestBank = -1;
        int position = (int) headerLength;
        while (position < fileBytes.length) {
            if (fileBytes.length - position < CHIP_HEADER_SIZE) {
                throw new IllegalArgumentException("C64 CRT CHIP packet header is truncated");
            }
            if (!matches(fileBytes, position, CHIP_MAGIC)) {
                throw new IllegalArgumentException("C64 CRT CHIP packet magic is invalid");
            }
            unsignedInt(fileBytes, position + 0x04);
            int chipType = unsignedShort(fileBytes, position + 0x08);
            if (chipType != 0 && chipType != 2) {
                throw new IllegalArgumentException("Unsupported C64 CRT CHIP type: " + chipType);
            }
            int bank = unsignedShort(fileBytes, position + 0x0A);
            if (bank >= BANK_COUNT) {
                throw new IllegalArgumentException("C64 CRT CHIP bank must be below 64: " + bank);
            }
            int loadAddress = unsignedShort(fileBytes, position + 0x0C);
            if (loadAddress != 0x8000 && loadAddress != 0xA000 && loadAddress != 0xE000) {
                throw new IllegalArgumentException(
                        "C64 CRT CHIP load address must be 0x8000, 0xA000, or 0xE000: 0x%04X"
                                .formatted(loadAddress)
                );
            }
            int dataSize = unsignedShort(fileBytes, position + 0x0E);
            if (dataSize != CHIP_SIZE && !(dataSize == BANK_IMAGE_SIZE && loadAddress == 0x8000)) {
                throw new IllegalArgumentException(
                        "C64 CRT CHIP data size must be 8192 bytes, or 16384 bytes at 0x8000: " + dataSize
                );
            }
            if (fileBytes.length - position - CHIP_HEADER_SIZE < dataSize) {
                throw new IllegalArgumentException("C64 CRT CHIP data is truncated");
            }

            int targetOffset = bank * BANK_IMAGE_SIZE + (loadAddress == 0x8000 ? 0 : CHIP_SIZE);
            System.arraycopy(fileBytes, position + CHIP_HEADER_SIZE, rom, targetOffset, dataSize);
            highestBank = Math.max(highestBank, bank);
            position += CHIP_HEADER_SIZE + dataSize;
        }

        return new C64CrtImage(
                rom,
                highestBank + 1,
                parseName(fileBytes),
                Byte.toUnsignedInt(fileBytes[0x18]),
                Byte.toUnsignedInt(fileBytes[0x19])
        );
    }

    public static C64CrtImage load(Path path) throws IOException {
        return parse(Files.readAllBytes(path));
    }

    public int read(int bank, boolean hirom, int offset) {
        if (bank < 0 || bank >= BANK_COUNT) {
            throw new IllegalArgumentException("C64 CRT bank must be between 0 and 63: " + bank);
        }
        int bankOffset = bank * BANK_IMAGE_SIZE + (hirom ? CHIP_SIZE : 0);
        return Byte.toUnsignedInt(rom[bankOffset + (offset & 0x1FFF)]);
    }

    public int bankCount() {
        return bankCount;
    }

    public String name() {
        return name;
    }

    public int exromLine() {
        return exromLine;
    }

    public int gameLine() {
        return gameLine;
    }

    private static String parseName(byte[] fileBytes) {
        int end = HEADER_SIZE;
        while (end > 0x20 && (fileBytes[end - 1] == 0 || fileBytes[end - 1] == 0x20)) {
            end--;
        }
        return new String(fileBytes, 0x20, end - 0x20, StandardCharsets.US_ASCII);
    }

    private static boolean matches(byte[] bytes, int offset, byte[] expected) {
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (Byte.toUnsignedInt(bytes[offset]) << 8)
                | Byte.toUnsignedInt(bytes[offset + 1]);
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return ((long) Byte.toUnsignedInt(bytes[offset]) << 24)
                | ((long) Byte.toUnsignedInt(bytes[offset + 1]) << 16)
                | ((long) Byte.toUnsignedInt(bytes[offset + 2]) << 8)
                | Byte.toUnsignedInt(bytes[offset + 3]);
    }
}
