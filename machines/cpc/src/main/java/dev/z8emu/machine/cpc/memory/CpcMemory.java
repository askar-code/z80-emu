package dev.z8emu.machine.cpc.memory;

import dev.z8emu.platform.memory.RamMemoryBank;
import dev.z8emu.platform.memory.ReadOnlyMemoryBank;
import java.util.Arrays;
import java.util.Objects;

public final class CpcMemory {
    public static final int ROM_SIZE = 16 * 1024;
    public static final int RAM_BANK_SIZE = 16 * 1024;
    public static final int RAM_BANK_COUNT_6128 = 8;
    public static final int UPPER_ROM_SLOT_COUNT = 16;
    public static final int ROM_IMAGE_SIZE_OS_ONLY = ROM_SIZE;
    public static final int ROM_IMAGE_SIZE_OS_BASIC = ROM_SIZE * 2;
    public static final int ROM_IMAGE_SIZE_OS_BASIC_AMSDOS = ROM_SIZE * 3;

    private static final int[][] RAM_CONFIGURATIONS = {
            {0, 1, 2, 3},
            {0, 1, 2, 7},
            {4, 5, 6, 7},
            {0, 3, 2, 7},
            {0, 4, 2, 3},
            {0, 5, 2, 3},
            {0, 6, 2, 3},
            {0, 7, 2, 3}
    };

    private final ReadOnlyMemoryBank lowerRom;
    private final ReadOnlyMemoryBank[] upperRoms = new ReadOnlyMemoryBank[UPPER_ROM_SLOT_COUNT];
    private final RamMemoryBank[] ramBanks = new RamMemoryBank[RAM_BANK_COUNT_6128];

    private boolean lowerRomEnabled = true;
    private boolean upperRomEnabled = true;
    private int selectedUpperRomIndex;
    private int ramConfiguration;
    private int screenMode;

    public CpcMemory(byte[] combinedRomImage) {
        this(splitCombinedRomImage(combinedRomImage));
    }

    public CpcMemory(byte[] lowerRomImage, UpperRom... upperRomImages) {
        this(new RomSet(lowerRomImage, upperRomImages));
    }

    private CpcMemory(RomSet romSet) {
        this.lowerRom = new ReadOnlyMemoryBank(validateRom(romSet.lowerRomImage(), "lower ROM"));
        for (UpperRom upperRom : romSet.upperRoms()) {
            Objects.requireNonNull(upperRom, "upperRom");
            upperRoms[upperRom.index()] = new ReadOnlyMemoryBank(validateRom(upperRom.image(), "upper ROM"));
        }
        byte[] emptyUpperRom = new byte[ROM_SIZE];
        Arrays.fill(emptyUpperRom, (byte) 0xFF);
        for (int i = 0; i < upperRoms.length; i++) {
            if (upperRoms[i] == null) {
                upperRoms[i] = new ReadOnlyMemoryBank(emptyUpperRom);
            }
        }
        for (int i = 0; i < ramBanks.length; i++) {
            ramBanks[i] = new RamMemoryBank(RAM_BANK_SIZE);
        }
    }

    public void reset() {
        for (RamMemoryBank ramBank : ramBanks) {
            ramBank.reset();
        }
        lowerRomEnabled = true;
        upperRomEnabled = true;
        selectedUpperRomIndex = 0;
        ramConfiguration = 0;
        screenMode = 0;
    }

    public int read(int address) {
        int normalized = address & 0xFFFF;
        int slot = normalized >>> 14;
        int offset = normalized & (RAM_BANK_SIZE - 1);

        if (slot == 0 && lowerRomEnabled) {
            return lowerRom.read(offset);
        }
        if (slot == 3 && upperRomEnabled) {
            return upperRoms[selectedUpperRomIndex].read(offset);
        }
        return ramBanks[visibleRamBankIndexForSlot(slot)].read(offset);
    }

    public void write(int address, int value) {
        int normalized = address & 0xFFFF;
        int slot = normalized >>> 14;
        int offset = normalized & (RAM_BANK_SIZE - 1);
        ramBanks[visibleRamBankIndexForSlot(slot)].write(offset, value);
    }

    public int readDisplayMemory(int address) {
        int normalized = address & 0xFFFF;
        int slot = normalized >>> 14;
        int offset = normalized & (RAM_BANK_SIZE - 1);
        return ramBanks[visibleRamBankIndexForSlot(slot)].read(offset);
    }

    public void writeGateArrayControl(int value) {
        int normalized = value & 0xFF;
        if ((normalized & 0xC0) == 0x80) {
            screenMode = normalized & 0x03;
            lowerRomEnabled = (normalized & 0x04) == 0;
            upperRomEnabled = (normalized & 0x08) == 0;
        } else if ((normalized & 0xC0) == 0xC0) {
            ramConfiguration = normalized & 0x07;
        }
    }

    public void selectUpperRom(int index) {
        selectedUpperRomIndex = index & 0x0F;
    }

    public RamMemoryBank ramBank(int index) {
        if (index < 0 || index >= ramBanks.length) {
            throw new IllegalArgumentException("RAM bank index out of range: " + index);
        }
        return ramBanks[index];
    }

    public boolean lowerRomEnabled() {
        return lowerRomEnabled;
    }

    public boolean upperRomEnabled() {
        return upperRomEnabled;
    }

    public int selectedUpperRomIndex() {
        return selectedUpperRomIndex;
    }

    public int ramConfiguration() {
        return ramConfiguration;
    }

    public int screenMode() {
        return screenMode;
    }

    public int visibleRamBankIndexForSlot(int slot) {
        if (slot < 0 || slot >= 4) {
            throw new IllegalArgumentException("Slot index out of range: " + slot);
        }
        return RAM_CONFIGURATIONS[ramConfiguration][slot];
    }

    public static boolean isSupportedCombinedRomSize(int length) {
        return length == ROM_IMAGE_SIZE_OS_ONLY
                || length == ROM_IMAGE_SIZE_OS_BASIC
                || length == ROM_IMAGE_SIZE_OS_BASIC_AMSDOS;
    }

    private static RomSet splitCombinedRomImage(byte[] combinedRomImage) {
        Objects.requireNonNull(combinedRomImage, "combinedRomImage");
        if (!isSupportedCombinedRomSize(combinedRomImage.length)) {
            throw new IllegalArgumentException("CPC ROM image must be exactly 16 KB, 32 KB, or 48 KB");
        }

        byte[] lowerRomImage = Arrays.copyOfRange(combinedRomImage, 0, ROM_SIZE);
        if (combinedRomImage.length == ROM_IMAGE_SIZE_OS_ONLY) {
            return new RomSet(lowerRomImage);
        }

        UpperRom basicRom = new UpperRom(0, Arrays.copyOfRange(combinedRomImage, ROM_SIZE, ROM_SIZE * 2));
        if (combinedRomImage.length == ROM_IMAGE_SIZE_OS_BASIC) {
            return new RomSet(lowerRomImage, basicRom);
        }

        UpperRom amsdosRom = new UpperRom(7, Arrays.copyOfRange(combinedRomImage, ROM_SIZE * 2, ROM_SIZE * 3));
        return new RomSet(lowerRomImage, basicRom, amsdosRom);
    }

    private static byte[] validateRom(byte[] image, String label) {
        Objects.requireNonNull(image, label);
        if (image.length != ROM_SIZE) {
            throw new IllegalArgumentException(label + " must be exactly 16 KB");
        }
        return image;
    }

    public record UpperRom(int index, byte[] image) {
        public UpperRom {
            if (index < 0 || index >= UPPER_ROM_SLOT_COUNT) {
                throw new IllegalArgumentException("Upper ROM index out of range: " + index);
            }
            Objects.requireNonNull(image, "image");
        }
    }

    private record RomSet(byte[] lowerRomImage, UpperRom... upperRoms) {
        private RomSet {
            Objects.requireNonNull(lowerRomImage, "lowerRomImage");
            Objects.requireNonNull(upperRoms, "upperRoms");
        }
    }
}
