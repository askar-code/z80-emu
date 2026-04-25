package dev.z8emu.cpu.z80;

import dev.z8emu.platform.bus.CpuBus;
import java.util.ArrayList;
import java.util.List;
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
    void clearedMaskableInterruptIsNotServicedAfterEiDelay() {
        TestBus bus = new TestBus();
        bus.load(0x0000, 0xFB, 0x00, 0x00);

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setInterruptMode(1);

        assertEquals(4, cpu.runInstruction());
        cpu.requestMaskableInterrupt();
        cpu.clearMaskableInterrupt();

        assertEquals(4, cpu.runInstruction());
        assertEquals(0x0002, cpu.registers().pc());
        assertEquals(4, cpu.runInstruction());
        assertEquals(0x0003, cpu.registers().pc());
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
    void haltReleasesOnMaskableInterruptEvenWhenInterruptsAreDisabled() {
        TestBus bus = new TestBus();
        bus.load(0x0000, 0x76, 0x00);

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(4, cpu.runInstruction());
        assertTrue(cpu.isHalted());
        assertEquals(0x0001, cpu.registers().pc());

        cpu.requestMaskableInterrupt();

        assertEquals(4, cpu.runInstruction());
        assertFalse(cpu.isHalted());
        assertEquals(0x0001, cpu.registers().pc());
        assertFalse(cpu.registers().iff1());
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
    void repeatedBlockInstructionsAdvanceRForEachRepeat() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x3E, 0x30,
                0xED, 0x4F,
                0x21, 0x02, 0x40,
                0x11, 0x12, 0x40,
                0x01, 0x03, 0x00,
                0xED, 0xB8,
                0xAF,
                0xED, 0x5F
        );
        bus.load(0x4000, 0x11, 0x22, 0x33);

        Z80Cpu cpu = new Z80Cpu(bus);

        while (cpu.registers().pc() != 0x0010) {
            cpu.runInstruction();
        }

        assertEquals(0x3A, cpu.registers().r(), "LDDR with 3 iterations should advance R on each repeated ED fetch");
        assertEquals(9, cpu.runInstruction());
        assertEquals(0x3C, cpu.registers().a(), "LD A,R should observe the advanced refresh register");
    }

    @Test
    void ldirRepeatsOneIterationPerRunInstruction() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x21, 0x00, 0x40,
                0x11, 0x00, 0x50,
                0x01, 0x03, 0x00,
                0xED, 0xB0
        );
        bus.load(0x4000, 0x11, 0x22, 0x33);

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(10, cpu.runInstruction());
        assertEquals(10, cpu.runInstruction());
        assertEquals(10, cpu.runInstruction());

        assertEquals(21, cpu.runInstruction());
        assertEquals(0x0009, cpu.registers().pc());
        assertEquals(0x4001, cpu.registers().hl());
        assertEquals(0x5001, cpu.registers().de());
        assertEquals(0x0002, cpu.registers().bc());
        assertEquals(0x11, bus.read(0x5000));

        assertEquals(21, cpu.runInstruction());
        assertEquals(0x0009, cpu.registers().pc());
        assertEquals(0x4002, cpu.registers().hl());
        assertEquals(0x5002, cpu.registers().de());
        assertEquals(0x0001, cpu.registers().bc());
        assertEquals(0x22, bus.read(0x5001));

        assertEquals(16, cpu.runInstruction());
        assertEquals(0x000B, cpu.registers().pc());
        assertEquals(0x4003, cpu.registers().hl());
        assertEquals(0x5003, cpu.registers().de());
        assertEquals(0x0000, cpu.registers().bc());
        assertEquals(0x33, bus.read(0x5002));
    }

    @Test
    void waitStateCallbacksReceiveInstructionPhaseInsteadOfOnlyAccumulatedWaits() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x32, 0x34, 0x12,
                0xDB, 0xFE
        );
        bus.setWaitStates(2, 1, 3, 4, 0);
        bus.setPortValue(0x55FE, 0xA5);

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setA(0x55);

        assertEquals(20, cpu.runInstruction());
        assertEquals(List.of(0), bus.fetchOpcodeWaitPhases());
        assertEquals(List.of(6, 10), bus.readMemoryWaitPhases());
        assertEquals(List.of(14), bus.writeMemoryWaitPhases());

        assertEquals(18, cpu.runInstruction());
        assertEquals(List.of(0, 0), bus.fetchOpcodeWaitPhases());
        assertEquals(List.of(6, 10, 6), bus.readMemoryWaitPhases());
        assertEquals(List.of(10), bus.readPortWaitPhases());
        assertEquals(0xA5, cpu.registers().a());
    }

    @Test
    void indexedCbPrefixAdvancesRByTwoOnly() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0xFD, 0xCB, 0x02, 0xDE
        );

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setIy(0x4000);
        bus.writeMemory(0x4002, 0x00);

        assertEquals(23, cpu.runInstruction());
        assertEquals(0x02, cpu.registers().r(), "FD CB d op should advance R by 2 fetches only");
        assertEquals(0x08, bus.read(0x4002), "SET 3,(IY+2) should modify the indexed byte");
    }

    @Test
    void bitInstructionDoesNotModifyMemory() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x21, 0x00, 0x40,
                0xCB, 0x7E
        );
        bus.writeMemory(0x4000, 0x11);

        Z80Cpu cpu = new Z80Cpu(bus);

        cpu.runInstruction();
        assertEquals(0x11, bus.read(0x4000));

        assertEquals(12, cpu.runInstruction());
        assertEquals(0x11, bus.read(0x4000), "BIT 7,(HL) must not alter the memory byte");
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_Z), "Bit 7 of 0x11 is clear so Z should be set");
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_H));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_N));
    }

    @Test
    void edPrefixSupportsOutdAndOtdr() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x21, 0x02, 0x40,
                0x01, 0x03, 0x10,
                0xED, 0xAB,
                0xED, 0xBB
        );
        bus.writeMemory(0x4000, 0x11);
        bus.writeMemory(0x4001, 0x22);
        bus.writeMemory(0x4002, 0x33);

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(10, cpu.runInstruction());
        assertEquals(10, cpu.runInstruction());

        assertEquals(16, cpu.runInstruction());
        assertEquals(0x1003, bus.lastPortWritePort());
        assertEquals(0x33, bus.lastPortWriteValue());
        assertEquals(0x4001, cpu.registers().hl());
        assertEquals(0x0F03, cpu.registers().bc());

        for (int iteration = 0; iteration < 14; iteration++) {
            assertEquals(21, cpu.runInstruction());
            assertEquals(0x0008, cpu.registers().pc());
        }

        assertEquals(16, cpu.runInstruction());
        assertEquals(0x0103, bus.lastPortWritePort());
        assertEquals(0x00, bus.lastPortWriteValue());
        assertEquals(0x3FF2, cpu.registers().hl());
        assertEquals(0x0003, cpu.registers().bc());
        assertEquals(0x000A, cpu.registers().pc());
    }

    @Test
    void undocumentedEd70AndEd71BehaveLikeInFlagsAndOutZero() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x01, 0x34, 0x12,
                0xED, 0x70,
                0xED, 0x71
        );
        bus.setPortValue(0x1234, 0xA5);

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setA(0x77);

        assertEquals(10, cpu.runInstruction());

        assertEquals(12, cpu.runInstruction());
        assertEquals(0x77, cpu.registers().a(), "ED70 should not modify A");
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_S));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_Z));

        assertEquals(12, cpu.runInstruction());
        assertEquals(0x1234, bus.lastPortWritePort());
        assertEquals(0x00, bus.lastPortWriteValue());
    }

    @Test
    void undocumentedEdHolesActAsEightTStateNops() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0xED, 0x00,
                0xED, 0x77,
                0xED, 0x80,
                0xED, 0xFF
        );

        Z80Cpu cpu = new Z80Cpu(bus);
        cpu.registers().setAf(0x1234);
        cpu.registers().setBc(0x5678);
        cpu.registers().setDe(0x9ABC);
        cpu.registers().setHl(0xDEF0);

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x0002, cpu.registers().pc());
        assertEquals(0x02, cpu.registers().r());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x0004, cpu.registers().pc());
        assertEquals(0x04, cpu.registers().r());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x0006, cpu.registers().pc());
        assertEquals(0x06, cpu.registers().r());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x0008, cpu.registers().pc());
        assertEquals(0x08, cpu.registers().r());

        assertEquals(0x1234, cpu.registers().af());
        assertEquals(0x5678, cpu.registers().bc());
        assertEquals(0x9ABC, cpu.registers().de());
        assertEquals(0xDEF0, cpu.registers().hl());
    }

    @Test
    void undocumentedIndexHighLowRegisterOpsWork() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0xDD, 0x21, 0x34, 0x12,
                0xFD, 0x21, 0x78, 0x56,
                0xDD, 0x44,
                0xDD, 0x6F,
                0xFD, 0x7C,
                0xFD, 0x65,
                0xDD, 0xAC,
                0xDD, 0xAD
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(14, cpu.runInstruction());
        assertEquals(14, cpu.runInstruction());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x12, cpu.registers().b());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x1200, cpu.registers().ix());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x56, cpu.registers().a());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x7878, cpu.registers().iy(), "FD 65 should copy IYL into IYH");

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x44, cpu.registers().a());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0x44, cpu.registers().a());
    }

    @Test
    void undocumentedSllOpcodesSetBitZero() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x06, 0x81,
                0xCB, 0x30,
                0x21, 0x00, 0x40,
                0x36, 0x81,
                0xDD, 0x21, 0x00, 0x40,
                0xDD, 0xCB, 0x00, 0x36
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(7, cpu.runInstruction());
        assertEquals(8, cpu.runInstruction());
        assertEquals(0x03, cpu.registers().b());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_C));

        assertEquals(10, cpu.runInstruction());
        assertEquals(10, cpu.runInstruction());
        assertEquals(14, cpu.runInstruction());
        assertEquals(23, cpu.runInstruction());
        assertEquals(0x03, bus.read(0x4000));
    }

    @Test
    void undocumentedEdAliasesMapToDocumentedBehavior() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x3E, 0x01,
                0xED, 0x4C,
                0xED, 0x6E,
                0xED, 0x76,
                0xED, 0x7E
        );

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(7, cpu.runInstruction());

        assertEquals(8, cpu.runInstruction());
        assertEquals(0xFF, cpu.registers().a(), "ED4C should behave like NEG");

        assertEquals(8, cpu.runInstruction());
        assertEquals(0, cpu.registers().interruptMode());

        assertEquals(8, cpu.runInstruction());
        assertEquals(1, cpu.registers().interruptMode());

        assertEquals(8, cpu.runInstruction());
        assertEquals(2, cpu.registers().interruptMode());
    }

    @Test
    void ldiSetsUndocumentedFlagBitsFromAccumulatorPlusTransferredByte() {
        TestBus bus = new TestBus();
        bus.load(
                0x0000,
                0x21, 0x00, 0x40,
                0x11, 0x00, 0x50,
                0x01, 0x02, 0x00,
                0x3E, 0x17,
                0xED, 0xA0
        );
        bus.writeMemory(0x4000, 0x2A);

        Z80Cpu cpu = new Z80Cpu(bus);

        assertEquals(10, cpu.runInstruction());
        assertEquals(10, cpu.runInstruction());
        assertEquals(10, cpu.runInstruction());
        assertEquals(7, cpu.runInstruction());
        assertEquals(16, cpu.runInstruction());

        assertEquals(0x2A, bus.read(0x5000));
        assertEquals(0x4001, cpu.registers().hl());
        assertEquals(0x5001, cpu.registers().de());
        assertEquals(0x0001, cpu.registers().bc());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_5), "Bit 5 should come from A + transferred byte");
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_3), "Bit 3 should come from A + transferred byte");
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

        assertEquals(21, cpu.runInstruction());
        assertEquals(0x0000, cpu.registers().pc());
        assertEquals(0x11, bus.read(0x5000));
        assertEquals(0x4001, cpu.registers().hl());
        assertEquals(0x5001, cpu.registers().de());
        assertEquals(0x0002, cpu.registers().bc());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));

        assertEquals(21, cpu.runInstruction());
        assertEquals(0x0000, cpu.registers().pc());
        assertEquals(0x22, bus.read(0x5001));
        assertEquals(0x4002, cpu.registers().hl());
        assertEquals(0x5002, cpu.registers().de());
        assertEquals(0x0001, cpu.registers().bc());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));

        assertEquals(16, cpu.runInstruction());
        assertEquals(0x0002, cpu.registers().pc());
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

        assertEquals(21, cpu.runInstruction());
        assertEquals(0x0002, cpu.registers().pc());
        assertEquals(0x6001, cpu.registers().hl());
        assertEquals(0x0002, cpu.registers().bc());
        assertFalse(cpu.registers().flagSet(Z80Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));

        assertEquals(16, cpu.runInstruction());
        assertEquals(0x6002, cpu.registers().hl());
        assertEquals(0x0001, cpu.registers().bc());
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_Z));
        assertTrue(cpu.registers().flagSet(Z80Registers.FLAG_PV));
        assertEquals(0x0004, cpu.registers().pc());
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
        private final List<Integer> fetchOpcodeWaitPhases = new ArrayList<>();
        private final List<Integer> readMemoryWaitPhases = new ArrayList<>();
        private final List<Integer> writeMemoryWaitPhases = new ArrayList<>();
        private final List<Integer> readPortWaitPhases = new ArrayList<>();
        private final List<Integer> writePortWaitPhases = new ArrayList<>();
        private int fetchOpcodeWaitStates;
        private int readMemoryWaitStates;
        private int writeMemoryWaitStates;
        private int readPortWaitStates;
        private int writePortWaitStates;

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

        void setWaitStates(
                int fetchOpcodeWaitStates,
                int readMemoryWaitStates,
                int writeMemoryWaitStates,
                int readPortWaitStates,
                int writePortWaitStates
        ) {
            this.fetchOpcodeWaitStates = fetchOpcodeWaitStates;
            this.readMemoryWaitStates = readMemoryWaitStates;
            this.writeMemoryWaitStates = writeMemoryWaitStates;
            this.readPortWaitStates = readPortWaitStates;
            this.writePortWaitStates = writePortWaitStates;
        }

        List<Integer> fetchOpcodeWaitPhases() {
            return List.copyOf(fetchOpcodeWaitPhases);
        }

        List<Integer> readMemoryWaitPhases() {
            return List.copyOf(readMemoryWaitPhases);
        }

        List<Integer> writeMemoryWaitPhases() {
            return List.copyOf(writeMemoryWaitPhases);
        }

        List<Integer> readPortWaitPhases() {
            return List.copyOf(readPortWaitPhases);
        }

        @Override
        public int fetchOpcode(int address) {
            return read(address);
        }

        @Override
        public int fetchOpcodeWaitStates(int address, int phaseTStates) {
            fetchOpcodeWaitPhases.add(phaseTStates);
            return fetchOpcodeWaitStates;
        }

        @Override
        public int readMemory(int address) {
            return read(address);
        }

        @Override
        public int readMemoryWaitStates(int address, int phaseTStates) {
            readMemoryWaitPhases.add(phaseTStates);
            return readMemoryWaitStates;
        }

        @Override
        public void writeMemory(int address, int value) {
            memory[address & 0xFFFF] = (byte) value;
        }

        @Override
        public int writeMemoryWaitStates(int address, int value, int phaseTStates) {
            writeMemoryWaitPhases.add(phaseTStates);
            return writeMemoryWaitStates;
        }

        @Override
        public int readPort(int port) {
            return portValues[port & 0xFFFF];
        }

        @Override
        public int readPortWaitStates(int port, int phaseTStates) {
            readPortWaitPhases.add(phaseTStates);
            return readPortWaitStates;
        }

        @Override
        public void writePort(int port, int value) {
            lastPortWritePort = port & 0xFFFF;
            lastPortWriteValue = value & 0xFF;
        }

        @Override
        public int writePortWaitStates(int port, int value, int phaseTStates) {
            writePortWaitPhases.add(phaseTStates);
            return writePortWaitStates;
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
