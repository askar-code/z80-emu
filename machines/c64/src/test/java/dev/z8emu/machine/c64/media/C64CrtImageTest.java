package dev.z8emu.machine.c64.media;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static dev.z8emu.machine.c64.media.C64CrtTestImages.crtBytes;
import static dev.z8emu.machine.c64.media.C64CrtTestImages.filled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C64CrtImageTest {
    @Test
    void parsesTwoBanksAndIgnoresAdvisoryPacketLengths() {
        byte[] bytes = crtBytes(
                "TWO BANK CART",
                chip(0, 0, 0x8000, 0x11),
                chip(2, 0, 0xA000, 0x12),
                chip(0, 1, 0x8000, 0x21),
                chip(2, 1, 0xA000, 0x22)
        );
        Arrays.fill(bytes, 0x44, 0x48, (byte) 0);

        C64CrtImage image = C64CrtImage.parse(bytes);

        assertEquals("TWO BANK CART", image.name());
        assertEquals(2, image.bankCount());
        assertEquals(0x11, image.read(0, false, 0x123));
        assertEquals(0x12, image.read(0, true, 0x123));
        assertEquals(0x21, image.read(1, false, 0x123));
        assertEquals(0x22, image.read(1, true, 0x123));
    }

    @Test
    void acceptsE000AsHirom() {
        C64CrtImage image = C64CrtImage.parse(crtBytes(
                "E000 HIROM",
                chip(0, 0, 0xE000, 0x5E)
        ));

        assertEquals(0x5E, image.read(0, true, 0x456));
    }

    @Test
    void splitsCombinedPacketIntoLoromAndHirom() {
        byte[] data = new byte[0x4000];
        Arrays.fill(data, 0, 0x2000, (byte) 0x31);
        Arrays.fill(data, 0x2000, 0x4000, (byte) 0x32);

        C64CrtImage image = C64CrtImage.parse(crtBytes(
                "COMBINED",
                new C64CrtTestImages.Chip(0, 0, 0x8000, data)
        ));

        assertEquals(0x31, image.read(0, false, 0x1FFF));
        assertEquals(0x32, image.read(0, true, 0x1FFF));
    }

    @Test
    void rejectsMalformedHeadersAndChips() {
        byte[] wrongMagic = crtBytes("BAD MAGIC");
        wrongMagic[0] = 0;
        assertThrows(IllegalArgumentException.class, () -> C64CrtImage.parse(wrongMagic));

        byte[] wrongHardware = crtBytes("BAD TYPE");
        wrongHardware[0x17] = 33;
        IllegalArgumentException hardwareFailure = assertThrows(
                IllegalArgumentException.class,
                () -> C64CrtImage.parse(wrongHardware)
        );
        assertTrue(hardwareFailure.getMessage().contains("33"));

        assertThrows(IllegalArgumentException.class, () -> C64CrtImage.parse(crtBytes(
                "BAD BANK",
                new C64CrtTestImages.Chip(0, 64, 0x8000, filled(0x2000, 0))
        )));
        assertThrows(IllegalArgumentException.class, () -> C64CrtImage.parse(crtBytes(
                "BAD ADDRESS",
                new C64CrtTestImages.Chip(0, 0, 0x9000, filled(0x2000, 0))
        )));

        byte[] truncated = crtBytes("TRUNCATED", chip(0, 0, 0x8000, 0x44));
        truncated = Arrays.copyOf(truncated, truncated.length - 1);
        byte[] finalTruncated = truncated;
        assertThrows(IllegalArgumentException.class, () -> C64CrtImage.parse(finalTruncated));
    }

    @Test
    void unpopulatedBanksReadAsErasedFlash() {
        C64CrtImage image = C64CrtImage.parse(crtBytes(
                "SPARSE",
                chip(0, 1, 0x8000, 0x71)
        ));

        assertEquals(0xFF, image.read(0, false, 0));
        assertEquals(0xFF, image.read(0, true, 0x1FFF));
        assertEquals(0xFF, image.read(63, false, 0x123));
    }

    private static C64CrtTestImages.Chip chip(int type, int bank, int loadAddress, int sentinel) {
        return new C64CrtTestImages.Chip(type, bank, loadAddress, filled(0x2000, sentinel));
    }
}
