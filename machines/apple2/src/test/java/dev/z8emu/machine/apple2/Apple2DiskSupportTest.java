package dev.z8emu.machine.apple2;

import dev.z8emu.machine.apple2.disk.Apple2DosDiskImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Apple2DiskSupportTest {
    @Test
    void diskSlotRomIsEmptyUntilExternalPromIsLoaded() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.insertDisk(Apple2DosDiskImage.fromDosOrderedBytes(dosImage()));

        assertEquals(0xFF, machine.board().cpuBus().readMemory(0xC600));
        assertEquals(0xFF, machine.board().cpuBus().readMemory(0xC65C));
    }

    @Test
    void diskSlotRomCanBeLoadedExternally() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        byte[] slotRom = new byte[0x100];
        put(slotRom, 0x00,
                0xA9, 0x42,
                0x85, 0x00,
                0x4C, 0x04, 0xC6
        );
        machine.loadDisk2SlotRom(slotRom);
        machine.bootDiskFromSlot6();

        runInstructions(machine, 3);

        assertEquals(0x42, machine.board().memory().read(0x0000));
        assertEquals(0xC604, machine.cpu().registers().pc());
    }

    @Test
    void diskSlotRomMustBeExactlyOnePage() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();

        assertThrows(IllegalArgumentException.class, () -> machine.loadDisk2SlotRom(new byte[0x80]));
    }

    @Test
    void diskControllerExposesNibbleStreamThroughSlot6ReadLatch() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.insertDisk(Apple2DosDiskImage.fromDosOrderedBytes(dosImage()));
        machine.loadProgram(nops(16_000), 0x0800);
        machine.setProgramCounter(0x0800);
        machine.board().cpuBus().readMemory(0xC0E9);
        machine.board().cpuBus().readMemory(0xC0EA);
        machine.board().cpuBus().readMemory(0xC0EE);

        boolean foundAddressPrologue = false;
        int previous2 = 0;
        int previous1 = 0;
        for (int i = 0; i < 1000; i++) {
            int value = machine.board().cpuBus().readMemory(0xC0EC);
            if (previous2 == 0xD5 && previous1 == 0xAA && value == 0x96) {
                foundAddressPrologue = true;
                break;
            }
            previous2 = previous1;
            previous1 = value;
            runInstructions(machine, 16);
        }

        assertTrue(foundAddressPrologue);
    }

    private static void runInstructions(Apple2Machine machine, int count) {
        for (int i = 0; i < count; i++) {
            machine.runInstruction();
        }
    }

    private static byte[] dosImage() {
        byte[] image = new byte[Apple2DosDiskImage.IMAGE_SIZE];
        fillLogicalSector(image, 0, 0, 0x01);
        image[1] = 0x42;
        fillLogicalSector(image, 0, 1, 0xA1);
        return image;
    }

    private static void fillLogicalSector(byte[] image, int track, int sector, int value) {
        int offset = ((track * Apple2DosDiskImage.SECTORS_PER_TRACK) + sector)
                * Apple2DosDiskImage.SECTOR_SIZE;
        for (int i = 0; i < Apple2DosDiskImage.SECTOR_SIZE; i++) {
            image[offset + i] = (byte) value;
        }
    }

    private static void put(byte[] memory, int address, int... values) {
        for (int i = 0; i < values.length; i++) {
            memory[(address + i) & 0xFF] = (byte) values[i];
        }
    }

    private static byte[] nops(int count) {
        byte[] bytes = new byte[count];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) 0xEA;
        }
        return bytes;
    }
}
