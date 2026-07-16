package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.media.C64PrgImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class C64PrgLoaderTest {
    @Test
    void injectsBasicPayloadAndUpdatesPointerChain() {
        C64Machine machine = syntheticMachine();
        C64Memory memory = machine.board().memory();
        memory.writeRam(0x2B, 0x01);
        memory.writeRam(0x2C, 0x08);
        byte[] payload = new byte[15];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (0x40 + index);
        }

        C64PrgLoader.inject(machine, new C64PrgImage(0x0801, payload));

        for (int index = 0; index < payload.length; index++) {
            assertEquals(Byte.toUnsignedInt(payload[index]), memory.readRam(0x0801 + index));
        }
        assertEquals(0x01, memory.readRam(0x2B));
        assertEquals(0x08, memory.readRam(0x2C));
        for (int pointer = 0x2D; pointer <= 0x31; pointer += 2) {
            assertEquals(0x10, memory.readRam(pointer));
            assertEquals(0x08, memory.readRam(pointer + 1));
        }
    }

    @Test
    void nonBasicPayloadLeavesPointerChainUntouched() {
        C64Machine machine = syntheticMachine();
        C64Memory memory = machine.board().memory();
        for (int address = 0x2D; address <= 0x32; address++) {
            memory.writeRam(address, address);
        }

        C64PrgLoader.inject(machine, new C64PrgImage(0x2000, new byte[]{0x12, 0x34}));

        assertEquals(0x12, memory.readRam(0x2000));
        assertEquals(0x34, memory.readRam(0x2001));
        for (int address = 0x2D; address <= 0x32; address++) {
            assertEquals(address, memory.readRam(address));
        }
    }

    @Test
    void selectsRunOrSysStartCommand() {
        C64PrgImage basic = new C64PrgImage(0x0801, new byte[]{0x00});
        C64PrgImage machineCode = new C64PrgImage(0x2000, new byte[]{0x00});

        assertEquals("RUN\r", C64PrgLoader.startCommand(basic, null));
        assertEquals("SYS 49152\r", C64PrgLoader.startCommand(basic, 0xC000));
        assertEquals("SYS 8192\r", C64PrgLoader.startCommand(machineCode, null));
    }

    private static C64Machine syntheticMachine() {
        return new C64Machine(
                new byte[C64Memory.BASIC_ROM_SIZE],
                new byte[C64Memory.KERNAL_ROM_SIZE],
                new byte[C64Memory.CHAR_ROM_SIZE]
        );
    }
}
