package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.device.C64CiaDevice;
import dev.z8emu.machine.c64.device.C64SidDevice;
import dev.z8emu.machine.c64.device.C64VideoDevice;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64BusTest {
    private static final int BASIC_SENTINEL = 0xB0;
    private static final int KERNAL_SENTINEL = 0xE0;
    private static final int CHAR_SENTINEL = 0xC0;
    private static final int BASIC_RAM_SENTINEL = 0xA1;
    private static final int IO_RAM_SENTINEL = 0xD1;
    private static final int COLOR_RAM_UNDERLAY_SENTINEL = 0xD9;
    private static final int KERNAL_RAM_SENTINEL = 0xE1;
    private static final int COLOR_SENTINEL = 0x0D;

    private static final int[][] PLA_ROWS = {
            {1, 1, 1, BASIC_SENTINEL, 0xF0, COLOR_SENTINEL, KERNAL_SENTINEL},
            {1, 1, 0, BASIC_SENTINEL, CHAR_SENTINEL, CHAR_SENTINEL, KERNAL_SENTINEL},
            {0, 1, 1, BASIC_RAM_SENTINEL, 0xF0, COLOR_SENTINEL, KERNAL_SENTINEL},
            {0, 1, 0, BASIC_RAM_SENTINEL, CHAR_SENTINEL, CHAR_SENTINEL, KERNAL_SENTINEL},
            {1, 0, 1, BASIC_RAM_SENTINEL, 0xF0, COLOR_SENTINEL, KERNAL_RAM_SENTINEL},
            {1, 0, 0, BASIC_RAM_SENTINEL, CHAR_SENTINEL, CHAR_SENTINEL, KERNAL_RAM_SENTINEL},
            {0, 0, 1, BASIC_RAM_SENTINEL, IO_RAM_SENTINEL, COLOR_RAM_UNDERLAY_SENTINEL, KERNAL_RAM_SENTINEL},
            {0, 0, 0, BASIC_RAM_SENTINEL, IO_RAM_SENTINEL, COLOR_RAM_UNDERLAY_SENTINEL, KERNAL_RAM_SENTINEL}
    };

    private C64Memory memory;
    private C64Bus bus;
    private C64VideoDevice video;
    private C64SidDevice sid;
    private C64CiaDevice cia1;
    private C64CiaDevice cia2;

    @BeforeEach
    void setUp() {
        byte[] basicRom = filled(C64Memory.BASIC_ROM_SIZE, BASIC_SENTINEL);
        byte[] kernalRom = filled(C64Memory.KERNAL_ROM_SIZE, KERNAL_SENTINEL);
        byte[] chargenRom = filled(C64Memory.CHAR_ROM_SIZE, CHAR_SENTINEL);
        memory = new C64Memory(basicRom, kernalRom, chargenRom);
        C64CpuPort cpuPort = new C64CpuPort();
        cpuPort.reset();
        video = new C64VideoDevice(memory);
        sid = new C64SidDevice(C64ModelConfig.pal().cpuClockHz());
        cia1 = new C64CiaDevice();
        cia2 = new C64CiaDevice();
        cia1.reset();
        cia2.reset();
        bus = new C64Bus(new TStateCounter(), memory, cpuPort, video, sid, cia1, cia2);

        memory.writeRam(0xA123, BASIC_RAM_SENTINEL);
        memory.writeRam(0xD123, IO_RAM_SENTINEL);
        memory.writeRam(0xD923, COLOR_RAM_UNDERLAY_SENTINEL);
        memory.writeRam(0xE123, KERNAL_RAM_SENTINEL);
        memory.writeColorRam(0x0123, COLOR_SENTINEL);
    }

    @Test
    void plaTruthTableSelectsTheDocumentedSourceInAllEightModes() {
        for (int rowIndex = 0; rowIndex < PLA_ROWS.length; rowIndex++) {
            int[] row = PLA_ROWS[rowIndex];
            driveMode(row[0], row[1], row[2]);

            assertEquals(row[3], bus.readMemory(0xA123), "A000 source in PLA row " + rowIndex);
            assertEquals(row[4], bus.readMemory(0xD123), "D000 source in PLA row " + rowIndex);
            assertEquals(row[5], bus.readMemory(0xD923), "D800 source in PLA row " + rowIndex);
            assertEquals(row[6], bus.readMemory(0xE123), "E000 source in PLA row " + rowIndex);
        }
    }

    @Test
    void writesUnderBasicKernalAndCharacterRomLandInRamInEveryRomMode() {
        for (int rowIndex = 0; rowIndex < PLA_ROWS.length; rowIndex++) {
            int[] row = PLA_ROWS[rowIndex];
            int loram = row[0];
            int hiram = row[1];
            int charen = row[2];
            driveMode(loram, hiram, charen);

            if (loram == 1 && hiram == 1) {
                int value = 0x10 + rowIndex;
                bus.writeMemory(0xA234, value);
                assertEquals(value, memory.readRam(0xA234), "BASIC underlay in PLA row " + rowIndex);
            }
            if ((loram == 1 || hiram == 1) && charen == 0) {
                int value = 0x20 + rowIndex;
                bus.writeMemory(0xD234, value);
                assertEquals(value, memory.readRam(0xD234), "character underlay in PLA row " + rowIndex);
            }
            if (hiram == 1) {
                int value = 0x30 + rowIndex;
                bus.writeMemory(0xE234, value);
                assertEquals(value, memory.readRam(0xE234), "KERNAL underlay in PLA row " + rowIndex);
            }
        }
    }

    @Test
    void writesToUnmappedIoAreDroppedWithoutTouchingUnderlyingRam() {
        driveMode(1, 1, 1);
        memory.writeRam(0xDE05, 0x44);

        bus.writeMemory(0xDE05, 0x99);

        assertEquals(0xFF, bus.readMemory(0xDE05));
        assertEquals(0x44, memory.readRam(0xDE05));
    }

    @Test
    void vicRegistersMirrorAcrossTheD000Window() {
        driveMode(1, 1, 1);

        bus.writeMemory(0xD000, 0x42);

        assertEquals(0x42, bus.readMemory(0xD000));
        assertEquals(0x42, bus.readMemory(0xD040));
        assertEquals(0x42, bus.readMemory(0xD3C0));
        assertEquals(0xFF, bus.readMemory(0xD3FB));
    }

    @Test
    void vicRasterInterruptCanBeAcknowledgedThroughTheBus() {
        driveMode(1, 1, 1);
        bus.writeMemory(0xD012, 0x01);
        bus.writeMemory(0xD01A, 0x01);

        video.onTStatesElapsed(C64VideoDevice.CYCLES_PER_LINE);

        assertEquals(0xF1, bus.readMemory(0xD019));
        bus.writeMemory(0xD019, 0x01);
        assertEquals(0x70, bus.readMemory(0xD019));
    }

    @Test
    void sidRegistersMirrorAcrossTheD400ToD7ffWindow() {
        driveMode(1, 1, 1);
        bus.writeMemory(0xD40E, 0x45);
        bus.writeMemory(0xD40F, 0x1D);
        bus.writeMemory(0xD412, 0x20);
        sid.onTStatesElapsed(1_000);

        int baseOscillator = bus.readMemory(0xD41B);
        assertEquals(baseOscillator, bus.readMemory(0xD7DB));
        assertNotEquals(0xFF, baseOscillator);

        sid.reset();
        bus.writeMemory(0xD50E, 0x45);
        bus.writeMemory(0xD50F, 0x1D);
        bus.writeMemory(0xD412, 0x20);
        sid.onTStatesElapsed(1_000);
        int first = bus.readMemory(0xD41B);
        sid.onTStatesElapsed(1_000);

        assertNotEquals(first, bus.readMemory(0xD41B));
    }

    @Test
    void ciaOneRegistersMirrorAcrossTheDcWindow() {
        driveMode(1, 1, 1);

        bus.writeMemory(0xDC04, 0x42);
        bus.writeMemory(0xDC05, 0x00);

        assertEquals(0x42, bus.readMemory(0xDC44));
        assertEquals(0x42, bus.readMemory(0xDCF4));
    }

    @Test
    void ciaTwoAtDd00IsIndependentFromCiaOne() {
        driveMode(1, 1, 1);

        bus.writeMemory(0xDC02, 0xA5);
        bus.writeMemory(0xDD02, 0x5A);

        assertEquals(0xA5, bus.readMemory(0xDC02));
        assertEquals(0x5A, bus.readMemory(0xDD02));
        assertEquals(0xA5, cia1.readRegister(0x02));
        assertEquals(0x5A, cia2.readRegister(0x02));
    }

    @Test
    void ciaRegistersAreHiddenWhenAllRamIsBankedIn() {
        driveMode(1, 1, 1);
        bus.writeMemory(0xDC02, 0xF0);
        memory.writeRam(0xDC02, 0x3C);

        driveMode(0, 0, 1);

        assertEquals(0x3C, bus.readMemory(0xDC02));
        assertEquals(0xF0, cia1.readRegister(0x02));
    }

    @Test
    void colorRamStoresNibblesAtBothEndsOnlyWhenIoIsVisible() {
        driveMode(1, 1, 1);
        bus.writeMemory(0xD800, 0xFF);
        bus.writeMemory(0xDBFF, 0xAB);

        assertEquals(0x0F, bus.readMemory(0xD800));
        assertEquals(0x0B, bus.readMemory(0xDBFF));
        assertEquals(0x0B, memory.readColorRam(0x03FF));

        driveMode(1, 1, 0);
        bus.writeMemory(0xD800, 0x07);
        assertEquals(CHAR_SENTINEL, bus.readMemory(0xD800));
        assertEquals(0x0F, memory.readColorRam(0x0000));
        assertEquals(0x07, memory.readRam(0xD800));

        driveMode(0, 0, 1);
        bus.writeMemory(0xD800, 0x66);
        assertEquals(0x66, bus.readMemory(0xD800));
        assertEquals(0x0F, memory.readColorRam(0x0000));
    }

    @Test
    void cpuPortAccessesNeverTouchRamAtZeroAndOne() {
        assertEquals(0x00, memory.readRam(0x0000));
        assertEquals(0x00, memory.readRam(0x0001));

        bus.writeMemory(0x0000, 0xFF);
        bus.writeMemory(0x0001, 0xA5);

        assertEquals(0xFF, bus.readMemory(0x0000));
        assertEquals(0xA5, bus.readMemory(0x0001));
        assertEquals(0x00, memory.readRam(0x0000));
        assertEquals(0x00, memory.readRam(0x0001));
    }

    @Test
    void lowAndC000RamRemainVisibleInEveryPlaMode() {
        for (int rowIndex = 0; rowIndex < PLA_ROWS.length; rowIndex++) {
            int[] row = PLA_ROWS[rowIndex];
            driveMode(row[0], row[1], row[2]);
            int lowValue = 0x40 + rowIndex;
            int c000Value = 0x50 + rowIndex;

            bus.writeMemory(0x0002, lowValue);
            bus.writeMemory(0xC123, c000Value);

            assertEquals(lowValue, bus.readMemory(0x0002), "low RAM in PLA row " + rowIndex);
            assertEquals(c000Value, bus.readMemory(0xC123), "C000 RAM in PLA row " + rowIndex);
        }
    }

    @Test
    void resetClearsRamAndColorRamButKeepsDefensiveRomCopies() {
        byte[] basicRom = filled(C64Memory.BASIC_ROM_SIZE, 0x61);
        byte[] kernalRom = filled(C64Memory.KERNAL_ROM_SIZE, 0x62);
        byte[] chargenRom = filled(C64Memory.CHAR_ROM_SIZE, 0x63);
        C64Memory isolatedMemory = new C64Memory(basicRom, kernalRom, chargenRom);
        basicRom[0] = 0;
        kernalRom[0] = 0;
        chargenRom[0] = 0;
        isolatedMemory.writeRam(0x2000, 0xFF);
        isolatedMemory.writeColorRam(0, 0x0F);

        isolatedMemory.reset();

        assertEquals(0x00, isolatedMemory.readRam(0x2000));
        assertEquals(0x00, isolatedMemory.readColorRam(0));
        assertEquals(0x61, isolatedMemory.readBasicRom(0));
        assertEquals(0x62, isolatedMemory.readKernalRom(0));
        assertEquals(0x63, isolatedMemory.readCharRom(0));
    }

    private void driveMode(int loram, int hiram, int charen) {
        bus.writeMemory(0x0000, 0x07);
        bus.writeMemory(0x0001, loram | (hiram << 1) | (charen << 2));
    }

    private static byte[] filled(int size, int value) {
        byte[] image = new byte[size];
        Arrays.fill(image, (byte) value);
        return image;
    }
}
