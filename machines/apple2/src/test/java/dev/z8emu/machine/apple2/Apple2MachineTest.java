package dev.z8emu.machine.apple2;

import dev.z8emu.platform.video.FrameBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Apple2MachineTest {
    private static final int BACKGROUND_ARGB = 0xFF101010;
    private static final int FOREGROUND_ARGB = 0xFF66FF66;
    private static final int LORES_DARK_BLUE_ARGB = 0xFF000099;
    private static final int LORES_DARK_GREEN_ARGB = 0xFF007722;
    private static final int LORES_GREEN_ARGB = 0xFF00CC00;

    @Test
    void defaultModelIsAppleIIPlus() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();

        assertEquals("Apple II Plus", machine.board().modelName());
    }

    @Test
    void runs6502ProgramAgainstApple2Bus() {
        byte[] image = new byte[Apple2Memory.ADDRESS_SPACE_SIZE];
        load(image, 0x0800, 0xA9, 0x41, 0x8D, 0x00, 0x04);
        writeVector(image, 0xFFFC, 0x0800);
        Apple2Machine machine = new Apple2Machine(image);

        assertEquals(2, machine.runInstruction());
        assertEquals(4, machine.runInstruction());

        assertEquals(0x41, machine.cpu().registers().a());
        assertEquals(0x41, machine.board().memory().read(Apple2Memory.TEXT_PAGE_1_START));
        assertEquals(0x0805, machine.cpu().registers().pc());
        assertEquals(6, machine.currentTState());
    }

    @Test
    void resetRestoresInitialMemoryImageAndRuntimeClock() {
        byte[] image = new byte[Apple2Memory.ADDRESS_SPACE_SIZE];
        load(image, 0x0800, 0xA9, 0x22, 0x8D, 0x00, 0x04);
        image[Apple2Memory.TEXT_PAGE_1_START] = 0x11;
        writeVector(image, 0xFFFC, 0x0800);
        Apple2Machine machine = new Apple2Machine(image);

        machine.runInstruction();
        machine.runInstruction();
        assertEquals(0x22, machine.board().memory().read(Apple2Memory.TEXT_PAGE_1_START));
        assertEquals(6, machine.currentTState());

        machine.reset();

        assertEquals(0x11, machine.board().memory().read(Apple2Memory.TEXT_PAGE_1_START));
        assertEquals(0x0800, machine.cpu().registers().pc());
        assertEquals(0, machine.currentTState());
    }

    @Test
    void routesRamIoAndEmptySlotRomRanges() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();

        machine.board().cpuBus().writeMemory(0x0000, 0x12);
        machine.board().cpuBus().writeMemory(Apple2Memory.IO_START, 0x34);
        machine.board().cpuBus().writeMemory(Apple2Memory.SLOT_ROM_START, 0x45);
        machine.board().cpuBus().writeMemory(0xFFFF, 0x56);

        assertEquals(0x12, machine.board().cpuBus().readMemory(0x0000));
        assertEquals(0x00, machine.board().cpuBus().readMemory(Apple2Memory.IO_START));
        assertEquals(0x00, machine.board().memory().read(Apple2Memory.IO_START));
        assertEquals(0xFF, machine.board().cpuBus().readMemory(Apple2Memory.SLOT_ROM_START));
        assertEquals(0x56, machine.board().cpuBus().readMemory(0xFFFF));
    }

    @Test
    void optionalSystemRomMapsAtTheTopOfMemoryAndIgnoresWrites() {
        byte[] rom = filledRom(Apple2Memory.SYSTEM_ROM_MAX_SIZE, 0xEA);
        rom[0] = (byte) 0xA9;
        rom[rom.length - 1] = 0x60;
        Apple2Memory memory = new Apple2Memory(new byte[0], rom);

        assertEquals(Apple2Memory.SYSTEM_ROM_START, memory.systemRomStart());
        assertEquals(0xA9, memory.read(Apple2Memory.SYSTEM_ROM_START));
        assertEquals(0x60, memory.read(0xFFFF));

        memory.write(Apple2Memory.SYSTEM_ROM_START, 0x00);

        assertEquals(0xA9, memory.read(Apple2Memory.SYSTEM_ROM_START));
    }

    @Test
    void resetVectorCanBeFetchedThroughSystemRom() {
        byte[] rom = filledRom(Apple2Memory.SYSTEM_ROM_MAX_SIZE, 0xEA);
        writeRomVector(rom, 0xFFFC, Apple2Memory.SYSTEM_ROM_START);

        Apple2Machine machine = new Apple2Machine(new byte[0], rom);

        assertEquals(Apple2Memory.SYSTEM_ROM_START, machine.cpu().registers().pc());
        assertEquals(0xEA, machine.board().cpuBus().fetchOpcode(machine.cpu().registers().pc()));
    }

    @Test
    void launchImageFactoryAcceptsSystemRomAndFullMemoryImages() {
        byte[] rom = filledRom(Apple2Memory.SYSTEM_ROM_SIZE_12K, 0xEA);
        writeRomVector(rom, 0xFFFC, Apple2Memory.SYSTEM_ROM_START);
        Apple2Machine romMachine = Apple2Machine.fromLaunchImage(rom);

        assertEquals(Apple2Memory.SYSTEM_ROM_START, romMachine.cpu().registers().pc());

        byte[] image = new byte[Apple2Memory.ADDRESS_SPACE_SIZE];
        load(image, 0x0800, 0xEA);
        writeVector(image, 0xFFFC, 0x0800);
        Apple2Machine memoryMachine = Apple2Machine.fromLaunchImage(image);

        assertEquals(0x0800, memoryMachine.cpu().registers().pc());
        assertThrows(IllegalArgumentException.class, () -> Apple2Machine.fromLaunchImage(new byte[16 * 1024]));
    }

    @Test
    void rawProgramLoaderPlacesBytesInRamAndCanOverrideProgramCounter() {
        Apple2Machine machine = Apple2Machine.fromLaunchImage(filledRom(Apple2Memory.SYSTEM_ROM_SIZE_12K, 0xEA));

        machine.loadProgram(new byte[]{(byte) 0xA9, (byte) 0xC1}, 0x0800);
        machine.setProgramCounter(0x0800);

        assertEquals(0xA9, machine.board().memory().read(0x0800));
        assertEquals(0xC1, machine.board().memory().read(0x0801));
        assertEquals(0x0800, machine.cpu().registers().pc());
    }

    @Test
    void rawProgramLoaderRejectsProgramsThatDoNotFitWritableRam() {
        Apple2Machine romMachine = Apple2Machine.fromLaunchImage(filledRom(Apple2Memory.SYSTEM_ROM_SIZE_12K, 0xEA));
        Apple2Machine ramMachine = Apple2Machine.withBlankMemory();

        assertThrows(IllegalArgumentException.class, () -> romMachine.loadProgram(new byte[]{0x00}, 0xD000));
        assertThrows(IllegalArgumentException.class, () -> ramMachine.loadProgram(new byte[]{0x00, 0x00}, 0xFFFF));
    }

    @Test
    void languageCardCanWriteRamBehindRomAfterDoubleWriteEnableSwitch() {
        byte[] rom = filledRom(Apple2Memory.SYSTEM_ROM_SIZE_12K, 0xEA);
        rom[0x1000] = (byte) 0x4C;
        Apple2Machine machine = Apple2Machine.fromLaunchImage(rom);

        machine.board().cpuBus().readMemory(0xC081);
        machine.board().cpuBus().readMemory(0xC081);
        machine.board().cpuBus().writeMemory(0xE000, 0xA9);

        assertEquals(0x4C, machine.board().cpuBus().readMemory(0xE000));

        machine.board().cpuBus().readMemory(0xC080);

        assertEquals(0xA9, machine.board().cpuBus().readMemory(0xE000));
    }

    @Test
    void languageCardKeepsSeparateD000BanksAndSharedE000Ram() {
        Apple2Machine machine = Apple2Machine.fromLaunchImage(filledRom(Apple2Memory.SYSTEM_ROM_SIZE_12K, 0xEA));

        machine.board().cpuBus().readMemory(0xC083);
        machine.board().cpuBus().readMemory(0xC083);
        machine.board().cpuBus().writeMemory(0xD000, 0x22);
        machine.board().cpuBus().writeMemory(0xE000, 0x33);
        machine.board().cpuBus().readMemory(0xC08B);
        machine.board().cpuBus().readMemory(0xC08B);
        machine.board().cpuBus().writeMemory(0xD000, 0x44);

        assertEquals(0x44, machine.board().cpuBus().readMemory(0xD000));
        assertEquals(0x33, machine.board().cpuBus().readMemory(0xE000));

        machine.board().cpuBus().readMemory(0xC083);
        machine.board().cpuBus().readMemory(0xC083);

        assertEquals(0x22, machine.board().cpuBus().readMemory(0xD000));
        assertEquals(0x33, machine.board().cpuBus().readMemory(0xE000));
    }

    @Test
    void rawMachineCodeProgramCanWriteTextPage() {
        Apple2Machine machine = Apple2Machine.fromLaunchImage(filledRom(Apple2Memory.SYSTEM_ROM_SIZE_12K, 0xEA));
        machine.loadProgram(new byte[]{
                (byte) 0xA9, (byte) 0xC1,
                (byte) 0x8D, 0x00, 0x04,
                (byte) 0x4C, 0x05, 0x08
        }, 0x0800);
        machine.setProgramCounter(0x0800);

        assertEquals(2, machine.runInstruction());
        assertEquals(4, machine.runInstruction());

        assertEquals(0xC1, machine.board().memory().read(Apple2Memory.TEXT_PAGE_1_START));
    }

    @Test
    void rawMachineCodeProgramCanToggleSpeaker() {
        Apple2Machine machine = Apple2Machine.fromLaunchImage(filledRom(Apple2Memory.SYSTEM_ROM_SIZE_12K, 0xEA));
        machine.loadProgram(new byte[]{
                (byte) 0xAD, 0x30, (byte) 0xC0,
                (byte) 0x4C, 0x00, 0x08
        }, 0x0800);
        machine.setProgramCounter(0x0800);

        assertEquals(4, machine.runInstruction());

        assertTrue(machine.board().speaker().high());
    }

    @Test
    void softSwitchIoReadsAndWritesUpdateVideoState() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();

        assertEquals(0x00, machine.board().cpuBus().readMemory(0xC050));
        assertEquals(false, machine.board().softSwitches().textMode());

        machine.board().cpuBus().writeMemory(0xC051, 0x00);
        assertEquals(true, machine.board().softSwitches().textMode());

        machine.board().cpuBus().readMemory(0xC055);
        assertEquals(true, machine.board().softSwitches().page2());

        machine.board().cpuBus().writeMemory(0xC054, 0x00);
        assertEquals(false, machine.board().softSwitches().page2());
    }

    @Test
    void keyboardDataReadReturnsLatchedAsciiWithStrobeUntilC010ClearsIt() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();

        machine.board().keyboard().pressKey('A');

        assertEquals(0xC1, machine.board().cpuBus().readMemory(0xC000));
        assertTrue(machine.board().keyboard().strobe());

        assertEquals(0x00, machine.board().cpuBus().readMemory(0xC010));

        assertFalse(machine.board().keyboard().strobe());
        assertEquals(0x41, machine.board().cpuBus().readMemory(0xC000));
    }

    @Test
    void cpuCanReadKeyboardLatchThroughApple2Bus() {
        byte[] image = new byte[Apple2Memory.ADDRESS_SPACE_SIZE];
        load(image, 0x0800, 0xAD, 0x00, 0xC0);
        writeVector(image, 0xFFFC, 0x0800);
        Apple2Machine machine = new Apple2Machine(image);
        machine.board().keyboard().pressKey('Z');

        assertEquals(4, machine.runInstruction());

        assertEquals(0xDA, machine.cpu().registers().a());
    }

    @Test
    void idleSpeakerProducesSilentPcm() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();

        machine.board().onTStatesElapsed(5_000, 5_000);
        byte[] audio = new byte[256];
        int drained = machine.board().speaker().drainAudio(audio, 0, audio.length);

        assertTrue(drained > 0);
        assertFalse(anyNonZero(audio, drained));
    }

    @Test
    void speakerTogglesOnC030ReadAndWriteAndProducesPcm() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();

        machine.board().cpuBus().readMemory(0xC030);
        assertTrue(machine.board().speaker().high());

        machine.board().onTStatesElapsed(5_000, 5_000);
        byte[] audio = new byte[256];
        int drained = machine.board().speaker().drainAudio(audio, 0, audio.length);

        assertTrue(drained > 0);
        assertTrue(anyNonZero(audio, drained));

        machine.board().cpuBus().writeMemory(0xC030, 0x00);
        assertFalse(machine.board().speaker().high());
    }

    @Test
    void staticSpeakerLevelDecaysBackToSilence() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        byte[] audio = new byte[512];

        machine.board().cpuBus().readMemory(0xC030);
        machine.board().onTStatesElapsed(100_000, 100_000);
        while (machine.board().speaker().drainAudio(audio, 0, audio.length) > 0) {
            // Drop the initial edge impulse before checking the settled level.
        }

        machine.board().onTStatesElapsed(20_000, 120_000);
        int drained = machine.board().speaker().drainAudio(audio, 0, audio.length);

        assertTrue(drained > 0);
        assertFalse(anyNonZero(audio, drained));
    }

    @Test
    void cpuCanToggleSpeakerThroughApple2Bus() {
        byte[] image = new byte[Apple2Memory.ADDRESS_SPACE_SIZE];
        load(image, 0x0800, 0xAD, 0x30, 0xC0);
        writeVector(image, 0xFFFC, 0x0800);
        Apple2Machine machine = new Apple2Machine(image);

        assertEquals(4, machine.runInstruction());

        assertTrue(machine.board().speaker().high());
        assertEquals(0x00, machine.cpu().registers().a());
    }

    @Test
    void rejectsInitialImagesLargerThanThe6502AddressSpace() {
        assertThrows(IllegalArgumentException.class, () -> new Apple2Memory(new byte[Apple2Memory.ADDRESS_SPACE_SIZE + 1]));
        assertThrows(IllegalArgumentException.class, () -> new Apple2Memory(
                new byte[0],
                new byte[Apple2Memory.SYSTEM_ROM_MAX_SIZE + 1]
        ));
    }

    @Test
    void rendersTextPage1WithAppleScreenLineLayout() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.board().memory().write(Apple2Memory.textPage1Address(0, 0), 0xC1);
        machine.board().memory().write(Apple2Memory.textPage1Address(8, 0), 0xC2);

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(Apple2VideoDevice.FRAME_WIDTH, frame.width());
        assertEquals(Apple2VideoDevice.FRAME_HEIGHT, frame.height());
        assertEquals(0x0428, Apple2Memory.textPage1Address(8, 0));
        assertEquals(BACKGROUND_ARGB, pixel(frame, 0, 0));
        assertEquals(FOREGROUND_ARGB, pixel(frame, 2, 0));
        assertEquals(FOREGROUND_ARGB, pixel(frame, 1, 8 * Apple2VideoDevice.CELL_HEIGHT));
    }

    @Test
    void textRendererCanSwitchToPage2() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.board().memory().write(Apple2Memory.textPage1Address(0, 0), 0xC1);
        machine.board().memory().write(Apple2Memory.textPage2Address(0, 0), 0xC2);
        machine.board().cpuBus().readMemory(0xC055);

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(FOREGROUND_ARGB, pixel(frame, 1, 0));
        assertEquals(BACKGROUND_ARGB, pixel(frame, 5, 0));
    }

    @Test
    void graphicsAddressHelpersFollowAppleScreenLineLayout() {
        assertEquals(0x0400, Apple2Memory.loresPage1Address(0, 0));
        assertEquals(0x0400, Apple2Memory.loresPage1Address(1, 0));
        assertEquals(0x0480, Apple2Memory.loresPage1Address(2, 0));
        assertEquals(0x0800, Apple2Memory.loresPage2Address(0, 0));
        assertEquals(0x2000, Apple2Memory.hiresPage1Address(0, 0));
        assertEquals(0x2400, Apple2Memory.hiresPage1Address(1, 0));
        assertEquals(0x2080, Apple2Memory.hiresPage1Address(8, 0));
        assertEquals(0x2028, Apple2Memory.hiresPage1Address(64, 0));
        assertEquals(0x4000, Apple2Memory.hiresPage2Address(0, 0));
    }

    @Test
    void loresGraphicsRendersPage1NibblesAsFourLineBlocks() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.board().memory().write(Apple2Memory.loresPage1Address(0, 0), 0xC4);
        machine.board().cpuBus().readMemory(0xC050);
        machine.board().cpuBus().readMemory(0xC056);

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(LORES_DARK_GREEN_ARGB, pixel(frame, 1, 1));
        assertEquals(LORES_DARK_GREEN_ARGB, pixel(frame, 6, 3));
        assertEquals(LORES_GREEN_ARGB, pixel(frame, 1, 4));
        assertEquals(LORES_GREEN_ARGB, pixel(frame, 6, 7));
    }

    @Test
    void mixedLoresGraphicsKeepsBottomFourTextRows() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.board().memory().write(Apple2Memory.loresPage1Address(0, 0), 0x22);
        machine.board().memory().write(Apple2Memory.textPage1Address(20, 0), 0xC1);
        machine.board().cpuBus().readMemory(0xC050);
        machine.board().cpuBus().readMemory(0xC056);
        machine.board().cpuBus().readMemory(0xC053);

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(LORES_DARK_BLUE_ARGB, pixel(frame, 1, 1));
        assertEquals(FOREGROUND_ARGB, pixel(frame, 2, 20 * Apple2VideoDevice.CELL_HEIGHT));
    }

    @Test
    void hiresGraphicsRendersSevenPixelsPerScreenByte() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.board().memory().write(Apple2Memory.hiresPage1Address(0, 0), 0x41);
        machine.board().cpuBus().readMemory(0xC050);
        machine.board().cpuBus().readMemory(0xC057);

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(FOREGROUND_ARGB, pixel(frame, 0, 0));
        assertEquals(BACKGROUND_ARGB, pixel(frame, 1, 0));
        assertEquals(FOREGROUND_ARGB, pixel(frame, 6, 0));
    }

    @Test
    void graphicsRendererCanSwitchLoResAndHiResToPage2() {
        Apple2Machine machine = Apple2Machine.withBlankMemory();
        machine.board().memory().write(Apple2Memory.loresPage1Address(0, 0), 0x44);
        machine.board().memory().write(Apple2Memory.loresPage2Address(0, 0), 0x22);
        machine.board().cpuBus().readMemory(0xC050);
        machine.board().cpuBus().readMemory(0xC056);
        machine.board().cpuBus().readMemory(0xC055);

        FrameBuffer loresFrame = machine.board().renderVideoFrame();

        assertEquals(LORES_DARK_BLUE_ARGB, pixel(loresFrame, 1, 1));

        machine.board().memory().write(Apple2Memory.hiresPage1Address(0, 0), 0x01);
        machine.board().memory().write(Apple2Memory.hiresPage2Address(0, 0), 0x02);
        machine.board().cpuBus().readMemory(0xC057);

        FrameBuffer hiresFrame = machine.board().renderVideoFrame();

        assertEquals(BACKGROUND_ARGB, pixel(hiresFrame, 0, 0));
        assertEquals(FOREGROUND_ARGB, pixel(hiresFrame, 1, 0));
    }

    @Test
    void textRendererBlinksFlashingSpaceCursorCell() {
        Apple2Memory memory = new Apple2Memory();
        Apple2SoftSwitches softSwitches = new Apple2SoftSwitches();
        softSwitches.reset();
        Apple2VideoDevice video = new Apple2VideoDevice(Apple2VideoDevice.FRAME_WIDTH, Apple2VideoDevice.FRAME_HEIGHT);
        int frameTStates = Apple2ModelConfig.appleIIPlus().frameTStates();
        memory.write(Apple2Memory.textPage1Address(0, 0), 0x60);
        memory.write(Apple2Memory.textPage1Address(0, 1), 0xE0);

        FrameBuffer visibleCursor = video.renderFrame(memory, softSwitches, 0, frameTStates);
        FrameBuffer hiddenCursor = video.renderFrame(memory, softSwitches, 16L * frameTStates, frameTStates);

        assertEquals(FOREGROUND_ARGB, pixel(visibleCursor, 1, 0));
        assertEquals(BACKGROUND_ARGB, pixel(visibleCursor, Apple2VideoDevice.CELL_WIDTH + 1, 0));
        assertEquals(BACKGROUND_ARGB, pixel(hiddenCursor, 1, 0));
    }

    private static void load(byte[] image, int startAddress, int... values) {
        for (int i = 0; i < values.length; i++) {
            image[(startAddress + i) & 0xFFFF] = (byte) values[i];
        }
    }

    private static void writeVector(byte[] image, int address, int target) {
        image[address & 0xFFFF] = (byte) target;
        image[(address + 1) & 0xFFFF] = (byte) (target >>> 8);
    }

    private static void writeRomVector(byte[] rom, int vectorAddress, int target) {
        int romStart = Apple2Memory.ADDRESS_SPACE_SIZE - rom.length;
        int offset = vectorAddress - romStart;
        rom[offset] = (byte) target;
        rom[offset + 1] = (byte) (target >>> 8);
    }

    private static byte[] filledRom(int length, int value) {
        byte[] rom = new byte[length];
        for (int i = 0; i < rom.length; i++) {
            rom[i] = (byte) value;
        }
        return rom;
    }

    private static int pixel(FrameBuffer frame, int x, int y) {
        return frame.pixels()[(y * frame.width()) + x];
    }

    private static boolean anyNonZero(byte[] audio, int length) {
        for (int i = 0; i < length; i++) {
            if (audio[i] != 0) {
                return true;
            }
        }
        return false;
    }
}
