package dev.z8emu.machine.c64.media;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class C64CrtTestImages {
    private static final int CHIP_SIZE = 0x2000;

    private C64CrtTestImages() {
    }

    public static byte[] crtBytes(String name, Chip... chips) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] header = new byte[0x40];
        byte[] magic = "C64 CARTRIDGE   ".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 0, magic.length);
        putInt(header, 0x10, 0x40);
        putShort(header, 0x14, 0x0100);
        putShort(header, 0x16, 32);
        header[0x18] = 1;
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        Arrays.fill(header, 0x20, 0x40, (byte) 0x20);
        System.arraycopy(nameBytes, 0, header, 0x20, Math.min(nameBytes.length, 32));
        output.writeBytes(header);

        for (Chip chip : chips) {
            byte[] chipHeader = new byte[0x10];
            chipHeader[0] = 'C';
            chipHeader[1] = 'H';
            chipHeader[2] = 'I';
            chipHeader[3] = 'P';
            putInt(chipHeader, 0x04, chipHeader.length + chip.data().length);
            putShort(chipHeader, 0x08, chip.type());
            putShort(chipHeader, 0x0A, chip.bank());
            putShort(chipHeader, 0x0C, chip.loadAddress());
            putShort(chipHeader, 0x0E, chip.data().length);
            output.writeBytes(chipHeader);
            output.writeBytes(chip.data());
        }
        return output.toByteArray();
    }

    public static C64CrtImage syntheticCart(int... loromHiromSentinels) {
        if (loromHiromSentinels.length == 0 || (loromHiromSentinels.length & 1) != 0) {
            throw new IllegalArgumentException("Sentinels must contain one LOROM/HIROM pair per bank");
        }
        Chip[] chips = new Chip[loromHiromSentinels.length];
        for (int bank = 0; bank < loromHiromSentinels.length / 2; bank++) {
            chips[bank * 2] = new Chip(0, bank, 0x8000, filled(CHIP_SIZE, loromHiromSentinels[bank * 2]));
            chips[bank * 2 + 1] = new Chip(
                    0,
                    bank,
                    0xA000,
                    filled(CHIP_SIZE, loromHiromSentinels[bank * 2 + 1])
            );
        }
        return C64CrtImage.parse(crtBytes("SYNTHETIC EASYFLASH", chips));
    }

    public static byte[] filled(int size, int value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static void putShort(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 8);
        target[offset + 1] = (byte) value;
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    public record Chip(int type, int bank, int loadAddress, byte[] data) {
    }
}
