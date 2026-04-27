package dev.z8emu.app.desktop;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Apple2BinaryScannerTest {
    @Test
    void findsPlainAndAppleHighBitPrintableStrings() {
        byte[] data = new byte[]{
                0x00,
                'P', '.', 'O', '.', 'P', '.',
                0x00,
                (byte) 0xC2, (byte) 0xCF, (byte) 0xCF, (byte) 0xD4,
                0x00
        };

        List<Apple2BinaryScanner.AsciiString> strings = Apple2BinaryScanner.printableStrings(data, 4);

        assertEquals(2, strings.size());
        assertEquals(1, strings.get(0).offset());
        assertEquals("P.O.P.", strings.get(0).value());
        assertEquals(8, strings.get(1).offset());
        assertEquals("BOOT", strings.get(1).value());
    }

    @Test
    void findsAbsoluteReferencesWithLoadedProgramCounters() {
        byte[] data = new byte[]{
                0x20, 0x31, 0x20,
                (byte) 0xAD, 0x00, (byte) 0xC0,
                0x4C, 0x00, 0x08
        };

        List<Apple2BinaryScanner.AbsoluteReference> refs = Apple2BinaryScanner.absoluteReferences(data, 0x2000);

        assertEquals(3, refs.size());
        assertEquals(0x2000, refs.get(0).pc());
        assertEquals("JSR", refs.get(0).mnemonic());
        assertEquals(0x2031, refs.get(0).target());
        assertEquals(0x2003, refs.get(1).pc());
        assertEquals("LDA", refs.get(1).mnemonic());
        assertEquals(0xC000, refs.get(1).target());
        assertEquals("JMP", refs.get(2).mnemonic());
        assertEquals(0x0800, refs.get(2).target());
    }

    @Test
    void filtersAppleIoPageReferences() {
        byte[] data = new byte[]{
                (byte) 0xAD, 0x00, (byte) 0xC0,
                (byte) 0x8D, 0x00, 0x20,
                0x2C, 0x17, (byte) 0xC0
        };

        List<Apple2BinaryScanner.AbsoluteReference> refs = Apple2BinaryScanner.ioReferences(data, 0x0800);

        assertEquals(2, refs.size());
        assertEquals(0xC000, refs.get(0).target());
        assertEquals(0xC017, refs.get(1).target());
    }

    @Test
    void describesAppleIoAreas() {
        assertEquals("system-switches", Apple2BinaryScanner.apple2IoArea(0xC005));
        assertEquals("system-io", Apple2BinaryScanner.apple2IoArea(0xC034));
        assertEquals("language-card", Apple2BinaryScanner.apple2IoArea(0xC081));
        assertEquals("slot6-io", Apple2BinaryScanner.apple2IoArea(0xC0E8));
        assertEquals("slot6-rom", Apple2BinaryScanner.apple2IoArea(0xC600));
        assertEquals("slot-expansion-rom", Apple2BinaryScanner.apple2IoArea(0xCC40));
        assertEquals("other", Apple2BinaryScanner.apple2IoArea(0x2000));
    }
}
