package dev.z8emu.machine.cpc;

import dev.z8emu.machine.cpc.disk.CpcDiskSector;
import dev.z8emu.machine.cpc.disk.CpcDskImage;
import dev.z8emu.machine.cpc.disk.CpcDskLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpcDiskSupportTest {
    @Test
    void loadsStandardDskTrackAndSectorData() throws IOException {
        CpcDskImage image = CpcDskLoader.load(standardDskImage());

        assertEquals(1, image.trackCount());
        assertEquals(1, image.sideCount());

        CpcDiskSector sector = image.findSector(0, 0, 0, 0, 0xC1, 2).orElseThrow();

        assertEquals(0xC1, sector.sectorId());
        assertEquals(2, sector.sizeCode());
        assertEquals(512, sector.data().length);
        assertEquals(0x00, sector.data()[0] & 0xFF);
        assertEquals(0xFF, sector.data()[255] & 0xFF);
        assertEquals(0x00, sector.data()[256] & 0xFF);
    }

    @Test
    void loadsExtendedDskWithPerSectorDataLengths() throws IOException {
        CpcDskImage image = CpcDskLoader.load(extendedDskImage());

        assertEquals(2, image.trackCount());
        assertEquals(1, image.sideCount());
        assertTrue(image.track(1, 0).isEmpty());

        CpcDiskSector sector = image.findSector(0, 0, 0, 0, 0xC1, 2).orElseThrow();

        assertEquals(600, sector.data().length);
        assertEquals(0x00, sector.data()[0] & 0xFF);
        assertEquals(0x57, sector.data()[599] & 0xFF);
    }

    @Test
    void fdcReadsSectorThroughCpcIoPorts() throws IOException {
        CpcMachine machine = new CpcMachine(new byte[0x4000], CpcDskLoader.load(standardDskImage()));

        machine.board().cpuBus().writePort(0xFA7E, 0x01);
        writeFdc(machine, 0x07, 0x00);
        writeFdc(machine, 0x08);

        assertEquals(0x20, readFdc(machine));
        assertEquals(0x00, readFdc(machine));

        writeFdc(machine, 0x46, 0x00, 0x00, 0x00, 0xC1, 0x02, 0xC1, 0x2A, 0xFF);

        byte[] actual = new byte[512];
        for (int i = 0; i < actual.length; i++) {
            assertTrue((machine.board().cpuBus().readPort(0xFB7E) & 0xC0) == 0xC0);
            actual[i] = (byte) readFdc(machine);
        }

        assertArrayEquals(sectorPattern(), actual);

        assertEquals(0x00, readFdc(machine));
        assertEquals(0x00, readFdc(machine));
        assertEquals(0x00, readFdc(machine));
        assertEquals(0x00, readFdc(machine));
        assertEquals(0x00, readFdc(machine));
        assertEquals(0xC1, readFdc(machine));
        assertEquals(0x02, readFdc(machine));
        assertEquals(0x80, machine.board().cpuBus().readPort(0xFB7E));
    }

    @Test
    void fdcReadIdReturnsFirstSectorOnCurrentTrack() throws IOException {
        CpcMachine machine = new CpcMachine(new byte[0x4000], CpcDskLoader.load(standardDskImage()));

        machine.board().cpuBus().writePort(0xFA7E, 0x01);
        writeFdc(machine, 0x4A, 0x00);

        assertEquals(0x00, readFdc(machine));
        assertEquals(0x00, readFdc(machine));
        assertEquals(0x00, readFdc(machine));
        assertEquals(0x00, readFdc(machine));
        assertEquals(0x00, readFdc(machine));
        assertEquals(0xC1, readFdc(machine));
        assertEquals(0x02, readFdc(machine));
    }

    private static void writeFdc(CpcMachine machine, int... values) {
        for (int value : values) {
            assertEquals(0x80, machine.board().cpuBus().readPort(0xFB7E) & 0xC0);
            machine.board().cpuBus().writePort(0xFB7F, value);
        }
    }

    private static int readFdc(CpcMachine machine) {
        assertEquals(0xC0, machine.board().cpuBus().readPort(0xFB7E) & 0xC0);
        return machine.board().cpuBus().readPort(0xFB7F);
    }

    private static byte[] standardDskImage() {
        byte[] image = new byte[0x400];
        putAscii(image, 0x000, "MV - CPCEMU Disk-File\r\nDisk-Info\r\n");
        image[0x30] = 1;
        image[0x31] = 1;
        image[0x32] = 0x00;
        image[0x33] = 0x03;

        putAscii(image, 0x100, "Track-Info\r\n");
        image[0x110] = 0;
        image[0x111] = 0;
        image[0x114] = 2;
        image[0x115] = 1;
        image[0x116] = 0x2A;
        image[0x117] = (byte) 0xE5;

        image[0x118] = 0;
        image[0x119] = 0;
        image[0x11A] = (byte) 0xC1;
        image[0x11B] = 2;
        image[0x11C] = 0;
        image[0x11D] = 0;

        byte[] sector = sectorPattern();
        System.arraycopy(sector, 0, image, 0x200, sector.length);
        return image;
    }

    private static byte[] extendedDskImage() {
        byte[] image = new byte[0x500];
        putAscii(image, 0x000, "EXTENDED CPC DSK File\r\nDisk-Info\r\n");
        image[0x30] = 2;
        image[0x31] = 1;
        image[0x34] = 0x04;
        image[0x35] = 0x00;

        putAscii(image, 0x100, "Track-Info\r\n");
        image[0x110] = 0;
        image[0x111] = 0;
        image[0x114] = 2;
        image[0x115] = 1;
        image[0x116] = 0x2A;
        image[0x117] = (byte) 0xE5;

        image[0x118] = 0;
        image[0x119] = 0;
        image[0x11A] = (byte) 0xC1;
        image[0x11B] = 2;
        image[0x11C] = 0;
        image[0x11D] = 0;
        image[0x11E] = 0x58;
        image[0x11F] = 0x02;

        for (int i = 0; i < 600; i++) {
            image[0x200 + i] = (byte) i;
        }
        return image;
    }

    private static byte[] sectorPattern() {
        byte[] sector = new byte[512];
        for (int i = 0; i < sector.length; i++) {
            sector[i] = (byte) i;
        }
        return sector;
    }

    private static void putAscii(byte[] target, int offset, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }
}
