package dev.z8emu.machine.c64.device;

import dev.z8emu.machine.c64.media.C64CrtImage;
import dev.z8emu.machine.c64.media.C64CrtTestImages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C64EasyFlashCartridgeTest {
    @Test
    void constructionImmediatelyPresentsBankZeroInUltimaxMode() {
        C64EasyFlashCartridge cartridge = cartridge();

        assertEquals(0, cartridge.bankRegister());
        assertEquals(0, cartridge.controlRegister());
        assertTrue(cartridge.gameAsserted());
        assertFalse(cartridge.exromAsserted());
        assertEquals(0x61, cartridge.readRoml(0x123));
    }

    @Test
    void bankAndControlWritesApplyTheirHardwareMasks() {
        C64EasyFlashCartridge cartridge = cartridge();

        cartridge.writeBankRegister(0x41);
        cartridge.writeControlRegister(0xFF);

        assertEquals(1, cartridge.bankRegister());
        assertEquals(0x87, cartridge.controlRegister());
        assertEquals(0x63, cartridge.readRoml(0x123));
    }

    @Test
    void bankRegisterUsesAllSixBits() {
        C64CrtImage image = C64CrtImage.parse(C64CrtTestImages.crtBytes(
                "BANK33",
                new C64CrtTestImages.Chip(0, 1, 0x8000, C64CrtTestImages.filled(0x2000, 0x71)),
                new C64CrtTestImages.Chip(0, 33, 0x8000, C64CrtTestImages.filled(0x2000, 0x72))
        ));
        C64EasyFlashCartridge cartridge = new C64EasyFlashCartridge(image);

        cartridge.writeBankRegister(0x61);

        assertEquals(33, cartridge.bankRegister());
        assertEquals(0x72, cartridge.readRoml(0x123));
    }

    @Test
    void modeGameAndExromBitsDriveThePhysicalLines() {
        C64EasyFlashCartridge cartridge = cartridge();

        for (int value = 0; value < 8; value++) {
            cartridge.writeControlRegister(value);

            assertEquals((value & 0x02) != 0, cartridge.exromAsserted(), "EXROM for value " + value);
            boolean expectedGame = (value & 0x04) == 0 || (value & 0x01) != 0;
            assertEquals(expectedGame, cartridge.gameAsserted(), "GAME for value " + value);
        }
    }

    @Test
    void resetRestoresBootStateAndClearsRam() {
        C64EasyFlashCartridge cartridge = cartridge();
        cartridge.writeBankRegister(1);
        cartridge.writeControlRegister(7);
        cartridge.writeRam(0x123, 0xA5);

        cartridge.reset();

        assertEquals(0, cartridge.bankRegister());
        assertEquals(0, cartridge.controlRegister());
        assertTrue(cartridge.gameAsserted());
        assertFalse(cartridge.exromAsserted());
        assertEquals(0, cartridge.readRam(0x23));
    }

    private static C64EasyFlashCartridge cartridge() {
        return new C64EasyFlashCartridge(C64CrtTestImages.syntheticCart(0x61, 0x62, 0x63, 0x64));
    }
}
