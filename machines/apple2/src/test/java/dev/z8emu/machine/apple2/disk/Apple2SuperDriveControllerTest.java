package dev.z8emu.machine.apple2.disk;

import dev.z8emu.machine.apple2.Apple2Machine;
import dev.z8emu.machine.apple2.Apple2Memory;
import dev.z8emu.machine.apple2.Apple2ModelConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Apple2SuperDriveControllerTest {
    @Test
    void requiresRealSizeControllerRom() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Apple2SuperDriveController(5, new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE - 1])
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Apple2SuperDriveController(0, new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE])
        );
    }

    @Test
    void c0nxSelectsBankForC800Window() {
        Apple2SuperDriveController controller = new Apple2SuperDriveController(
                5,
                rom()
        );

        controller.writeC800(0x000, 0x11);
        write(controller, 0xC0D3, 0x00);
        controller.writeC800(0x000, 0x22);

        assertEquals(3, controller.bankSelect());
        assertEquals(0x22, controller.readC800(0x000));
        write(controller, 0xC0D0, 0x00);
        assertEquals(0x11, controller.readC800(0x000));
    }

    @Test
    void mapsCnxxAndFixedC800RangesToControllerRam() {
        Apple2SuperDriveController controller = new Apple2SuperDriveController(
                5,
                romWritingRam(0x7B5C, 0xA5)
        );

        controller.onHostTStatesElapsed(20);
        controller.writeC800(0x400, 0xC8);

        assertTrue(controller.hasCnxxRom());
        assertTrue(controller.usesC800ExpansionRom());
        assertEquals(0xA5, controller.readCnxx(0x5C));
        assertEquals(0xC8, controller.readC800(0x400));
    }

    @Test
    void hostWritesToCnxxDoNotPatchControllerRam() {
        Apple2SuperDriveController controller = new Apple2SuperDriveController(
                5,
                rom()
        );

        controller.writeCnxx(0x5C, 0xA5);

        assertEquals(0x00, controller.readCnxx(0x5C));
        assertEquals(0x00, controller.controllerRamByte(Apple2SuperDriveController.CNXX_RAM_OFFSET + 0x5C));
    }

    @Test
    void machineCanInstallSuperDriveCardInSlot5() {
        byte[] rom = new byte[Apple2Memory.SYSTEM_ROM_SIZE_16K];
        writeRomVector(rom, 0xFFFC, 0xFA62);
        Apple2Machine machine = Apple2Machine.fromLaunchImage(Apple2ModelConfig.appleIIe128K(), rom);

        Apple2SuperDriveController controller = machine.installSuperDrive35Controller(5, rom());

        machine.board().cpuBus().writeMemory(0xC006, 0x00);
        machine.board().cpuBus().writeMemory(0xC0D4, 0x00);
        machine.board().cpuBus().writeMemory(0xC800, 0x42);
        assertEquals(0x42, machine.board().cpuBus().readMemory(0xC800));
        assertEquals(controller, machine.board().superDrive35Controller());
        assertEquals(5, controller.slot());
    }

    @Test
    void onboardControllerCpuRunsFromControllerRom() {
        byte[] rom = new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE];
        int offset = 0x1000;
        rom[offset] = (byte) 0xE6; // INC $20
        rom[offset + 1] = (byte) 0x20;
        rom[offset + 2] = (byte) 0x4C; // JMP $9000
        rom[offset + 3] = (byte) 0x00;
        rom[offset + 4] = (byte) 0x90;
        rom[0x7FFC] = (byte) 0x00;
        rom[0x7FFD] = (byte) 0x90;
        Apple2SuperDriveController controller = new Apple2SuperDriveController(5, rom);

        controller.onHostTStatesElapsed(1);

        assertEquals(0x9002, controller.controllerCpuPc());
        assertEquals(1, controller.controllerRamByte(0x20));
        assertEquals(null, controller.controllerFault());
    }

    @Test
    void tracesOnboardControllerIoAccesses() {
        byte[] rom = new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE];
        int offset = 0x1000;
        rom[offset] = (byte) 0xA9; // LDA #$5A
        rom[offset + 1] = (byte) 0x5A;
        rom[offset + 2] = (byte) 0x8D; // STA $0A80
        rom[offset + 3] = (byte) 0x80;
        rom[offset + 4] = (byte) 0x0A;
        rom[offset + 5] = (byte) 0xAD; // LDA $0A80
        rom[offset + 6] = (byte) 0x80;
        rom[offset + 7] = (byte) 0x0A;
        rom[offset + 8] = (byte) 0x4C; // JMP $9008
        rom[offset + 9] = (byte) 0x08;
        rom[offset + 10] = (byte) 0x90;
        rom[0x7FFC] = (byte) 0x00;
        rom[0x7FFD] = (byte) 0x90;
        Apple2SuperDriveController controller = new Apple2SuperDriveController(5, rom);
        List<Apple2SuperDriveTraceEvent> events = new ArrayList<>();
        controller.setTraceSink(events::add);

        controller.onHostTStatesElapsed(20);

        assertFalse(events.isEmpty());
        Apple2SuperDriveTraceEvent write = events.get(0);
        Apple2SuperDriveTraceEvent read = events.get(1);
        assertEquals(Apple2SuperDriveTraceEvent.Source.CONTROLLER, write.source());
        assertEquals("controller-io", write.region());
        assertFalse(write.read());
        assertEquals(0x0A80, write.address());
        assertEquals(0x80, write.offset());
        assertEquals(0x5A, write.value());
        assertEquals(Apple2SuperDriveTraceEvent.Source.CONTROLLER, read.source());
        assertTrue(read.read());
        assertEquals(0x0A80, read.address());
        assertEquals(0x5A, read.value());
    }

    @Test
    void tracesHostSharedRamWindowAccesses() {
        Apple2SuperDriveController controller = new Apple2SuperDriveController(
                5,
                rom()
        );
        List<Apple2SuperDriveTraceEvent> events = new ArrayList<>();
        controller.setTraceSink(events::add);

        write(controller, 0xC0D2, 0x00);
        controller.writeCnxx(0x5C, 0xA5);
        controller.readCnxx(0x5C);
        controller.writeC800(0x123, 0x7E);
        controller.readC800(0x123);

        assertEquals(5, events.size());
        assertEquals(Apple2SuperDriveTraceEvent.Source.HOST, events.get(0).source());
        assertEquals("c0x", events.get(0).region());
        assertEquals(0xC0D2, events.get(0).address());
        assertEquals(0x02, events.get(0).bankSelect());

        assertEquals("cnxx", events.get(1).region());
        assertFalse(events.get(1).read());
        assertEquals(0xC55C, events.get(1).address());
        assertEquals(0xA5, events.get(1).value());
        assertTrue(events.get(2).read());
        assertEquals(0x00, events.get(2).value());

        assertEquals("c800", events.get(3).region());
        assertEquals(0xC923, events.get(3).address());
        assertEquals(0x7E, events.get(4).value());
    }

    @Test
    void tracesOnboardControllerAccessesToHostTouchedSharedRam() {
        byte[] rom = new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE];
        int offset = 0x1000;
        rom[offset] = (byte) 0xAD; // LDA $0002
        rom[offset + 1] = (byte) 0x02;
        rom[offset + 2] = (byte) 0x00;
        rom[offset + 3] = (byte) 0x85; // STA $20
        rom[offset + 4] = (byte) 0x20;
        rom[offset + 5] = (byte) 0x4C; // JMP $9005
        rom[offset + 6] = (byte) 0x05;
        rom[offset + 7] = (byte) 0x90;
        rom[0x7FFC] = (byte) 0x00;
        rom[0x7FFD] = (byte) 0x90;
        Apple2SuperDriveController controller = new Apple2SuperDriveController(5, rom);
        List<Apple2SuperDriveTraceEvent> events = new ArrayList<>();
        controller.setTraceSink(events::add);

        controller.writeC800(0x002, 0x5A);
        controller.onHostTStatesElapsed(20);

        assertEquals(0x5A, controller.controllerRamByte(0x20));
        Apple2SuperDriveTraceEvent controllerRead = events.stream()
                .filter(event -> event.source() == Apple2SuperDriveTraceEvent.Source.CONTROLLER)
                .filter(event -> event.read())
                .filter(event -> event.address() == 0x0002)
                .findFirst()
                .orElseThrow();
        assertEquals("controller-ram-host-touched", controllerRead.region());
        assertEquals(0x5A, controllerRead.value());
    }

    @Test
    void swimModeStatusAndHandshakeRegistersAreVisibleToOnboardCpu() {
        byte[] rom = new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE];
        int offset = 0x1000;
        int i = offset;
        rom[i++] = (byte) 0xAD; // LDA $0A0E
        rom[i++] = (byte) 0x0E;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0x85; // STA $20
        rom[i++] = (byte) 0x20;
        rom[i++] = (byte) 0xA9; // LDA #$1F
        rom[i++] = (byte) 0x1F;
        rom[i++] = (byte) 0x2C; // BIT $0A0D
        rom[i++] = (byte) 0x0D;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0x8D; // STA $0A0F
        rom[i++] = (byte) 0x0F;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0xAD; // LDA $0A0E
        rom[i++] = (byte) 0x0E;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0x85; // STA $21
        rom[i++] = (byte) 0x21;
        rom[i++] = (byte) 0xA9; // LDA #$00
        rom[i++] = (byte) 0x00;
        rom[i++] = (byte) 0x8D; // STA $0A0F
        rom[i++] = (byte) 0x0F;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0xAD; // LDA $0A0E
        rom[i++] = (byte) 0x0E;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0x85; // STA $22
        rom[i++] = (byte) 0x22;
        rom[i++] = (byte) 0x2C; // BIT $0A0F
        rom[i++] = (byte) 0x0F;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0xAD; // LDA $0A0C
        rom[i++] = (byte) 0x0C;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0x85; // STA $23
        rom[i++] = (byte) 0x23;
        rom[i++] = (byte) 0x4C; // JMP $9024
        rom[i++] = (byte) 0x24;
        rom[i] = (byte) 0x90;
        rom[0x7FFC] = (byte) 0x00;
        rom[0x7FFD] = (byte) 0x90;
        Apple2SuperDriveController controller = new Apple2SuperDriveController(5, rom);

        controller.onHostTStatesElapsed(100);

        assertEquals(0xFF, controller.controllerRamByte(0x20));
        assertEquals(0x5F, controller.controllerRamByte(0x21));
        assertEquals(0x40, controller.controllerRamByte(0x22));
        assertEquals(0xF0, controller.controllerRamByte(0x23));
    }

    @Test
    void onboardControllerCpuCanReadBytesFromSwimMediaStream() {
        byte[] rom = new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE];
        int offset = 0x1000;
        int i = offset;
        rom[i++] = (byte) 0xA9; // LDA #$1F
        rom[i++] = (byte) 0x1F;
        rom[i++] = (byte) 0x2C; // BIT $0A0D
        rom[i++] = (byte) 0x0D;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0x8D; // STA $0A0F
        rom[i++] = (byte) 0x0F;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0xAD; // LDA $0A0E
        rom[i++] = (byte) 0x0E;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0x85; // STA $20
        rom[i++] = (byte) 0x20;
        rom[i++] = (byte) 0x10; // BPL $9008
        rom[i++] = (byte) 0xF9;
        rom[i++] = (byte) 0x2C; // BIT $0A0C
        rom[i++] = (byte) 0x0C;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0xAD; // LDA $0A00
        rom[i++] = (byte) 0x00;
        rom[i++] = (byte) 0x0A;
        rom[i++] = (byte) 0x85; // STA $21
        rom[i++] = (byte) 0x21;
        rom[i++] = (byte) 0x4C; // JMP $9017
        rom[i++] = (byte) 0x17;
        rom[i] = (byte) 0x90;
        rom[0x7FFC] = (byte) 0x00;
        rom[0x7FFD] = (byte) 0x90;
        Apple2SuperDriveController controller = new Apple2SuperDriveController(5, rom);
        controller.insertSwimMediaStream(new CyclingStream(0xD5));

        controller.onHostTStatesElapsed(200);

        assertEquals(0xDF, controller.controllerRamByte(0x20));
        assertEquals(0xD5, controller.controllerRamByte(0x21));
    }

    @Test
    void auxiliaryControllerIoTracksLedAndSideSelectLine() {
        byte[] rom = new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE];
        int offset = 0x1000;
        rom[offset] = (byte) 0x8D; // STA $0A80
        rom[offset + 1] = (byte) 0x80;
        rom[offset + 2] = (byte) 0x0A;
        rom[offset + 3] = (byte) 0x8D; // STA $0A41
        rom[offset + 4] = (byte) 0x41;
        rom[offset + 5] = (byte) 0x0A;
        rom[offset + 6] = (byte) 0x8D; // STA $0A81
        rom[offset + 7] = (byte) 0x81;
        rom[offset + 8] = (byte) 0x0A;
        rom[offset + 9] = (byte) 0x4C; // JMP $9009
        rom[offset + 10] = (byte) 0x09;
        rom[offset + 11] = (byte) 0x90;
        rom[0x7FFC] = (byte) 0x00;
        rom[0x7FFD] = (byte) 0x90;
        Apple2SuperDriveController controller = new Apple2SuperDriveController(5, rom);

        controller.onHostTStatesElapsed(20);

        assertEquals(1, controller.selectedSide());
        assertFalse(controller.diagnosticLedOn());
    }

    @Test
    void drive35StatusFunctionsExposeInsertedWritableDoubleDensityDisk() {
        ControllerProgram program = controllerProgram();
        initializeIwmForDrive35(program);
        readDrive35Status(program, 0x0F, 0x20);
        readDrive35Status(program, 0x08, 0x21);
        readDrive35Status(program, 0x09, 0x22);
        readDrive35Status(program, 0x07, 0x23);
        readDrive35Status(program, 0x06, 0x24);
        readDrive35Status(program, 0x0D, 0x25);
        Apple2SuperDriveController controller = new Apple2SuperDriveController(5, program.toRom());
        controller.insertSwimMediaStream(new CyclingStream(0xFF));

        controller.onHostTStatesElapsed(500);

        assertEquals(0xFF, controller.controllerRamByte(0x20));
        assertEquals(0x7F, controller.controllerRamByte(0x21));
        assertEquals(0xFF, controller.controllerRamByte(0x22));
        assertEquals(0x7F, controller.controllerRamByte(0x23));
        assertEquals(0xFF, controller.controllerRamByte(0x24));
        assertEquals(0xFF, controller.controllerRamByte(0x25));
        assertEquals(1, controller.selectedSide());
    }

    @Test
    void drive35StatusReflectsAbsentMediaUntilDiskIsInserted() {
        ControllerProgram program = controllerProgram();
        initializeIwmForDrive35(program);
        readDrive35Status(program, 0x0F, 0x20);
        readDrive35Status(program, 0x08, 0x21);
        Apple2SuperDriveController controller = new Apple2SuperDriveController(5, program.toRom());

        controller.onHostTStatesElapsed(300);

        assertFalse(controller.diskInserted());
        assertEquals(0x7F, controller.controllerRamByte(0x20));
        assertEquals(0xFF, controller.controllerRamByte(0x21));
    }

    @Test
    void drive35EjectControlClearsInsertedMediaState() {
        ControllerProgram program = controllerProgram();
        initializeIwmForDrive35(program);
        pulseDrive35Control(program, 0x07);
        readDrive35Status(program, 0x0F, 0x20);
        readDrive35Status(program, 0x08, 0x21);
        Apple2SuperDriveController controller = new Apple2SuperDriveController(5, program.toRom());
        controller.insertSwimMediaStream(new CyclingStream(0xFF));

        controller.onHostTStatesElapsed(500);

        assertFalse(controller.diskInserted());
        assertEquals(0x7F, controller.controllerRamByte(0x20));
        assertEquals(0xFF, controller.controllerRamByte(0x21));
    }

    @Test
    void drive35ControlFunctionsStepTrackAndTrackZeroStatusFollows() {
        ControllerProgram program = controllerProgram();
        initializeIwmForDrive35(program);
        pulseDrive35Control(program, 0x00);
        pulseDrive35Control(program, 0x01);
        readDrive35Status(program, 0x0A, 0x20);
        pulseDrive35Control(program, 0x04);
        pulseDrive35Control(program, 0x01);
        readDrive35Status(program, 0x0A, 0x21);
        Apple2SuperDriveController controller = new Apple2SuperDriveController(5, program.toRom());

        controller.onHostTStatesElapsed(700);

        assertEquals(0xFF, controller.controllerRamByte(0x20));
        assertEquals(0x7F, controller.controllerRamByte(0x21));
        assertEquals(0, controller.selectedTrack());
    }

    private static void write(Apple2SuperDriveController controller, int address, int value) {
        controller.writeC0x(new dev.z8emu.platform.bus.io.IoAccess(address, address & 0x0F, 0, 0), value);
    }

    private static ControllerProgram controllerProgram() {
        return new ControllerProgram();
    }

    private static void initializeIwmForDrive35(ControllerProgram program) {
        program.lda(0x1F)
                .bit(0x0A0D)
                .staAbs(0x0A0F)
                .bit(0x0A09);
    }

    private static void readDrive35Status(ControllerProgram program, int function, int zeroPageAddress) {
        selectDrive35Function(program, function);
        program.bit(0x0A0D)
                .ldaAbs(0x0A0E)
                .staZp(zeroPageAddress);
    }

    private static void pulseDrive35Control(ControllerProgram program, int function) {
        selectDrive35Function(program, function);
        program.bit(0x0A07)
                .bit(0x0A06);
    }

    private static void selectDrive35Function(ControllerProgram program, int function) {
        program.bit(0x0A00)
                .bit(0x0A06)
                .bit(0x0A02)
                .bit(0x0A04);
        program.bit((function & 0x08) != 0 ? 0x0A41 : 0x0A40);
        if ((function & 0x01) != 0) {
            program.bit(0x0A01);
        }
        if ((function & 0x02) != 0) {
            program.bit(0x0A03);
        }
        if ((function & 0x04) != 0) {
            program.bit(0x0A05);
        }
    }

    private static byte[] rom() {
        byte[] rom = new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE];
        rom[0] = (byte) 0xA9;
        rom[1] = (byte) 0x00;
        return rom;
    }

    private static byte[] romWritingRam(int address, int value) {
        byte[] rom = new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE];
        int offset = 0x1000;
        rom[offset] = (byte) 0xA9; // LDA #value
        rom[offset + 1] = (byte) value;
        rom[offset + 2] = (byte) 0x8D; // STA address
        rom[offset + 3] = (byte) address;
        rom[offset + 4] = (byte) (address >>> 8);
        rom[offset + 5] = (byte) 0x4C; // JMP $9005
        rom[offset + 6] = (byte) 0x05;
        rom[offset + 7] = (byte) 0x90;
        rom[0x7FFC] = (byte) 0x00;
        rom[0x7FFD] = (byte) 0x90;
        return rom;
    }

    private static void writeRomVector(byte[] rom, int vectorAddress, int target) {
        int romStart = Apple2Memory.ADDRESS_SPACE_SIZE - rom.length;
        int offset = vectorAddress - romStart;
        rom[offset] = (byte) target;
        rom[offset + 1] = (byte) (target >>> 8);
    }

    private static final class ControllerProgram {
        private static final int ROM_START = 0x8000;
        private static final int PROGRAM_OFFSET = 0x1000;
        private static final int PROGRAM_START = ROM_START + PROGRAM_OFFSET;

        private final byte[] rom = new byte[Apple2SuperDriveController.CONTROLLER_ROM_SIZE];
        private int offset = PROGRAM_OFFSET;

        private ControllerProgram lda(int value) {
            emit(0xA9, value);
            return this;
        }

        private ControllerProgram ldaAbs(int address) {
            emit(0xAD, address & 0xFF, address >>> 8);
            return this;
        }

        private ControllerProgram staAbs(int address) {
            emit(0x8D, address & 0xFF, address >>> 8);
            return this;
        }

        private ControllerProgram staZp(int address) {
            emit(0x85, address & 0xFF);
            return this;
        }

        private ControllerProgram bit(int address) {
            emit(0x2C, address & 0xFF, address >>> 8);
            return this;
        }

        private byte[] toRom() {
            int loopAddress = ROM_START + offset;
            emit(0x4C, loopAddress & 0xFF, loopAddress >>> 8);
            rom[0x7FFC] = (byte) (PROGRAM_START & 0xFF);
            rom[0x7FFD] = (byte) (PROGRAM_START >>> 8);
            return rom;
        }

        private void emit(int... bytes) {
            for (int value : bytes) {
                rom[offset++] = (byte) value;
            }
        }
    }

    private static final class CyclingStream implements Apple2SwimMediaStream {
        private final int[] bytes;
        private int offset;

        private CyclingStream(int... bytes) {
            this.bytes = bytes;
        }

        @Override
        public int nextByte() {
            int value = bytes[offset];
            offset = (offset + 1) % bytes.length;
            return value;
        }

        @Override
        public void reset() {
            offset = 0;
        }
    }
}
