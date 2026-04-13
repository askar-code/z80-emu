package dev.z8emu.cpu.z80;

import dev.z8emu.platform.bus.CpuBus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Z80CpuTest {
    @Test
    void executesImmediateLoadsAndMemoryRoundTrip() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x21, 0x00, 0x40,
                0x36, 0x42,
                0x7E,
                0x06, 0x17,
                0x78
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x4000, cpu.registers().hl());

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x42, bus.read(0x4000));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x42, cpu.registers().a());

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x17, cpu.registers().b());

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x17, cpu.registers().a());
        assertEquals(9, cpu.registers().pc());
    }

    @Test
    void incAndDecUpdateFlagsAndPreserveCarry() {
        TestBus bus = new TestBus();
        bus.load(0x0000, 0x04, 0x05);

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setB(0x7F);
        cpu.registers().setF(Z80Registers.FLAG_C);

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x80, cpu.registers().b());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_S));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_H));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_Z));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_N));

        cpu.registers().setB(0x80);
        cpu.registers().setF(Z80Registers.FLAG_C);
        cpu.registers().setPc(0x0001);

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x7F, cpu.registers().b());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_H));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_N));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_S));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_Z));
    }

    @Test
    void djnzAndJrFollowRelativeOffsets() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x06, 0x02,
                0x10, 0x02,
                0x3E, 0x11,
                0x10, 0x00,
                0x18, 0x02,
                0x3E, 0x22,
                0x3E, 0x33
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(7, cpu.runInstruction());
        assertEquals(13, cpu.runInstruction());
        assertEquals(8, cpu.runInstruction());
        assertEquals(12, cpu.runInstruction());
        assertEquals(7, cpu.runInstruction());

        assertEquals(0x33, cpu.registers().a());
        assertEquals(0x00, cpu.registers().b());
        assertEquals(14, cpu.registers().pc());
    }

    @Test
    void eiDelaysMaskableInterruptByOneInstructionAndPushesReturnAddress() {
        TestBus bus = new TestBus();
        bus.load(0x0000, 0xFB, 0x00, 0x00);

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setInterruptMode(1);

        assertEquals(4, cpu.runInstruction());
        assertTrue(cpu.registers().iff1());
        cpu.requestMaskableInterrupt();

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x0002, cpu.registers().pc());

        assertEquals(13, cpu.runInstruction());
        assertEquals(0x0038, cpu.registers().pc());
        assertEquals(0xFFFD, cpu.registers().sp());
        assertEquals(0x02, bus.read(0xFFFD));
        assertEquals(0x00, bus.read(0xFFFE));
        assertFalse(cpu.registers().iff1());
    }

    @Test
    void haltRepeatsRefreshCyclesUntilNmiArrives() {
        TestBus bus = new TestBus();
        bus.load(0x0000, 0x76, 0x00);

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(4, cpu.runInstruction());
        assertTrue(cpu.isHalted());
        assertEquals(0x0001, cpu.registers().pc());
        assertEquals(1, cpu.registers().r());

        assertEquals(4, cpu.runInstruction());
        assertTrue(cpu.isHalted());
        assertEquals(2, cpu.registers().r());

        cpu.requestNonMaskableInterrupt();

        assertEquals(11, cpu.runInstruction());
        assertFalse(cpu.isHalted());
        assertEquals(0x0066, cpu.registers().pc());
        assertEquals(0xFFFD, cpu.registers().sp());
        assertEquals(0x01, bus.read(0xFFFD));
        assertEquals(0x00, bus.read(0xFFFE));
        assertFalse(cpu.registers().iff1());
        assertFalse(cpu.registers().iff2());
    }

    @Test
    void pushPopCallRetAndRstUseStackCorrectly() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x31, 0x00, 0x90,
                0x01, 0x34, 0x12,
                0xC5,
                0xD1,
                0xCD, 0x0D, 0x00,
                0xFF,
                0x00,
                0x3E, 0x55,
                0xC9
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x9000, cpu.registers().sp());

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x1234, cpu.registers().bc());

        assertEquals(11, cpu.runInstruction());
        assertEquals(0x8FFE, cpu.registers().sp());
        assertEquals(0x34, bus.read(0x8FFE));
        assertEquals(0x12, bus.read(0x8FFF));

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x1234, cpu.registers().de());
        assertEquals(0x9000, cpu.registers().sp());

        assertEquals(17, cpu.runInstruction());
        assertEquals(0x000D, cpu.registers().pc());
        assertEquals(0x8FFE, cpu.registers().sp());
        assertEquals(0x0B, bus.read(0x8FFE));
        assertEquals(0x00, bus.read(0x8FFF));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x55, cpu.registers().a());

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x000B, cpu.registers().pc());
        assertEquals(0x9000, cpu.registers().sp());

        assertEquals(11, cpu.runInstruction());
        assertEquals(0x0038, cpu.registers().pc());
        assertEquals(0x8FFE, cpu.registers().sp());
        assertEquals(0x0C, bus.read(0x8FFE));
        assertEquals(0x00, bus.read(0x8FFF));
    }

    @Test
    void aluGroupsUpdateAccumulatorAndFlags() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x3E, 0x14,
                0x06, 0x22,
                0x80,
                0xD6, 0x10,
                0xE6, 0x0F,
                0xF6, 0x80,
                0xEE, 0x8E,
                0xFE, 0x01
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(7, cpu.runInstruction());
        assertEquals(7, cpu.runInstruction());

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x36, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_N));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x26, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_N));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x06, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_H));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_N));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x86, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_S));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x08, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_H));

        assertEquals(7, cpu.runInstruction());
        assertEquals(0x08, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_N));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_C));
    }

    @Test
    void exchangeInstructionsSwapMainAndAlternateRegisters() {
        TestBus bus = new TestBus();
        bus.load(0x0000, 0x08, 0xD9, 0xEB);

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setAf(0x1234);
        cpu.registers().setAfAlt(0x5678);
        cpu.registers().setBc(0x1111);
        cpu.registers().setDe(0x2222);
        cpu.registers().setHl(0x3333);
        cpu.registers().setBcAlt(0xAAAA);
        cpu.registers().setDeAlt(0xBBBB);
        cpu.registers().setHlAlt(0xCCCC);

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x5678, cpu.registers().af());
        assertEquals(0x1234, cpu.registers().afAlt());

        assertEquals(4, cpu.runInstruction());
        assertEquals(0xAAAA, cpu.registers().bc());
        assertEquals(0xBBBB, cpu.registers().de());
        assertEquals(0xCCCC, cpu.registers().hl());
        assertEquals(0x1111, cpu.registers().bcAlt());
        assertEquals(0x2222, cpu.registers().deAlt());
        assertEquals(0x3333, cpu.registers().hlAlt());

        assertEquals(4, cpu.runInstruction());
        assertEquals(0xCCCC, cpu.registers().de());
        assertEquals(0xBBBB, cpu.registers().hl());
    }

    @Test
    void cbPrefixHandlesRotateBitAndSetReset() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x06, 0x81,
                0xCB, 0x00,
                0xCB, 0x78,
                0x21, 0x00, 0x40,
                0x36, 0x01,
                0xCB, 0xF6,
                0xCB, 0xB6
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(7, cpu.runInstruction());
        assertEquals(8, cpu.runInstruction());
        assertEquals(0x03, cpu.registers().b());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_C));

        assertEquals(8, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_H));

        assertEquals(10, cpu.runInstruction());
        assertEquals(10, cpu.runInstruction());
        assertEquals(15, cpu.runInstruction());
        assertEquals(0x41, bus.read(0x4000));

        assertEquals(15, cpu.runInstruction());
        assertEquals(0x01, bus.read(0x4000));
    }

    @Test
    void edPrefixSupportsNegInterruptModeAndRetn() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x3E, 0x81,
                0xED, 0x47,
                0xAF,
                0xED, 0x57,
                0xED, 0x5E,
                0x3E, 0x05,
                0xED, 0x44,
                0x31, 0x00, 0x90,
                0xED, 0x45
        );

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setIff2(true);
        bus.writeMemory(0x9000, 0x34);
        bus.writeMemory(0x9001, 0x12);

        assertEquals(7, cpu.runInstruction());
        assertEquals(9, cpu.runInstruction());
        assertEquals(0x81, cpu.registers().i());

        assertEquals(4, cpu.runInstruction());
        assertEquals(9, cpu.runInstruction());
        assertEquals(0x81, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_S));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));

        assertEquals(8, cpu.runInstruction());
        assertEquals(2, cpu.registers().interruptMode());

        assertEquals(7, cpu.runInstruction());
        assertEquals(8, cpu.runInstruction());
        assertEquals(0xFB, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_N));

        assertEquals(10, cpu.runInstruction());
        assertEquals(14, cpu.runInstruction());
        assertEquals(0x1234, cpu.registers().pc());
        assertEquals(0x9002, cpu.registers().sp());
        assertTrue(cpu.registers().iff1());
    }

    @Test
    void edPrefixSupportsLdRFromAAndLdAFromR() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x3E, 0x4F,
                0xED, 0x4F,
                0xAF,
                0xED, 0x5F
        );

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setIff2(true);
        cpu.registers().setR(0x80);

        assertEquals(7, cpu.runInstruction());
        assertEquals(9, cpu.runInstruction());
        assertEquals(0xCF, cpu.registers().r());

        assertEquals(4, cpu.runInstruction());
        assertEquals(9, cpu.runInstruction());
        assertEquals(0xD2, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_S));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));
    }

    @Test
    void accumulatorControlInstructionsUpdateFlagsAsExpected() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x3E, 0x15,
                0xC6, 0x27,
                0x27,
                0x37,
                0x17,
                0x0F,
                0x2F,
                0x3F
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(7, cpu.runInstruction());
        assertEquals(7, cpu.runInstruction());
        assertEquals(0x3C, cpu.registers().a());

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x42, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_C));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_N));

        assertEquals(4, cpu.runInstruction());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_C));

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x85, cpu.registers().a());
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_C));

        assertEquals(4, cpu.runInstruction());
        assertEquals(0xC2, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_C));

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x3D, cpu.registers().a());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_H));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_N));

        assertEquals(4, cpu.runInstruction());
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_C));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_H));
    }

    @Test
    void ioInstructionsReadAndWritePorts() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x3E, 0x9A,
                0xD3, 0xFE,
                0xDB, 0xFE,
                0x01, 0x34, 0x12,
                0xED, 0x40,
                0xED, 0x79
        );
        bus.setPortValue(0x9AFE, 0x55);
        bus.setPortValue(0x1234, 0x81);

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(7, cpu.runInstruction());
        assertEquals(11, cpu.runInstruction());
        assertEquals(0x9AFE, bus.lastPortWritePort());
        assertEquals(0x9A, bus.lastPortWriteValue());

        assertEquals(11, cpu.runInstruction());
        assertEquals(0x55, cpu.registers().a());

        assertEquals(10, cpu.runInstruction());
        assertEquals(12, cpu.runInstruction());
        assertEquals(0x81, cpu.registers().b());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_S));

        assertEquals(12, cpu.runInstruction());
        assertEquals(0x8134, bus.lastPortWritePort());
        assertEquals(0x55, bus.lastPortWriteValue());
    }

    @Test
    void blockEdInstructionsTransferAndSearchAcrossMultipleIterations() {
        TestBus bus = new TestBus();
        bus.load(0x0000, 0xED, 0xB0, 0xED, 0xB1);
        bus.writeMemory(0x4000, 0x11);
        bus.writeMemory(0x4001, 0x22);
        bus.writeMemory(0x4002, 0x33);
        bus.writeMemory(0x5000, 0x00);
        bus.writeMemory(0x5001, 0x00);
        bus.writeMemory(0x5002, 0x00);
        bus.writeMemory(0x6000, 0x10);
        bus.writeMemory(0x6001, 0x22);
        bus.writeMemory(0x6002, 0x30);

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setHl(0x4000);
        cpu.registers().setDe(0x5000);
        cpu.registers().setBc(0x0003);

        assertEquals(58, cpu.runInstruction());
        assertEquals(0x11, bus.read(0x5000));
        assertEquals(0x22, bus.read(0x5001));
        assertEquals(0x33, bus.read(0x5002));
        assertEquals(0x4003, cpu.registers().hl());
        assertEquals(0x5003, cpu.registers().de());
        assertEquals(0x0000, cpu.registers().bc());
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_PV));

        cpu.registers().setA(0x22);
        cpu.registers().setHl(0x6000);
        cpu.registers().setBc(0x0003);
        cpu.registers().setPc(0x0002);

        assertEquals(37, cpu.runInstruction());
        assertEquals(0x6002, cpu.registers().hl());
        assertEquals(0x0001, cpu.registers().bc());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));
    }

    @Test
    void edSixteenBitAdcAndSbcUpdateHlAndFlags() {
        TestBus bus = new TestBus();
        bus.load(0x0000, 0xED, 0x4A, 0xED, 0x52);

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setHl(0x7FFF);
        cpu.registers().setBc(0x0001);
        cpu.registers().setF(Z80Registers.FLAG_C);

        assertEquals(15, cpu.runInstruction());
        assertEquals(0x8001, cpu.registers().hl());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_S));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_H));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_N));

        cpu.registers().setDe(0x0001);
        cpu.registers().setF(Z80Registers.FLAG_C);
        cpu.registers().setPc(0x0002);

        assertEquals(15, cpu.runInstruction());
        assertEquals(0x7FFF, cpu.registers().hl());
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_S));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_H));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_N));
    }

    @Test
    void edSixteenBitLoadStoreViaAbsoluteAddressWorks() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x01, 0x34, 0x12,
                0xED, 0x43, 0x00, 0x80,
                0x11, 0x00, 0x00,
                0xED, 0x5B, 0x00, 0x80
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x1234, cpu.registers().bc());

        assertEquals(20, cpu.runInstruction());
        assertEquals(0x34, bus.read(0x8000));
        assertEquals(0x12, bus.read(0x8001));

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x0000, cpu.registers().de());

        assertEquals(20, cpu.runInstruction());
        assertEquals(0x1234, cpu.registers().de());
    }

    @Test
    void rrdAndRldTransformAccumulatorAndMemoryNibbleWise() {
        TestBus bus = new TestBus();
        bus.load(0x0000, 0xED, 0x67, 0xED, 0x6F);
        bus.writeMemory(0x4000, 0xAB);

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setHl(0x4000);
        cpu.registers().setA(0x34);

        assertEquals(18, cpu.runInstruction());
        assertEquals(0x3B, cpu.registers().a());
        assertEquals(0x4A, bus.read(0x4000));

        assertEquals(18, cpu.runInstruction());
        assertEquals(0x34, cpu.registers().a());
        assertEquals(0xAB, bus.read(0x4000));
    }

    @Test
    void ddAndFdPrefixesReplaceHlHAndLWithIndexRegisters() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0xDD, 0x21, 0x00, 0x40,
                0xDD, 0x26, 0x12,
                0xDD, 0x2E, 0x34,
                0xDD, 0x36, 0x02, 0x56,
                0xDD, 0x7E, 0x02,
                0xFD, 0x21, 0x10, 0x40,
                0xFD, 0x26, 0xAB,
                0xFD, 0x2E, 0xCD,
                0xFD, 0x7C,
                0xFD, 0x7D,
                0xFD, 0x36, 0xFE, 0x99,
                0xFD, 0x46, 0xFE
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(14, cpu.runInstruction());
        assertEquals(0x4000, cpu.registers().ix());

        assertEquals(11, cpu.runInstruction());
        assertEquals(0x1200, cpu.registers().ix() & 0xFF00);

        assertEquals(11, cpu.runInstruction());
        assertEquals(0x1234, cpu.registers().ix());

        assertEquals(19, cpu.runInstruction());
        assertEquals(0x56, bus.read(0x1236));

        assertEquals(19, cpu.runInstruction());
        assertEquals(0x56, cpu.registers().a());

        assertEquals(14, cpu.runInstruction());
        assertEquals(0x4010, cpu.registers().iy());

        assertEquals(11, cpu.runInstruction());
        assertEquals(0xAB10, cpu.registers().iy());

        assertEquals(11, cpu.runInstruction());
        assertEquals(0xABCD, cpu.registers().iy());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0xAB, cpu.registers().a());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0xCD, cpu.registers().a());

        assertEquals(19, cpu.runInstruction());
        assertEquals(0x99, bus.read(0xABCB));

        assertEquals(19, cpu.runInstruction());
        assertEquals(0x99, cpu.registers().b());
    }

    @Test
    void loadsBetweenHandLAndIndexedMemoryUseRealHandLRegisters() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x26, 0x11,
                0x2E, 0x22,
                0xDD, 0x21, 0x00, 0x40,
                0xDD, 0x36, 0x01, 0x66,
                0xDD, 0x66, 0x01,
                0xDD, 0x75, 0x02
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(7, cpu.runInstruction());
        assertEquals(7, cpu.runInstruction());
        assertEquals(0x1122, cpu.registers().hl());

        assertEquals(14, cpu.runInstruction());
        assertEquals(0x4000, cpu.registers().ix());

        assertEquals(19, cpu.runInstruction());
        assertEquals(0x66, bus.read(0x4001));

        assertEquals(19, cpu.runInstruction());
        assertEquals(0x66, cpu.registers().h());
        assertEquals(0x4000, cpu.registers().ix());

        assertEquals(19, cpu.runInstruction());
        assertEquals(0x22, bus.read(0x4002));
    }

    @Test
    void indexedRegisterPairAndStackOperationsWork() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x01, 0x34, 0x12,
                0xDD, 0x21, 0x00, 0x20,
                0xDD, 0x09,
                0xDD, 0x23,
                0xDD, 0x2B,
                0x31, 0x00, 0x90,
                0xDD, 0xE5,
                0xFD, 0xE1,
                0xFD, 0xE3,
                0xFD, 0xF9,
                0xFD, 0xE9
        );

        Z80Cpu cpu = new Z80Cpu(bus);
        bus.writeMemory(0x9000, 0x78);
        bus.writeMemory(0x9001, 0x56);

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x1234, cpu.registers().bc());

        assertEquals(14, cpu.runInstruction());
        assertEquals(0x2000, cpu.registers().ix());

        assertEquals(15, cpu.runInstruction());
        assertEquals(0x3234, cpu.registers().ix());

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x3235, cpu.registers().ix());

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x3234, cpu.registers().ix());

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x9000, cpu.registers().sp());

        assertEquals(15, cpu.runInstruction());
        assertEquals(0x8FFE, cpu.registers().sp());
        assertEquals(0x34, bus.read(0x8FFE));
        assertEquals(0x32, bus.read(0x8FFF));

        assertEquals(14, cpu.runInstruction());
        assertEquals(0x3234, cpu.registers().iy());
        assertEquals(0x9000, cpu.registers().sp());

        assertEquals(23, cpu.runInstruction());
        assertEquals(0x5678, cpu.registers().iy());
        assertEquals(0x34, bus.read(0x9000));
        assertEquals(0x32, bus.read(0x9001));

        assertEquals(10, cpu.runInstruction());
        assertEquals(0x5678, cpu.registers().sp());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x5678, cpu.registers().pc());
    }

    @Test
    void ddcbAndFdcbPrefixesOperateOnIndexedMemory() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0xDD, 0x21, 0x00, 0x40,
                0xDD, 0x36, 0x01, 0x81,
                0xDD, 0xCB, 0x01, 0x00,
                0xDD, 0xCB, 0x01, 0x46,
                0xFD, 0x21, 0x10, 0x40,
                0xFD, 0x36, 0xFE, 0x01,
                0xFD, 0xCB, 0xFE, 0xF6,
                0xFD, 0xCB, 0xFE, 0xB6
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(14, cpu.runInstruction());
        assertEquals(19, cpu.runInstruction());

        assertEquals(23, cpu.runInstruction());
        assertEquals(0x03, cpu.registers().b());
        assertEquals(0x03, bus.read(0x4001));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_C));

        assertEquals(20, cpu.runInstruction());
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_H));

        assertEquals(14, cpu.runInstruction());
        assertEquals(19, cpu.runInstruction());

        assertEquals(23, cpu.runInstruction());
        assertEquals(0x41, bus.read(0x400E));

        assertEquals(23, cpu.runInstruction());
        assertEquals(0x01, bus.read(0x400E));
    }

    private static final class TestBus implements CpuBus {
        private final byte[] memory = new byte[0x10000];
        private final int[] portValues = new int[0x10000];
        private int interruptVector = 0xFF;
        private int lastPortWritePort = -1;
        private int lastPortWriteValue = -1;

        void load(int address, int... values) {
            for (int i = 0; i < values.length; i++) {
                memory[(address + i) & 0xFFFF] = (byte) values[i];
            }
        }

        int read(int address) {
            return Byte.toUnsignedInt(memory[address & 0xFFFF]);
        }

        void setPortValue(int port, int value) {
            portValues[port & 0xFFFF] = value & 0xFF;
        }

        int lastPortWritePort() {
            return lastPortWritePort;
        }

        int lastPortWriteValue() {
            return lastPortWriteValue;
        }

        @Override
        public int fetchOpcode(int address) {
            return read(address);
        }

        @Override
        public int readMemory(int address) {
            return read(address);
        }

        @Override
        public void writeMemory(int address, int value) {
            memory[address & 0xFFFF] = (byte) value;
        }

        @Override
        public int readPort(int port) {
            return portValues[port & 0xFFFF];
        }

        @Override
        public void writePort(int port, int value) {
            lastPortWritePort = port & 0xFFFF;
            lastPortWriteValue = value & 0xFF;
        }

        @Override
        public int acknowledgeInterrupt() {
            return interruptVector;
        }

        @Override
        public void onRefresh(int irValue) {
        }

        @Override
        public int currentTState() {
            return 0;
        }
    }
}
