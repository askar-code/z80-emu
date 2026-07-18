package dev.z8emu.machine.spectrum;

import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumRamResetTest {
    @Test
    void coldPowerOnZeroFillsEveryPhysicalRamBank() {
        Spectrum48kMachine spectrum48 = Spectrum48kMachine.withBlankRom();
        Spectrum128Machine spectrum128 = blankSpectrum128();

        assertAllRamEquals(spectrum48.board().memory(), 3, 0);
        assertAllRamEquals(spectrum128.board().memory(), 8, 0);
    }

    @Test
    void warmResetPreservesAll48kRamAndRestoresTheStockMapping() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        fillRam(machine.board().memory(), 3, 0x48);

        machine.board().machineState().setTopRamBankIndex(1);
        machine.board().memory().applyState();
        machine.reset();

        assertEquals(2, machine.board().machineState().topRamBankIndex());
        assertRamPattern(machine.board().memory(), 3, 0x48);
    }

    @Test
    void warmResetPreservesAllEight128kBanksFromEveryPagedTopBank() {
        Spectrum128Machine machine = blankSpectrum128();

        for (int topBank = 0; topBank < 8; topBank++) {
            int seed = 0x80 + topBank;
            fillRam(machine.board().memory(), 8, seed);

            int pagingValue = topBank | 0x08 | 0x10 | 0x20;
            machine.board().cpuBus().writePort(0x7FFD, pagingValue);
            assertEquals(topBank, machine.board().machineState().topRamBankIndex());
            assertEquals(7, machine.board().machineState().activeScreenBankIndex());
            assertEquals(1, machine.board().machineState().selectedRomIndex());
            assertEquals(pagingValue, machine.board().machineState().pagingPort7ffd());
            assertTrue(machine.board().machineState().pagingLocked());

            machine.reset();

            assertEquals(0, machine.board().machineState().topRamBankIndex());
            assertEquals(5, machine.board().machineState().activeScreenBankIndex());
            assertEquals(0, machine.board().machineState().selectedRomIndex());
            assertEquals(0, machine.board().machineState().pagingPort7ffd());
            assertFalse(machine.board().machineState().pagingLocked());
            assertRamPattern(machine.board().memory(), 8, seed);
        }
    }

    private static Spectrum128Machine blankSpectrum128() {
        return new Spectrum128Machine(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE]
        );
    }

    private static void fillRam(Spectrum48kMemoryMap memory, int bankCount, int seed) {
        for (int bank = 0; bank < bankCount; bank++) {
            for (int offset = 0; offset < Spectrum48kMemoryMap.RAM_BANK_SIZE; offset++) {
                memory.ramBank(bank).write(offset, expectedByte(seed, bank, offset));
            }
        }
    }

    private static void assertRamPattern(Spectrum48kMemoryMap memory, int bankCount, int seed) {
        for (int bank = 0; bank < bankCount; bank++) {
            for (int offset = 0; offset < Spectrum48kMemoryMap.RAM_BANK_SIZE; offset++) {
                assertEquals(
                        expectedByte(seed, bank, offset),
                        memory.ramBank(bank).read(offset),
                        "RAM bank " + bank + " offset " + offset
                );
            }
        }
    }

    private static void assertAllRamEquals(
            Spectrum48kMemoryMap memory,
            int bankCount,
            int expected
    ) {
        for (int bank = 0; bank < bankCount; bank++) {
            for (int offset = 0; offset < Spectrum48kMemoryMap.RAM_BANK_SIZE; offset++) {
                assertEquals(
                        expected,
                        memory.ramBank(bank).read(offset),
                        "RAM bank " + bank + " offset " + offset
                );
            }
        }
    }

    private static int expectedByte(int seed, int bank, int offset) {
        return (seed + (bank * 37) + offset) & 0xFF;
    }
}
