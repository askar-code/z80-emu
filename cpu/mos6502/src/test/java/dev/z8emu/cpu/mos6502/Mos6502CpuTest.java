package dev.z8emu.cpu.mos6502;

import dev.z8emu.platform.bus.CpuBus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mos6502CpuTest {
    private static final int[] DOCUMENTED_OPCODES = {
            0x00, 0x01, 0x05, 0x06, 0x08, 0x09, 0x0A, 0x0D, 0x0E,
            0x10, 0x11, 0x15, 0x16, 0x18, 0x19, 0x1D, 0x1E,
            0x20, 0x21, 0x24, 0x25, 0x26, 0x28, 0x29, 0x2A, 0x2C, 0x2D, 0x2E,
            0x30, 0x31, 0x35, 0x36, 0x38, 0x39, 0x3D, 0x3E,
            0x40, 0x41, 0x45, 0x46, 0x48, 0x49, 0x4A, 0x4C, 0x4D, 0x4E,
            0x50, 0x51, 0x55, 0x56, 0x58, 0x59, 0x5D, 0x5E,
            0x60, 0x61, 0x65, 0x66, 0x68, 0x69, 0x6A, 0x6C, 0x6D, 0x6E,
            0x70, 0x71, 0x75, 0x76, 0x78, 0x79, 0x7D, 0x7E,
            0x81, 0x84, 0x85, 0x86, 0x88, 0x8A, 0x8C, 0x8D, 0x8E,
            0x90, 0x91, 0x94, 0x95, 0x96, 0x98, 0x99, 0x9A, 0x9D,
            0xA0, 0xA1, 0xA2, 0xA4, 0xA5, 0xA6, 0xA8, 0xA9, 0xAA, 0xAC, 0xAD, 0xAE,
            0xB0, 0xB1, 0xB4, 0xB5, 0xB6, 0xB8, 0xB9, 0xBA, 0xBC, 0xBD, 0xBE,
            0xC0, 0xC1, 0xC4, 0xC5, 0xC6, 0xC8, 0xC9, 0xCA, 0xCC, 0xCD, 0xCE,
            0xD0, 0xD1, 0xD5, 0xD6, 0xD8, 0xD9, 0xDD, 0xDE,
            0xE0, 0xE1, 0xE4, 0xE5, 0xE6, 0xE8, 0xE9, 0xEA, 0xEC, 0xED, 0xEE,
            0xF0, 0xF1, 0xF5, 0xF6, 0xF8, 0xF9, 0xFD, 0xFE
    };

    @Test
    void resetLoadsProgramCounterFromResetVector() {
        TestBus bus = new TestBus();
        bus.writeVector(0xFFFC, 0x1234);

        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(0x1234, cpu.registers().pc());
        assertEquals(0xFD, cpu.registers().sp());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_I));
    }

    @Test
    void loadTransferAndIncrementUpdateZeroAndNegativeFlags() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xA9, 0x00, 0xAA, 0xE8, 0xA2, 0x80, 0xCA, 0xA0, 0x00, 0xA0, 0x81, 0x98, 0xA9, 0x40, 0xA8);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x00, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x00, cpu.registers().x());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x01, cpu.registers().x());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x80, cpu.registers().x());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x7F, cpu.registers().x());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x00, cpu.registers().y());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x81, cpu.registers().y());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x81, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x40, cpu.registers().y());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void storesAccumulatorThroughZeroPageAndAbsoluteAddressing() {
        TestBus bus = new TestBus();
        bus.load(0x0800, 0xA9, 0x42, 0x85, 0x10, 0x8D, 0x00, 0x04);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(2, cpu.runInstruction());
        assertEquals(3, cpu.runInstruction());
        assertEquals(0x42, bus.readMemory(0x0010));

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x42, bus.readMemory(0x0400));
    }

    @Test
    void storesYThroughZeroPageAddressing() {
        TestBus bus = new TestBus();
        bus.load(0x0800, 0xA0, 0x7E, 0x84, 0x20);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(2, cpu.runInstruction());
        assertEquals(3, cpu.runInstruction());

        assertEquals(0x7E, bus.readMemory(0x0020));
    }

    @Test
    void loadsAccumulatorThroughZeroPageAddressing() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0040, 0x80);
        bus.writeMemory(0x0041, 0x7F);
        bus.load(0x0800, 0xA5, 0x40, 0xA6, 0x41);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(3, cpu.runInstruction());

        assertEquals(0x80, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(3, cpu.runInstruction());
        assertEquals(0x7F, cpu.registers().x());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void indexedZeroPageAddressingWrapsForLoadAndStores() {
        TestBus bus = new TestBus();
        bus.load(0x0800, 0xA2, 0x02, 0xA0, 0x77, 0x94, 0xFF, 0xA9, 0x88, 0x95, 0xFE, 0xB5, 0xFF, 0xB4, 0xFF);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(4, cpu.runInstruction());
        assertEquals(0x77, bus.readMemory(0x0001));

        cpu.runInstruction();
        assertEquals(4, cpu.runInstruction());
        assertEquals(0x88, bus.readMemory(0x0000));

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x77, cpu.registers().a());

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x77, cpu.registers().y());
    }

    @Test
    void indirectYAddressingUsesZeroPagePointerPlusY() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0020, 0x00);
        bus.writeMemory(0x0021, 0x40);
        bus.load(0x0800, 0xA0, 0x05, 0xA9, 0x66, 0x91, 0x20, 0xA9, 0x00, 0xB1, 0x20, 0xD1, 0x20);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(6, cpu.runInstruction());
        assertEquals(0x66, bus.readMemory(0x4005));

        cpu.runInstruction();
        assertEquals(5, cpu.runInstruction());
        assertEquals(0x66, cpu.registers().a());

        assertEquals(5, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
    }

    @Test
    void indexedIndirectAddressingUsesZeroPagePointerPlusX() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0024, 0x00);
        bus.writeMemory(0x0025, 0x40);
        bus.writeMemory(0x4000, 0x33);
        bus.load(0x0800, 0xA2, 0x04, 0xA1, 0x20, 0x69, 0x01, 0x81, 0x20, 0xC1, 0x20);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x33, cpu.registers().a());

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x34, cpu.registers().a());

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x34, bus.readMemory(0x4000));

        assertEquals(6, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
    }

    @Test
    void indirectYLoadAndCompareAddCycleOnPageCross() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0020, 0xFF);
        bus.writeMemory(0x0021, 0x40);
        bus.writeMemory(0x4100, 0x66);
        bus.load(0x0800, 0xA0, 0x01, 0xA9, 0x66, 0xB1, 0x20, 0xD1, 0x20);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(6, cpu.runInstruction());
        assertEquals(0x66, cpu.registers().a());

        assertEquals(6, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
    }

    @Test
    void eorIndirectYAndCompareAbsoluteXSupportAppleRomGraphicsRoutines() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0030, 0xFF);
        bus.writeMemory(0x0031, 0x40);
        bus.writeMemory(0x4100, 0xFF);
        bus.load(0x2000, 0xA0, 0x01, 0xA9, 0x00, 0x51, 0x30, 0xA2, 0x01, 0xDD, 0xFF, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();

        assertEquals(6, cpu.runInstruction());
        assertEquals(0xFF, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        cpu.runInstruction();
        assertEquals(5, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
    }

    @Test
    void absoluteYAddressingAddsIndexForLoadAndStore() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x4005, 0x77);
        bus.load(0x0800, 0xA0, 0x05, 0xB9, 0x00, 0x40, 0x99, 0x00, 0x41);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(4, cpu.runInstruction());
        assertEquals(0x77, cpu.registers().a());

        assertEquals(5, cpu.runInstruction());
        assertEquals(0x77, bus.readMemory(0x4105));
    }

    @Test
    void absoluteXAddressingAddsIndexForLoadAndStore() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x4005, 0x77);
        bus.load(0x0800, 0xA2, 0x05, 0xBD, 0x00, 0x40, 0x9D, 0x00, 0x41);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(4, cpu.runInstruction());
        assertEquals(0x77, cpu.registers().a());

        assertEquals(5, cpu.runInstruction());
        assertEquals(0x77, bus.readMemory(0x4105));
    }

    @Test
    void absoluteYLoadAddsCycleOnPageCross() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x4100, 0x44);
        bus.load(0x0800, 0xA0, 0x01, 0xB9, 0xFF, 0x40, 0xBE, 0xFF, 0x40);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(5, cpu.runInstruction());

        assertEquals(0x44, cpu.registers().a());

        assertEquals(5, cpu.runInstruction());
        assertEquals(0x44, cpu.registers().x());
    }

    @Test
    void absoluteXLoadAddsCycleOnPageCross() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x4100, 0x44);
        bus.load(0x0800, 0xA2, 0x01, 0xBD, 0xFF, 0x40);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(5, cpu.runInstruction());

        assertEquals(0x44, cpu.registers().a());
    }

    @Test
    void storesXZeroPageAndYAbsolute() {
        TestBus bus = new TestBus();
        bus.load(0x0800, 0xA2, 0x12, 0x86, 0x44, 0xA0, 0x34, 0x8C, 0x00, 0x40, 0x8E, 0x01, 0x40);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(3, cpu.runInstruction());
        assertEquals(0x12, bus.readMemory(0x0044));

        cpu.runInstruction();
        assertEquals(4, cpu.runInstruction());
        assertEquals(0x34, bus.readMemory(0x4000));

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x12, bus.readMemory(0x4001));
    }

    @Test
    void transferXToStackPointerDoesNotUpdateFlags() {
        TestBus bus = new TestBus();
        bus.load(0x0800, 0xA2, 0x80, 0x18, 0x9A, 0x8A, 0xA2, 0x00, 0xBA);
        bus.writeVector(0xFFFC, 0x0800);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());

        assertEquals(0x80, cpu.registers().sp());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x80, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x80, cpu.registers().x());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void branchNotEqualUsesRelativeOffsetWhenZeroFlagIsClear() {
        TestBus bus = new TestBus();
        bus.load(0x20F0, 0xD0, 0x02, 0xA9, 0x11, 0xA9, 0x22);
        bus.writeVector(0xFFFC, 0x20F0);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.registers().setFlag(Mos6502Registers.FLAG_Z, false);
        assertEquals(3, cpu.runInstruction());
        assertEquals(0x20F4, cpu.registers().pc());

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x22, cpu.registers().a());
    }

    @Test
    void branchNotEqualFallsThroughWhenZeroFlagIsSet() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xD0, 0x02, 0xA9, 0x11);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.registers().setFlag(Mos6502Registers.FLAG_Z, true);
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x2002, cpu.registers().pc());

        cpu.runInstruction();
        assertEquals(0x11, cpu.registers().a());
    }

    @Test
    void branchEqualUsesRelativeOffsetWhenZeroFlagIsSet() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xF0, 0x02, 0xA9, 0x11, 0xA9, 0x33);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.registers().setFlag(Mos6502Registers.FLAG_Z, true);
        assertEquals(3, cpu.runInstruction());
        assertEquals(0x2004, cpu.registers().pc());

        cpu.runInstruction();
        assertEquals(0x33, cpu.registers().a());
    }

    @Test
    void branchOnCarryAndSignFlagsUseRelativeOffsets() {
        TestBus bus = new TestBus();
        bus.load(0x3000, 0x90, 0x02, 0xA9, 0x11, 0xB0, 0x02, 0xA9, 0x22, 0x10, 0x02, 0xA9, 0x33);
        bus.writeVector(0xFFFC, 0x3000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.registers().setFlag(Mos6502Registers.FLAG_C, false);
        assertEquals(3, cpu.runInstruction());
        assertEquals(0x3004, cpu.registers().pc());

        cpu.registers().setFlag(Mos6502Registers.FLAG_C, true);
        assertEquals(3, cpu.runInstruction());
        assertEquals(0x3008, cpu.registers().pc());

        cpu.registers().setFlag(Mos6502Registers.FLAG_N, false);
        assertEquals(3, cpu.runInstruction());
        assertEquals(0x300C, cpu.registers().pc());
    }

    @Test
    void branchTakenAcrossPageBoundaryAddsACycle() {
        TestBus bus = new TestBus();
        bus.load(0x20FD, 0xD0, 0x01);
        bus.writeVector(0xFFFC, 0x20FD);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.registers().setFlag(Mos6502Registers.FLAG_Z, false);

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x2100, cpu.registers().pc());
    }

    @Test
    void jsrAndRtsUse6502StackConvention() {
        TestBus bus = new TestBus();
        bus.load(0x3000, 0x20, 0x00, 0x40, 0xA9, 0x22);
        bus.load(0x4000, 0xA9, 0x11, 0x60);
        bus.writeVector(0xFFFC, 0x3000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x4000, cpu.registers().pc());
        assertEquals(0xFB, cpu.registers().sp());
        assertEquals(0x30, bus.readMemory(0x01FD));
        assertEquals(0x02, bus.readMemory(0x01FC));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x11, cpu.registers().a());

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x3003, cpu.registers().pc());
        assertEquals(0xFD, cpu.registers().sp());

        cpu.runInstruction();
        assertEquals(0x22, cpu.registers().a());
    }

    @Test
    void jumpIndirectUsesOriginal6502PageWrapBehavior() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0x6C, 0xFF, 0x12);
        bus.writeMemory(0x12FF, 0x34);
        bus.writeMemory(0x1200, 0x56);
        bus.writeMemory(0x1300, 0x78);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(5, cpu.runInstruction());

        assertEquals(0x5634, cpu.registers().pc());
    }

    @Test
    void adcImmediateUpdatesCarryOverflowAndSignFlags() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xA9, 0x50, 0x18, 0x69, 0x50, 0x38, 0x69, 0x70);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0xA0, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_V));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x11, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_V));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void adcZeroPageUpdatesCarryOverflowAndSignFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0044, 0x50);
        bus.writeMemory(0x4100, 0x01);
        bus.load(0x2000, 0xA9, 0x50, 0x65, 0x44, 0xA0, 0x01, 0x79, 0xFF, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(3, cpu.runInstruction());

        assertEquals(0xA0, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_V));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        cpu.runInstruction();
        assertEquals(5, cpu.runInstruction());
        assertEquals(0xA1, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
    }

    @Test
    void sbcImmediateUpdatesCarryOverflowAndSignFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0040, 0x01);
        bus.load(0x2000, 0xA9, 0x40, 0x38, 0xE9, 0x10, 0xE9, 0x50, 0x38, 0xE5, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x30, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_V));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0xE0, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_V));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        cpu.runInstruction();
        assertEquals(3, cpu.runInstruction());
        assertEquals(0xDF, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void sbcIndirectYReadsPointerAndAddsPageCrossCycle() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0020, 0xFF);
        bus.writeMemory(0x0021, 0x40);
        bus.writeMemory(0x4100, 0x10);
        bus.load(0x2000, 0xA9, 0x40, 0xA0, 0x01, 0x38, 0xF1, 0x20);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(6, cpu.runInstruction());

        assertEquals(0x30, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void compareAccumulatorZeroPageUpdatesCarryZeroAndNegativeFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0044, 0x20);
        bus.writeMemory(0x0045, 0x40);
        bus.load(0x2000, 0xA9, 0x20, 0xC5, 0x44, 0xC5, 0x45);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(3, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(3, cpu.runInstruction());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void compareYZeroPageUpdatesCarryZeroAndNegativeFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0044, 0x20);
        bus.writeMemory(0x0045, 0x40);
        bus.load(0x2000, 0xA0, 0x20, 0xC0, 0x20, 0xC4, 0x44, 0xC4, 0x45);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(3, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(3, cpu.runInstruction());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void compareXImmediateUpdatesCarryZeroAndNegativeFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0044, 0x20);
        bus.load(0x2000, 0xA2, 0x20, 0xE0, 0x20, 0xE4, 0x44, 0xE0, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(3, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void compareAccumulatorImmediateUpdatesCarryZeroAndNegativeFlags() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xA9, 0x20, 0xC9, 0x20, 0xC9, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void compareAccumulatorAbsoluteUpdatesCarryZeroAndNegativeFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x3456, 0x20);
        bus.writeMemory(0x3457, 0x40);
        bus.load(0x2000, 0xA9, 0x20, 0xCD, 0x56, 0x34, 0xCD, 0x57, 0x34);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(4, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(4, cpu.runInstruction());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void compareAccumulatorAbsoluteYAddsIndexAndPageCrossCycle() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x4100, 0x20);
        bus.load(0x2000, 0xA9, 0x20, 0xA0, 0x01, 0xD9, 0xFF, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(5, cpu.runInstruction());

        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void bitAbsoluteUpdatesZeroOverflowAndNegativeFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x3456, 0xC0);
        bus.writeMemory(0x0040, 0x41);
        bus.load(0x2000, 0xA9, 0x3F, 0x2C, 0x56, 0x34, 0x24, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(4, cpu.runInstruction());

        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_V));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(3, cpu.runInstruction());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_V));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void decrementZeroPageStoresResultAndUpdatesFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0050, 0x01);
        bus.writeMemory(0x0051, 0x00);
        bus.load(0x2000, 0xC6, 0x50, 0xC6, 0x51);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(5, cpu.runInstruction());
        assertEquals(0x00, bus.readMemory(0x0050));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(5, cpu.runInstruction());
        assertEquals(0xFF, bus.readMemory(0x0051));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void incrementZeroPageStoresResultAndUpdatesFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0050, 0xFF);
        bus.writeMemory(0x0051, 0x7F);
        bus.writeMemory(0x0002, 0x01);
        bus.load(0x2000, 0xE6, 0x50, 0xE6, 0x51, 0xA2, 0x03, 0xF6, 0xFF);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(5, cpu.runInstruction());
        assertEquals(0x00, bus.readMemory(0x0050));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(5, cpu.runInstruction());
        assertEquals(0x80, bus.readMemory(0x0051));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        cpu.runInstruction();
        assertEquals(6, cpu.runInstruction());
        assertEquals(0x02, bus.readMemory(0x0002));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void incrementYWrapsAndUpdatesFlags() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xA0, 0xFF, 0xC8, 0xC8, 0x88, 0x88);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x00, cpu.registers().y());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x01, cpu.registers().y());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x00, cpu.registers().y());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0xFF, cpu.registers().y());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void exclusiveOrImmediateUpdatesAccumulatorAndFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0040, 0x80);
        bus.load(0x2000, 0xA9, 0x55, 0x49, 0xFF, 0x49, 0xAA, 0x45, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0xAA, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x00, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(3, cpu.runInstruction());
        assertEquals(0x80, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void andAndOrImmediateUpdateAccumulatorAndFlags() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xA9, 0xF0, 0x29, 0x0F, 0x09, 0x80);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x00, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x80, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void andZeroPageUpdatesAccumulatorAndFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0040, 0x0F);
        bus.load(0x2000, 0xA9, 0xF0, 0x25, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(3, cpu.runInstruction());

        assertEquals(0x00, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
    }

    @Test
    void loadYZeroPageUpdatesZeroAndNegativeFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0040, 0x80);
        bus.writeMemory(0x4000, 0x7F);
        bus.load(0x2000, 0xA4, 0x40, 0xAC, 0x00, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(3, cpu.runInstruction());

        assertEquals(0x80, cpu.registers().y());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x7F, cpu.registers().y());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void orZeroPageUpdatesAccumulatorAndFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0040, 0x80);
        bus.load(0x2000, 0xA9, 0x01, 0x05, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(3, cpu.runInstruction());

        assertEquals(0x81, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
    }

    @Test
    void accumulatorStackOperationsUse6502StackPage() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xA9, 0x81, 0x48, 0xA9, 0x00, 0x68, 0x18, 0x08, 0x38, 0x28);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(3, cpu.runInstruction());
        assertEquals(0x81, bus.readMemory(0x01FD));
        assertEquals(0xFC, cpu.registers().sp());

        cpu.runInstruction();
        assertEquals(0x00, cpu.registers().a());

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x81, cpu.registers().a());
        assertEquals(0xFD, cpu.registers().sp());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        cpu.runInstruction();
        assertEquals(3, cpu.runInstruction());
        assertEquals(0xFC, cpu.registers().sp());
        assertFalse((bus.readMemory(0x01FD) & Mos6502Registers.FLAG_C) != 0);
        assertTrue((bus.readMemory(0x01FD) & Mos6502Registers.FLAG_B) != 0);

        cpu.runInstruction();
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));

        assertEquals(4, cpu.runInstruction());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertEquals(0xFD, cpu.registers().sp());
    }

    @Test
    void shiftRightAccumulatorMovesBitZeroIntoCarry() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xA9, 0x03, 0x4A, 0x4A);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x01, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x00, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
    }

    @Test
    void shiftRightZeroPageStoresResultAndMovesBitZeroIntoCarry() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0040, 0x03);
        bus.writeMemory(0x0001, 0x02);
        bus.load(0x2000, 0x46, 0x40, 0x46, 0x40, 0xA2, 0x02, 0x56, 0xFF);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(5, cpu.runInstruction());
        assertEquals(0x01, bus.readMemory(0x0040));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(5, cpu.runInstruction());
        assertEquals(0x00, bus.readMemory(0x0040));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        cpu.runInstruction();
        assertEquals(6, cpu.runInstruction());
        assertEquals(0x01, bus.readMemory(0x0001));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
    }

    @Test
    void shiftLeftAccumulatorMovesBitSevenIntoCarry() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0040, 0x80);
        bus.writeMemory(0x0002, 0x40);
        bus.load(0x2000, 0xA9, 0x81, 0x0A, 0x0A, 0x06, 0x40, 0xA2, 0x03, 0x16, 0xFF);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x02, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x04, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));

        assertEquals(5, cpu.runInstruction());
        assertEquals(0x00, bus.readMemory(0x0040));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        cpu.runInstruction();
        assertEquals(6, cpu.runInstruction());
        assertEquals(0x80, bus.readMemory(0x0002));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void rotateLeftAccumulatorMovesCarryThroughBitZero() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0040, 0x80);
        bus.load(0x2000, 0xA9, 0x80, 0x38, 0x2A, 0x2A, 0x38, 0x26, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x01, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(2, cpu.runInstruction());
        assertEquals(0x03, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));

        cpu.runInstruction();
        assertEquals(5, cpu.runInstruction());
        assertEquals(0x01, bus.readMemory(0x0040));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
    }

    @Test
    void rotateRightAccumulatorAndZeroPageMoveCarryThroughBitSeven() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x0040, 0x01);
        bus.writeMemory(0x0002, 0x01);
        bus.load(0x2000, 0xA9, 0x01, 0x38, 0x6A, 0x18, 0x66, 0x40, 0x18, 0xA2, 0x03, 0x76, 0xFF);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x80, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        cpu.runInstruction();
        assertEquals(5, cpu.runInstruction());
        assertEquals(0x00, bus.readMemory(0x0040));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(6, cpu.runInstruction());
        assertEquals(0x00, bus.readMemory(0x0002));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));
    }

    @Test
    void absoluteReadModifyWriteOpcodesStoreResultsAndUpdateFlags() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x4000, 0x40);
        bus.writeMemory(0x4001, 0x01);
        bus.writeMemory(0x4002, 0x80);
        bus.writeMemory(0x4003, 0x10);
        bus.load(0x2000,
                0x0E, 0x00, 0x40,
                0x4E, 0x01, 0x40,
                0x2E, 0x02, 0x40,
                0x6E, 0x03, 0x40,
                0xCE, 0x00, 0x40,
                0xEE, 0x00, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x80, bus.readMemory(0x4000));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x00, bus.readMemory(0x4001));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x01, bus.readMemory(0x4002));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x88, bus.readMemory(0x4003));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x7F, bus.readMemory(0x4000));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_N));

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x80, bus.readMemory(0x4000));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_N));
    }

    @Test
    void indexedReadModifyWriteOpcodesUseAbsoluteXAndZeroPageX() {
        TestBus bus = new TestBus();
        bus.writeMemory(0x4102, 0x10);
        bus.writeMemory(0x0002, 0x02);
        bus.load(0x2000,
                0xA2, 0x02,
                0x1E, 0x00, 0x41,
                0x3E, 0x00, 0x41,
                0x5E, 0x00, 0x41,
                0x7E, 0x00, 0x41,
                0xDE, 0x00, 0x41,
                0xFE, 0x00, 0x41,
                0xD6, 0x00);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.runInstruction();

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x20, bus.readMemory(0x4102));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x40, bus.readMemory(0x4102));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x20, bus.readMemory(0x4102));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x10, bus.readMemory(0x4102));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x0F, bus.readMemory(0x4102));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x10, bus.readMemory(0x4102));

        assertEquals(6, cpu.runInstruction());
        assertEquals(0x01, bus.readMemory(0x0002));
    }

    @Test
    void brkPushesReturnAddressAndStatusThenJumpsThroughIrqVector() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0x00);
        bus.writeVector(0xFFFC, 0x2000);
        bus.writeVector(0xFFFE, 0x4444);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(7, cpu.runInstruction());

        assertEquals(0x4444, cpu.registers().pc());
        assertEquals(0xFA, cpu.registers().sp());
        assertEquals(0x20, bus.readMemory(0x01FD));
        assertEquals(0x02, bus.readMemory(0x01FC));
        assertTrue((bus.readMemory(0x01FB) & Mos6502Registers.FLAG_B) != 0);
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_I));
    }

    @Test
    void rtiRestoresStatusAndProgramCounterFromStack() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0x40);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);
        cpu.registers().setSp(0xFA);
        bus.writeMemory(0x01FB, Mos6502Registers.FLAG_C);
        bus.writeMemory(0x01FC, 0x34);
        bus.writeMemory(0x01FD, 0x12);

        assertEquals(6, cpu.runInstruction());

        assertEquals(0x1234, cpu.registers().pc());
        assertEquals(0xFD, cpu.registers().sp());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_I));
    }

    @Test
    void maskableAndNonMaskableInterruptsUseTheirOwnVectors() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xEA, 0xEA);
        bus.writeVector(0xFFFC, 0x2000);
        bus.writeVector(0xFFFE, 0x3333);
        bus.writeVector(0xFFFA, 0x2222);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        cpu.registers().setFlag(Mos6502Registers.FLAG_I, false);
        cpu.requestMaskableInterrupt();
        assertEquals(7, cpu.runInstruction());
        assertEquals(0x3333, cpu.registers().pc());
        assertFalse((bus.readMemory(0x01FB) & Mos6502Registers.FLAG_B) != 0);

        cpu.reset();
        cpu.requestNonMaskableInterrupt();
        assertEquals(7, cpu.runInstruction());
        assertEquals(0x2222, cpu.registers().pc());
    }

    @Test
    void decimalModeAdjustsAdcAndSbcResults() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0xF8, 0xA9, 0x45, 0x69, 0x55, 0x38, 0xA9, 0x50, 0xE9, 0x01, 0xD8);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        assertEquals(2, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_D));

        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x00, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        cpu.runInstruction();
        cpu.runInstruction();
        assertEquals(2, cpu.runInstruction());
        assertEquals(0x49, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Mos6502Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_Z));

        assertEquals(2, cpu.runInstruction());
        assertFalse(cpu.registers().flagSet(Mos6502Registers.FLAG_D));
    }

    @Test
    void documentedOpcodesAreAllExecutableEntryPoints() {
        for (int opcode : DOCUMENTED_OPCODES) {
            TestBus bus = singleInstructionBus(opcode);
            Mos6502Cpu cpu = new Mos6502Cpu(bus);
            primeStackForSingleInstruction(opcode, bus, cpu);

            assertDoesNotThrow(cpu::runInstruction, "opcode 0x%02X".formatted(opcode));
        }
    }

    @Test
    void illegalOpcodeRestoresProgramCounterForDebugging() {
        TestBus bus = new TestBus();
        bus.load(0x2000, 0x02);
        bus.writeVector(0xFFFC, 0x2000);
        Mos6502Cpu cpu = new Mos6502Cpu(bus);

        IllegalStateException failure = assertThrows(IllegalStateException.class, cpu::runInstruction);

        assertEquals("Illegal MOS 6502 opcode 0x02 at 0x2000", failure.getMessage());
        assertEquals(0x2000, cpu.registers().pc());
    }

    private static TestBus singleInstructionBus(int opcode) {
        TestBus bus = new TestBus();
        bus.load(0x2000, opcode, 0x44, 0x20);
        bus.writeVector(0xFFFC, 0x2000);
        bus.writeVector(0xFFFA, 0x3000);
        bus.writeVector(0xFFFE, 0x3000);
        bus.writeMemory(0x0044, 0x00);
        bus.writeMemory(0x0045, 0x40);
        bus.writeMemory(0x2044, 0x00);
        bus.writeMemory(0x2045, 0x40);
        return bus;
    }

    private static void primeStackForSingleInstruction(int opcode, TestBus bus, Mos6502Cpu cpu) {
        if (opcode == 0x40) {
            cpu.registers().setSp(0xFA);
            bus.writeMemory(0x01FB, Mos6502Registers.FLAG_C);
            bus.writeMemory(0x01FC, 0x34);
            bus.writeMemory(0x01FD, 0x12);
        } else if (opcode == 0x60) {
            cpu.registers().setSp(0xFB);
            bus.writeMemory(0x01FC, 0x33);
            bus.writeMemory(0x01FD, 0x12);
        }
    }

    private static final class TestBus implements CpuBus {
        private final byte[] memory = new byte[0x10000];

        void load(int startAddress, int... values) {
            for (int i = 0; i < values.length; i++) {
                writeMemory(startAddress + i, values[i]);
            }
        }

        void writeVector(int address, int target) {
            writeMemory(address, target & 0xFF);
            writeMemory(address + 1, target >>> 8);
        }

        @Override
        public int readMemory(int address) {
            return Byte.toUnsignedInt(memory[address & 0xFFFF]);
        }

        @Override
        public void writeMemory(int address, int value) {
            memory[address & 0xFFFF] = (byte) value;
        }
    }
}
