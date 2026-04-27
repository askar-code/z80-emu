package dev.z8emu.machine.apple2.disk;

import dev.z8emu.machine.apple2.Apple2SlotCard;
import dev.z8emu.platform.bus.io.IoAccess;
import java.util.Objects;

/**
 * Synthetic ProDOS block-device shim for headless probes.
 *
 * <p>This is not an Apple 3.5, IWM, SmartPort, or real controller ROM. It
 * exposes enough ProDOS block-driver shape to inspect loader behavior against
 * local .po images while the real low-level controller path is still pending.
 */
public final class Apple2ProDosBlockShimController implements Apple2SlotCard {
    public static final int SLOT = 6;
    public static final int SLOT_INDEX = SLOT << 4;
    public static final int SLOT_ROM_START = 0xC000 | (SLOT << 8);
    public static final int DRIVER_ENTRY_OFFSET = 0x10;
    private static final int STATUS_READ_AND_STATUS = 0x03;
    private static final int ROM_SIZE = 0x100;
    private static final int IO_BASE = 0xC080 | SLOT_INDEX;

    private final Apple2BlockDevice blockDevice;
    private final byte[] slotRom;
    private byte[] streamBlock;
    private int blockLow;
    private int blockHigh;
    private int streamOffset;

    public Apple2ProDosBlockShimController(Apple2BlockDevice blockDevice) {
        this.blockDevice = Objects.requireNonNull(blockDevice, "blockDevice");
        this.slotRom = buildShimSlotRom(blockDevice.blockCount());
        reset();
    }

    public void reset() {
        blockLow = 0;
        blockHigh = 0;
        streamBlock = null;
        streamOffset = 0;
    }

    @Override
    public int readCnxx(int offset) {
        return Byte.toUnsignedInt(slotRom[offset & 0xFF]);
    }

    @Override
    public boolean hasCnxxRom() {
        return true;
    }

    @Override
    public int readC0x(IoAccess access) {
        if ((access.offset() & 0x0F) == 0x03) {
            return readStreamByte();
        }
        return 0x00;
    }

    @Override
    public void writeC0x(IoAccess access, int value) {
        switch (access.offset() & 0x0F) {
            case 0x0 -> blockLow = value & 0xFF;
            case 0x1 -> blockHigh = value & 0xFF;
            case 0x2 -> startReadStream();
            default -> {
            }
        }
    }

    private void startReadStream() {
        int block = blockLow | (blockHigh << 8);
        streamBlock = blockDevice.readBlock(block);
        streamOffset = 0;
    }

    private int readStreamByte() {
        if (streamBlock == null || streamOffset >= streamBlock.length) {
            return 0x00;
        }
        return Byte.toUnsignedInt(streamBlock[streamOffset++]);
    }

    private static byte[] buildShimSlotRom(int blockCount) {
        byte[] rom = new byte[ROM_SIZE];
        rom[0x01] = 0x20;
        rom[0x03] = 0x00;
        rom[0x05] = 0x03;
        rom[0xFC] = (byte) (blockCount & 0xFF);
        rom[0xFD] = (byte) ((blockCount >>> 8) & 0xFF);
        rom[0xFE] = STATUS_READ_AND_STATUS;
        rom[0xFF] = DRIVER_ENTRY_OFFSET;
        writeSyntheticDriver(rom, DRIVER_ENTRY_OFFSET, blockCount);
        return rom;
    }

    private static void writeSyntheticDriver(byte[] rom, int offset, int blockCount) {
        int ioLow = IO_BASE & 0xFF;
        int ioHigh = (IO_BASE >>> 8) & 0xFF;
        int slotRomLow = SLOT_ROM_START & 0xFF;
        int slotRomHigh = (SLOT_ROM_START >>> 8) & 0xFF;
        int blockCountLow = blockCount & 0xFF;
        int blockCountHigh = (blockCount >>> 8) & 0xFF;
        int i = offset;

        rom[i++] = 0x4C; rom[i++] = (byte) (slotRomLow + 0x1C); rom[i++] = (byte) slotRomHigh; // JMP dispatch
        rom[i++] = 0x4C; rom[i++] = (byte) (slotRomLow + 0x31); rom[i++] = (byte) slotRomHigh; // JMP read
        rom[i++] = 0x4C; rom[i++] = (byte) (slotRomLow + 0x5F); rom[i++] = (byte) slotRomHigh; // JMP write-protected
        rom[i++] = 0x4C; rom[i++] = (byte) (slotRomLow + 0x5F); rom[i++] = (byte) slotRomHigh; // JMP format unsupported

        rom[i++] = (byte) 0xD8; // CLD
        rom[i++] = (byte) 0xA5; rom[i++] = 0x42; // LDA $42
        rom[i++] = (byte) 0xF0; rom[i++] = 0x42; // BEQ status
        rom[i++] = (byte) 0xC9; rom[i++] = 0x01; // CMP #$01
        rom[i++] = (byte) 0xF0; rom[i++] = 0x0C; // BEQ read
        rom[i++] = (byte) 0xC9; rom[i++] = 0x02; // CMP #$02
        rom[i++] = (byte) 0xF0; rom[i++] = 0x36; // BEQ write-protected
        rom[i++] = (byte) 0xC9; rom[i++] = 0x03; // CMP #$03
        rom[i++] = (byte) 0xF0; rom[i++] = 0x32; // BEQ write-protected
        rom[i++] = 0x38; // SEC
        rom[i++] = (byte) 0xA9; rom[i++] = 0x27; // LDA #$27
        rom[i++] = 0x60; // RTS

        rom[i++] = (byte) 0xA5; rom[i++] = 0x44; // LDA $44
        rom[i++] = 0x48; // PHA
        rom[i++] = (byte) 0xA5; rom[i++] = 0x45; // LDA $45
        rom[i++] = 0x48; // PHA
        rom[i++] = (byte) 0xA5; rom[i++] = 0x46; // LDA $46
        rom[i++] = (byte) 0x8D; rom[i++] = (byte) ioLow; rom[i++] = (byte) ioHigh; // STA $C0E0
        rom[i++] = (byte) 0xA5; rom[i++] = 0x47; // LDA $47
        rom[i++] = (byte) 0x8D; rom[i++] = (byte) (ioLow + 1); rom[i++] = (byte) ioHigh; // STA $C0E1
        rom[i++] = (byte) 0x8D; rom[i++] = (byte) (ioLow + 2); rom[i++] = (byte) ioHigh; // STA $C0E2
        rom[i++] = (byte) 0xA2; rom[i++] = 0x02; // LDX #$02
        rom[i++] = (byte) 0xA0; rom[i++] = 0x00; // LDY #$00
        rom[i++] = (byte) 0xAD; rom[i++] = (byte) (ioLow + 3); rom[i++] = (byte) ioHigh; // LDA $C0E3
        rom[i++] = (byte) 0x91; rom[i++] = 0x44; // STA ($44),Y
        rom[i++] = (byte) 0xC8; // INY
        rom[i++] = (byte) 0xD0; rom[i++] = (byte) 0xF8; // BNE read-byte
        rom[i++] = (byte) 0xE6; rom[i++] = 0x45; // INC $45
        rom[i++] = (byte) 0xCA; // DEX
        rom[i++] = (byte) 0xD0; rom[i++] = (byte) 0xF1; // BNE read-page
        rom[i++] = 0x68; // PLA
        rom[i++] = (byte) 0x85; rom[i++] = 0x45; // STA $45
        rom[i++] = 0x68; // PLA
        rom[i++] = (byte) 0x85; rom[i++] = 0x44; // STA $44
        rom[i++] = (byte) 0xA9; rom[i++] = 0x00; // LDA #$00
        rom[i++] = 0x18; // CLC
        rom[i++] = 0x60; // RTS

        rom[i++] = 0x38; // SEC
        rom[i++] = (byte) 0xA9; rom[i++] = 0x2B; // LDA #$2B
        rom[i++] = 0x60; // RTS

        rom[i++] = (byte) 0xA2; rom[i++] = (byte) blockCountLow; // LDX #blocks low
        rom[i++] = (byte) 0xA0; rom[i++] = (byte) blockCountHigh; // LDY #blocks high
        rom[i++] = (byte) 0xA9; rom[i++] = 0x00; // LDA #$00
        rom[i++] = 0x18; // CLC
        rom[i] = 0x60; // RTS
    }
}
