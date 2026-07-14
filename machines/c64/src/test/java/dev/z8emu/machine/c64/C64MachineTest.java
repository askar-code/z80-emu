package dev.z8emu.machine.c64;

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
    void placeholderVideoFrameIsReusableBlackPalFrame() {
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
        Arrays.fill(kernalRom, (byte) 0xE0);
        kernalRom[0x0000] = (byte) 0xEA;
        kernalRom[0x1FFC] = 0x00;
        kernalRom[0x1FFD] = (byte) 0xE0;
        return kernalRom;
    }
}
