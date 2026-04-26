package dev.z8emu.machine.apple2;

import dev.z8emu.platform.bus.io.IoAccess;
import java.util.Objects;

public final class Apple2SlotBus {
    private static final int SLOT_COUNT = 8;
    private final Apple2SlotCard[] slots = new Apple2SlotCard[SLOT_COUNT];

    public void install(int slot, Apple2SlotCard card) {
        validateSlot(slot);
        slots[slot] = Objects.requireNonNull(card, "card");
    }

    public int readC0x(IoAccess access) {
        Apple2SlotCard card = slots[slotForC0x(access.address())];
        return card == null ? 0x00 : card.readC0x(slotAccess(access));
    }

    public void writeC0x(IoAccess access, int value) {
        Apple2SlotCard card = slots[slotForC0x(access.address())];
        if (card != null) {
            card.writeC0x(slotAccess(access), value);
        }
    }

    public int readCnxx(int address) {
        int normalized = address & 0xFFFF;
        Apple2SlotCard card = slots[(normalized >>> 8) & 0x07];
        return card == null ? 0xFF : card.readCnxx(normalized & 0xFF);
    }

    public void writeCnxx(int address, int value) {
        int normalized = address & 0xFFFF;
        Apple2SlotCard card = slots[(normalized >>> 8) & 0x07];
        if (card != null) {
            card.writeCnxx(normalized & 0xFF, value & 0xFF);
        }
    }

    private static int slotForC0x(int address) {
        int slot = (((address & 0xFF) >>> 4) - 8);
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
