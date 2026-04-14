package dev.z8emu.platform.memory;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

public final class FixedSlotMemoryMap implements AddressSpace {
    private final int slotSize;
    private final int slotCount;
    private final int addressSpaceSize;
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
        this.slots = new MemoryBank[slotCount];
    }

    public int slotSize() {
        return slotSize;
    }

    public int slotCount() {
        return slotCount;
    }

    public int addressSpaceSize() {
        return addressSpaceSize;
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
        MemoryAccess access = resolve(address);
        return access.bank().read(access.offset());
    }

    @Override
    public void write(int address, int value) {
        MemoryAccess access = resolve(address);
        access.bank().write(access.offset(), value);
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

    private MemoryAccess resolve(int address) {
        int normalized = Math.floorMod(address, addressSpaceSize);
        int slotIndex = normalized / slotSize;
        MemoryBank bank = slots[slotIndex];
        if (bank == null) {
            throw new IllegalStateException("Slot %d is not mapped".formatted(slotIndex));
        }
        return new MemoryAccess(bank, normalized % slotSize);
    }

    private void validateSlotIndex(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slotCount) {
            throw new IllegalArgumentException("slotIndex out of range: " + slotIndex);
        }
    }

    private record MemoryAccess(MemoryBank bank, int offset) {
    }
}
