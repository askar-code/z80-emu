package dev.z8emu.machine.apple2;

import dev.z8emu.platform.bus.io.IoAccess;
import java.util.Objects;

public final class Apple2SlotBus {
    private static final int SLOT_COUNT = 8;
    private static final int C800_EXPANSION_START = 0xC800;
    private static final int C800_EXPANSION_END_EXCLUSIVE = 0xD000;

    private final Apple2SlotCard[] slots = new Apple2SlotCard[SLOT_COUNT];
    private int selectedC800Slot = -1;

    public void install(int slot, Apple2SlotCard card) {
        validateSlot(slot);
        slots[slot] = Objects.requireNonNull(card, "card");
        if (selectedC800Slot == slot) {
            selectedC800Slot = -1;
        }
    }

    public void uninstall(int slot) {
        validateSlot(slot);
        slots[slot] = null;
        if (selectedC800Slot == slot) {
            selectedC800Slot = -1;
        }
    }

    public int readC0x(IoAccess access) {
        int slot = slotForC0x(access.address());
        Apple2SlotCard card = slots[slot];
        selectC800Slot(slot, card);
        return card == null ? 0x00 : card.readC0x(slotAccess(access));
    }

    public void writeC0x(IoAccess access, int value) {
        int slot = slotForC0x(access.address());
        Apple2SlotCard card = slots[slot];
        selectC800Slot(slot, card);
        if (card != null) {
            card.writeC0x(slotAccess(access), value);
        }
    }

    public int readCnxx(int address) {
        int normalized = address & 0xFFFF;
        int slot = slotForCnxx(normalized);
        Apple2SlotCard card = slots[slot];
        selectC800Slot(slot, card);
        return card == null ? 0xFF : card.readCnxx(normalized & 0xFF);
    }

    public boolean hasCnxxRom(int address) {
        int normalized = address & 0xFFFF;
        if (normalized < Apple2Memory.SLOT_ROM_START || normalized >= 0xC800) {
            return false;
        }
        Apple2SlotCard card = slots[slotForCnxx(normalized)];
        return card != null && card.hasCnxxRom();
    }

    public void writeCnxx(int address, int value) {
        int normalized = address & 0xFFFF;
        int slot = slotForCnxx(normalized);
        Apple2SlotCard card = slots[slot];
        selectC800Slot(slot, card);
        if (card != null) {
            card.writeCnxx(normalized & 0xFF, value & 0xFF);
        }
    }

    public boolean hasC800ExpansionRom() {
        Apple2SlotCard card = selectedC800Card();
        return card != null && card.usesC800ExpansionRom();
    }

    public int readC800(int address) {
        int normalized = address & 0xFFFF;
        if (normalized < C800_EXPANSION_START || normalized >= C800_EXPANSION_END_EXCLUSIVE) {
            return 0xFF;
        }
        Apple2SlotCard card = selectedC800Card();
        return card == null ? 0xFF : card.readC800(normalized - C800_EXPANSION_START);
    }

    public void writeC800(int address, int value) {
        int normalized = address & 0xFFFF;
        if (normalized < C800_EXPANSION_START || normalized >= C800_EXPANSION_END_EXCLUSIVE) {
            return;
        }
        Apple2SlotCard card = selectedC800Card();
        if (card != null) {
            card.writeC800(normalized - C800_EXPANSION_START, value & 0xFF);
        }
    }

    private Apple2SlotCard selectedC800Card() {
        if (selectedC800Slot < 0) {
            return null;
        }
        Apple2SlotCard card = slots[selectedC800Slot];
        return card != null && card.usesC800ExpansionRom() ? card : null;
    }

    private void selectC800Slot(int slot, Apple2SlotCard card) {
        if (card != null && card.usesC800ExpansionRom()) {
            selectedC800Slot = slot;
        }
    }

    private static int slotForC0x(int address) {
        int slot = (((address & 0xFF) >>> 4) - 8);
        validateSlot(slot);
        return slot;
    }

    private static int slotForCnxx(int address) {
        int slot = (address >>> 8) & 0x07;
        validateSlot(slot);
        return slot;
    }

    private static IoAccess slotAccess(IoAccess access) {
        return new IoAccess(access.address(), access.address() & 0x0F, access.tState(), access.phaseTStates());
    }

    private static void validateSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Apple II slot out of range: " + slot);
        }
    }
}
