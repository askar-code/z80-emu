package dev.z8emu.machine.apple2.disk;

import dev.z8emu.cpu.mos6502.Mos6502Registers;
import dev.z8emu.machine.apple2.Apple2Machine;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Apple2ProDosBlockShimControllerTest {
    @Test
    void exposesProDosBlockDeviceSignatureInSlotRom() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.installProDosBlockShim(testBlockDevice());

        assertEquals(0x20, machine.board().cpuBus().readMemory(0xC601));
        assertEquals(0x00, machine.board().cpuBus().readMemory(0xC603));
        assertEquals(0x03, machine.board().cpuBus().readMemory(0xC605));
        assertEquals(0x40, machine.board().cpuBus().readMemory(0xC6FC));
        assertEquals(0x06, machine.board().cpuBus().readMemory(0xC6FD));
        assertEquals(0x03, machine.board().cpuBus().readMemory(0xC6FE));
        assertEquals(Apple2ProDosBlockShimController.DRIVER_ENTRY_OFFSET, machine.board().cpuBus().readMemory(0xC6FF));
    }

    @Test
    void driverStatusReturnsBlockCount() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.installProDosBlockShim(testBlockDevice());
        machine.board().cpuBus().writeMemory(0x0042, 0x00);

        callDriver(machine);

        assertEquals(0x00, machine.cpu().registers().a());
        assertEquals(0x40, machine.cpu().registers().x());
        assertEquals(0x06, machine.cpu().registers().y());
        assertFalse(machine.cpu().registers().flagSet(Mos6502Registers.FLAG_C));
    }

    @Test
    void driverReadsSelectedBlockIntoCallerBuffer() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.installProDosBlockShim(testBlockDevice());
        machine.board().cpuBus().writeMemory(0x0042, 0x01);
        machine.board().cpuBus().writeMemory(0x0043, 0x60);
        machine.board().cpuBus().writeMemory(0x0044, 0x00);
        machine.board().cpuBus().writeMemory(0x0045, 0x10);
        machine.board().cpuBus().writeMemory(0x0046, 0x05);
        machine.board().cpuBus().writeMemory(0x0047, 0x00);

        callDriver(machine);

        assertEquals(0x05, machine.board().cpuBus().readMemory(0x1000));
        assertEquals(0x06, machine.board().cpuBus().readMemory(0x1001));
        assertEquals(0x04, machine.board().cpuBus().readMemory(0x10FF));
        assertEquals(0x05, machine.board().cpuBus().readMemory(0x1100));
        assertEquals(0x00, machine.cpu().registers().a());
        assertFalse(machine.cpu().registers().flagSet(Mos6502Registers.FLAG_C));
    }

    @Test
    void driverRejectsWritesAsWriteProtected() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.installProDosBlockShim(testBlockDevice());
        machine.board().cpuBus().writeMemory(0x0042, 0x02);

        callDriver(machine);

        assertEquals(0x2B, machine.cpu().registers().a());
        assertTrue(machine.cpu().registers().flagSet(Mos6502Registers.FLAG_C));
    }

    private static void callDriver(Apple2Machine machine) {
        machine.loadProgram(new byte[]{
                0x20, 0x10, (byte) 0xC6,
                0x4C, 0x03, 0x08
        }, 0x0800);
        machine.setProgramCounter(0x0800);
        for (int i = 0; i < 3_000 && machine.cpu().registers().pc() != 0x0803; i++) {
            machine.runInstruction();
        }
        assertEquals(0x0803, machine.cpu().registers().pc());
    }

    private static Apple2BlockDevice testBlockDevice() {
        return new Apple2BlockDevice() {
            @Override
            public int blockSize() {
                return Apple2ProDosBlockImage.BLOCK_SIZE;
            }

            @Override
            public int blockCount() {
                return Apple2ProDosBlockImage.BLOCK_COUNT_800K;
            }

            @Override
            public byte[] readBlock(int block) {
                byte[] bytes = new byte[Apple2ProDosBlockImage.BLOCK_SIZE];
                Arrays.fill(bytes, (byte) block);
                bytes[1] = (byte) (block + 1);
                bytes[0xFF] = (byte) (block - 1);
                return bytes;
            }
        };
    }
}
