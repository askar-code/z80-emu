package dev.z8emu.machine.apple2;

import dev.z8emu.platform.bus.io.IoAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Apple2SlotBusTest {
    @Test
    void routesC0xAccessesToInstalledSlotWithLocalRegisterOffset() {
        Apple2SlotBus slotBus = new Apple2SlotBus();
        RecordingSlotCard card = new RecordingSlotCard();
        slotBus.install(6, card);

        slotBus.writeC0x(new IoAccess(0xC0E9, 0x69, 0, 0), 0x42);

        assertEquals(0x09, card.lastC0xOffset);
        assertEquals(0x42, card.lastC0xWrite);
        assertEquals(0x86, slotBus.readC0x(new IoAccess(0xC0E6, 0, 0, 0)));
    }

    @Test
    void routesCnxxReadsToInstalledSlotRomWindow() {
        Apple2SlotBus slotBus = new Apple2SlotBus();
        RecordingSlotCard card = new RecordingSlotCard();
        slotBus.install(6, card);

        assertEquals(0xA5, slotBus.readCnxx(0xC65C));
        assertEquals(0xFF, slotBus.readCnxx(0xC55C));
    }

    @Test
    void routesSharedC800ExpansionWindowToSelectedSlotCard() {
        Apple2SlotBus slotBus = new Apple2SlotBus();
        RecordingSlotCard card = new RecordingSlotCard();
        card.usesC800ExpansionRom = true;
        slotBus.install(6, card);

        assertEquals(0xFF, slotBus.readC800(0xC800));

        assertEquals(0xA5, slotBus.readCnxx(0xC65C));
        assertEquals(0xC8, slotBus.readC800(0xC800));
        slotBus.writeC800(0xCBFE, 0x42);

        assertEquals(0x03FE, card.lastC800Offset);
        assertEquals(0x42, card.lastC800Write);
    }

    private static final class RecordingSlotCard implements Apple2SlotCard {
        private int lastC0xOffset;
        private int lastC0xWrite;
        private boolean usesC800ExpansionRom;
        private int lastC800Offset = -1;
        private int lastC800Write = -1;

        @Override
        public int readC0x(IoAccess access) {
            return 0x80 | access.offset();
        }

        @Override
        public void writeC0x(IoAccess access, int value) {
            lastC0xOffset = access.offset();
            lastC0xWrite = value;
        }

        @Override
        public int readCnxx(int offset) {
            return offset == 0x5C ? 0xA5 : 0x00;
        }

        @Override
        public int readC800(int offset) {
            return 0xC8 | (offset & 0x07);
        }

        @Override
        public void writeC800(int offset, int value) {
            lastC800Offset = offset;
            lastC800Write = value;
        }

        @Override
        public boolean usesC800ExpansionRom() {
            return usesC800ExpansionRom;
        }
    }
}
