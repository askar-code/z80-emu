package dev.z8emu.platform.memory;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

public final class FixedSlotMemoryMap implements AddressSpace {
    private final int slotSize;
    private final int slotCount;
    private final int addressSpaceSize;
    private final boolean pow2;
    private final int addressMask;
    private final int offsetMask;
    private final int slotShift;
    private final MemoryBank[] slots;

    public FixedSlotMemoryMap(int slotSize, int slotCount) {
        if (slotSize <= 0) {
            throw new IllegalArgumentException("slotSize must be positive");
        }
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive");
        }

        this.slotSize = slotSize;
        this.slotCount = slotCount;
        this.addressSpaceSize = slotSize * slotCount;
        this.pow2 = Integer.bitCount(slotSize) == 1 && Integer.bitCount(addressSpaceSize) == 1;
        // For power-of-two sizes, masking is equivalent to floorMod for every int input.
        this.addressMask = pow2 ? addressSpaceSize - 1 : 0;
        this.offsetMask = pow2 ? slotSize - 1 : 0;
        this.slotShift = pow2 ? Integer.numberOfTrailingZeros(slotSize) : 0;
        this.slots = new MemoryBank[slotCount];
    }

    public void mapSlot(int slotIndex, MemoryBank bank) {
        validateSlotIndex(slotIndex);
        Objects.requireNonNull(bank, "bank");
        if (bank.length() != slotSize) {
            throw new IllegalArgumentException("bank length %d does not match slot size %d".formatted(bank.length(), slotSize));
        }
        slots[slotIndex] = bank;
    }

    public MemoryBank bankAtSlot(int slotIndex) {
        validateSlotIndex(slotIndex);
        return slots[slotIndex];
    }

    @Override
    public int read(int address) {
        int normalized;
        int slotIndex;
        int offset;
        if (pow2) {
            normalized = address & addressMask;
            slotIndex = normalized >>> slotShift;
            offset = normalized & offsetMask;
        } else {
            normalized = normalizeAddress(address);
            slotIndex = normalized / slotSize;
            offset = normalized % slotSize;
        }
        MemoryBank bank = slots[slotIndex];
        if (bank == null) {
            throw new IllegalStateException("Slot %d is not mapped".formatted(slotIndex));
        }
        return bank.read(offset);
    }

    @Override
    public void write(int address, int value) {
        int normalized;
        int slotIndex;
        int offset;
        if (pow2) {
            normalized = address & addressMask;
            slotIndex = normalized >>> slotShift;
            offset = normalized & offsetMask;
        } else {
            normalized = normalizeAddress(address);
            slotIndex = normalized / slotSize;
            offset = normalized % slotSize;
        }
        MemoryBank bank = slots[slotIndex];
        if (bank == null) {
            throw new IllegalStateException("Slot %d is not mapped".formatted(slotIndex));
        }
        bank.write(offset, value);
    }

    @Override
    public void reset() {
        Set<MemoryBank> uniqueBanks = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (MemoryBank slot : slots) {
            if (slot != null && uniqueBanks.add(slot)) {
                slot.reset();
            }
        }
    }

    private int normalizeAddress(int address) {
        return Math.floorMod(address, addressSpaceSize);
    }

    private void validateSlotIndex(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slotCount) {
            throw new IllegalArgumentException("slotIndex out of range: " + slotIndex);
        }
    }
}
