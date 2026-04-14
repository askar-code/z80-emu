package dev.z8emu.machine.spectrum128k;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Spectrum128MachineTest {
    @Test
    void defaultsToRom0Bank0AndScreenBank5() {
        byte[] rom0 = new byte[Spectrum128Machine.ROM_BANK_SIZE];
        byte[] rom1 = new byte[Spectrum128Machine.ROM_BANK_SIZE];
        rom0[0] = 0x11;
        rom1[0] = 0x22;

        Spectrum128Machine machine = new Spectrum128Machine(rom0, rom1);

        assertEquals(0x11, machine.board().memory().read(0x0000));
        assertEquals(0, machine.board().machineState().selectedRomIndex());
        assertEquals(0, machine.board().machineState().topRamBankIndex());
        assertEquals(5, machine.board().machineState().activeScreenBankIndex());
    }

    @Test
    void writeTo7ffdPagesRomTopBankAndShadowScreen() {
        byte[] rom0 = new byte[Spectrum128Machine.ROM_BANK_SIZE];
        byte[] rom1 = new byte[Spectrum128Machine.ROM_BANK_SIZE];
        rom0[0] = 0x11;
        rom1[0] = 0x22;

        Spectrum128Machine machine = new Spectrum128Machine(rom0, rom1);
        machine.board().cpuBus().writePort(0x7FFD, 0x1F);

        assertEquals(0x22, machine.board().memory().read(0x0000));
        assertEquals(1, machine.board().machineState().selectedRomIndex());
        assertEquals(7, machine.board().machineState().topRamBankIndex());
        assertEquals(7, machine.board().machineState().activeScreenBankIndex());
    }

    @Test
    void pagingLockPreventsFurtherChanges() {
        Spectrum128Machine machine = new Spectrum128Machine(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE]
        );

        machine.board().cpuBus().writePort(0x7FFD, 0x20);
        machine.board().cpuBus().writePort(0x7FFD, 0x1F);

        assertEquals(0, machine.board().machineState().selectedRomIndex());
        assertEquals(0, machine.board().machineState().topRamBankIndex());
        assertEquals(5, machine.board().machineState().activeScreenBankIndex());
        assertEquals(true, machine.board().machineState().pagingLocked());
    }

    @Test
    void activeScreenBankControlsDisplayedMemory() {
        Spectrum128Machine machine = new Spectrum128Machine(
                new byte[Spectrum128Machine.ROM_BANK_SIZE],
                new byte[Spectrum128Machine.ROM_BANK_SIZE]
        );

        machine.board().memory().ramBank(5).write(0, 0xAA);
        machine.board().memory().ramBank(7).write(0, 0x55);

        assertEquals(0xAA, machine.board().memory().readDisplayMemory(0x4000));

        machine.board().cpuBus().writePort(0x7FFD, 0x08);

        assertEquals(0x55, machine.board().memory().readDisplayMemory(0x4000));
    }
}
