package dev.z8emu.machine.c64;

import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class C64MachineTest {
    @Test
    void palModelUsesTheFrozenClockFrameAndGeometry() {
        C64ModelConfig config = C64ModelConfig.pal();

        assertEquals("Commodore 64 (PAL)", config.modelName());
        assertEquals(985_248, config.cpuClockHz());
        assertEquals(19_656, config.frameTStates());
        assertEquals(384, config.frameWidth());
        assertEquals(272, config.frameHeight());
    }

    @Test
    void machineBootsFromSyntheticKernalResetVector() {
        C64Machine machine = bootableMachine();

        assertEquals(0xE000, machine.cpu().registers().pc());
        assertEquals(2, machine.runInstruction());
        assertEquals(0xE001, machine.cpu().registers().pc());
        assertEquals(2, machine.currentTState());
        assertEquals(985_248, machine.cpuClockHz());
        assertEquals(19_656, machine.frameTStates());
    }

    @Test
    void videoFrameIsReusableAndBorderOnlyWhileDisplayIsDisabled() {
        C64Machine machine = bootableMachine();

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(384, frame.width());
        assertEquals(272, frame.height());
        for (int pixel : frame.pixels()) {
            assertEquals(0xFF000000, pixel);
        }
        assertSame(frame, machine.board().renderVideoFrame());
    }

    @Test
    void vicRasterInterruptReachesTheCpuAndDropsAfterAcknowledgement() {
        C64Machine machine = bootableMachine();
        CpuBus bus = machine.board().cpuBus();
        bus.writeMemory(0xD012, 0x05);
        bus.writeMemory(0xD01A, 0x01);

        assertTrue(runUntilProgramCounter(machine, 0xE800, 400));
        assertNotEquals(0, bus.readMemory(0xD019) & 0x80);

        bus.writeMemory(0xD019, 0x01);

        assertEquals(0, bus.readMemory(0xD019) & 0x80);
        for (int instruction = 0; instruction < 10; instruction++) {
            machine.runInstruction();
            assertNotEquals(0xE800, machine.cpu().registers().pc());
        }
    }

    @Test
    void ciaTwoTimerUnderflowDeliversNonMaskableInterrupt() {
        C64Machine machine = bootableMachine();
        armCiaTwoTimer(machine);

        assertTrue(runUntilProgramCounter(machine, 0xE100, 10));
    }

    @Test
    void heldNmiLineDoesNotRetrigger() {
        C64Machine machine = bootableMachine();
        armCiaTwoTimer(machine);
        assertTrue(runUntilProgramCounter(machine, 0xE100, 10));

        for (int instruction = 0; instruction < 10; instruction++) {
            machine.runInstruction();
            assertNotEquals(0xE100, machine.cpu().registers().pc());
        }

        CpuBus bus = machine.board().cpuBus();
        bus.writeMemory(0xDD0E, 0x00);
        bus.readMemory(0xDD0D);
        machine.runInstruction();
        bus.writeMemory(0xDD0E, 0x01);

        assertTrue(runUntilProgramCounter(machine, 0xE100, 10));
    }

    @Test
    void restoreKeyDeliversSingleNonMaskableInterrupt() {
        C64Machine machine = bootableMachine();
        machine.board().keyboard().setRestorePressed(true);

        assertTrue(runUntilProgramCounter(machine, 0xE100, 10));

        for (int instruction = 0; instruction < 10; instruction++) {
            machine.runInstruction();
            assertNotEquals(0xE100, machine.cpu().registers().pc());
        }

        machine.board().keyboard().setRestorePressed(false);
        machine.runInstruction();
        machine.board().keyboard().setRestorePressed(true);

        assertTrue(runUntilProgramCounter(machine, 0xE100, 10));
    }

    @Test
    void romSizeValidationRejectsEveryIncorrectImageSize() {
        byte[] basicRom = new byte[C64Memory.BASIC_ROM_SIZE];
        byte[] kernalRom = bootableKernalRom();
        byte[] chargenRom = new byte[C64Memory.CHAR_ROM_SIZE];

        assertThrows(
                IllegalArgumentException.class,
                () -> new C64Machine(new byte[C64Memory.BASIC_ROM_SIZE - 1], kernalRom, chargenRom)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new C64Machine(basicRom, new byte[C64Memory.KERNAL_ROM_SIZE + 1], chargenRom)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new C64Machine(basicRom, kernalRom, new byte[C64Memory.CHAR_ROM_SIZE - 1])
        );
    }

    private static C64Machine bootableMachine() {
        byte[] basicRom = new byte[C64Memory.BASIC_ROM_SIZE];
        byte[] kernalRom = bootableKernalRom();
        byte[] chargenRom = new byte[C64Memory.CHAR_ROM_SIZE];
        return new C64Machine(basicRom, kernalRom, chargenRom);
    }

    private static byte[] bootableKernalRom() {
        byte[] kernalRom = new byte[C64Memory.KERNAL_ROM_SIZE];
        Arrays.fill(kernalRom, (byte) 0xEA);
        kernalRom[0x0000] = 0x58;
        kernalRom[0x0800] = 0x58;
        kernalRom[0x1FFA] = 0x00;
        kernalRom[0x1FFB] = (byte) 0xE1;
        kernalRom[0x1FFC] = 0x00;
        kernalRom[0x1FFD] = (byte) 0xE0;
        kernalRom[0x1FFE] = 0x00;
        kernalRom[0x1FFF] = (byte) 0xE8;
        return kernalRom;
    }

    private static void armCiaTwoTimer(C64Machine machine) {
        CpuBus bus = machine.board().cpuBus();
        bus.writeMemory(0xDD0D, 0x81);
        bus.writeMemory(0xDD04, 0x02);
        bus.writeMemory(0xDD05, 0x00);
        bus.writeMemory(0xDD0E, 0x01);
    }

    private static boolean runUntilProgramCounter(C64Machine machine, int programCounter, int limit) {
        for (int instruction = 0; instruction < limit; instruction++) {
            machine.runInstruction();
            if (machine.cpu().registers().pc() == programCounter) {
                return true;
            }
        }
        return false;
    }
}
