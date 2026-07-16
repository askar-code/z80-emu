package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.device.C64EasyFlashCartridge;
import dev.z8emu.machine.c64.media.C64CrtImage;
import dev.z8emu.machine.c64.media.C64CrtTestImages;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C64CartridgeBootTest {
    @Test
    void machineFetchesItsFirstResetVectorFromUltimaxCartridge() {
        byte[] hirom = bootableHirom();
        C64CrtImage image = C64CrtImage.parse(C64CrtTestImages.crtBytes(
                "EASYFLASH BOOT",
                new C64CrtTestImages.Chip(0, 0, 0x8000, new byte[0x2000]),
                new C64CrtTestImages.Chip(2, 0, 0xE000, hirom)
        ));
        C64EasyFlashCartridge cartridge = new C64EasyFlashCartridge(image);

        C64Machine machine = new C64Machine(
                new byte[C64Memory.BASIC_ROM_SIZE],
                new byte[C64Memory.KERNAL_ROM_SIZE],
                new byte[C64Memory.CHAR_ROM_SIZE],
                cartridge
        );
        for (int instruction = 0; instruction < 50_000; instruction++) {
            machine.runInstruction();
        }

        assertTrue(C64ScreenText.contains(machine, "EASYFLASH OK"));
        assertEquals(0x00, machine.board().cpuBus().readMemory(0xFFFC));
        assertEquals(0xE0, machine.board().cpuBus().readMemory(0xFFFD));
    }

    private static byte[] bootableHirom() {
        byte[] hirom = new byte[0x2000];
        Arrays.fill(hirom, (byte) 0xEA);
        ByteArrayOutputStream code = new ByteArrayOutputStream();
        ldaSta(code, 0x1B, 0xD011);
        ldaSta(code, 0xC8, 0xD016);
        ldaSta(code, 0x14, 0xD018);
        ldaSta(code, 0x0E, 0xD020);
        ldaSta(code, 0x06, 0xD021);
        byte[] screenCodes = {
                0x05, 0x01, 0x13, 0x19, 0x06, 0x0C, 0x01, 0x13, 0x08, 0x20, 0x0F, 0x0B
        };
        for (int index = 0; index < screenCodes.length; index++) {
            ldaSta(code, screenCodes[index], 0x0400 + index);
        }
        int loopAddress = 0xE000 + code.size();
        code.write(0x4C);
        code.write(loopAddress & 0xFF);
        code.write(loopAddress >>> 8);
        byte[] program = code.toByteArray();
        System.arraycopy(program, 0, hirom, 0, program.length);
        hirom[0x1FFC] = 0x00;
        hirom[0x1FFD] = (byte) 0xE0;
        return hirom;
    }

    private static void ldaSta(ByteArrayOutputStream code, int value, int address) {
        code.write(0xA9);
        code.write(value);
        code.write(0x8D);
        code.write(address & 0xFF);
        code.write(address >>> 8);
    }
}
