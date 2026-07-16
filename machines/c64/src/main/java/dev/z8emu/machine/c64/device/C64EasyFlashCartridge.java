package dev.z8emu.machine.c64.device;

import dev.z8emu.machine.c64.media.C64CrtImage;
import java.util.Arrays;
import java.util.Objects;

public final class C64EasyFlashCartridge {
    private final C64CrtImage image;
    private final byte[] ram = new byte[256];
    private int bankRegister;
    private int controlRegister;

    public C64EasyFlashCartridge(C64CrtImage image) {
        this.image = Objects.requireNonNull(image, "image");
    }

    public void writeBankRegister(int value) {
        bankRegister = value & 0x3F;
    }

    public void writeControlRegister(int value) {
        controlRegister = value & 0x87;
    }

    public int bankRegister() {
        return bankRegister;
    }

    public int controlRegister() {
        return controlRegister;
    }

    public boolean exromAsserted() {
        return (controlRegister & 0x02) != 0;
    }

    public boolean gameAsserted() {
        return (controlRegister & 0x04) == 0 || (controlRegister & 0x01) != 0;
    }

    public void reset() {
        bankRegister = 0;
        controlRegister = 0;
        Arrays.fill(ram, (byte) 0);
    }

    public int readRoml(int offset) {
        return image.read(bankRegister, false, offset & 0x3FFF);
    }

    public int readRomh(int offset) {
        return image.read(bankRegister, true, offset & 0x3FFF);
    }

    public int readRam(int offset) {
        return Byte.toUnsignedInt(ram[offset & 0xFF]);
    }

    public void writeRam(int offset, int value) {
        ram[offset & 0xFF] = (byte) value;
    }
}
