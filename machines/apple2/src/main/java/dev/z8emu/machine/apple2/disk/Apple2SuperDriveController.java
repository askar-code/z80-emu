package dev.z8emu.machine.apple2.disk;

import dev.z8emu.cpu.mos6502.Mos6502Cpu;
import dev.z8emu.cpu.mos6502.Mos6502Variant;
import dev.z8emu.machine.apple2.Apple2SlotCard;
import dev.z8emu.platform.bus.CpuBus;
import dev.z8emu.platform.bus.io.IoAccess;
import java.util.Arrays;
import java.util.Objects;

/**
 * Host-visible side of the Apple II 3.5 / SuperDrive controller card.
 *
 * <p>The real card is an intelligent controller with its own 65C02, RAM, ROM,
 * and SWIM. From the Apple II bus, the host sees a bank latch in C0nX and
 * controller RAM windows in CnXX and C800-CFFF. The SWIM media path currently
 * accepts a byte-oriented GCR stream; drive mechanics are still minimal.
 */
public final class Apple2SuperDriveController implements Apple2SlotCard {
    public static final int DEFAULT_SLOT = 5;
    public static final int CONTROLLER_ROM_SIZE = 32 * 1024;
    public static final int CONTROLLER_RAM_SIZE = 32 * 1024;
    public static final int CNXX_RAM_OFFSET = 0x7B00;
    public static final int C800_BANK_SIZE = 0x0400;
    public static final int C800_FIXED_RAM_OFFSET = 0x7C00;
    private static final int CONTROLLER_ROM_START = 0x8000;
    private static final int CONTROLLER_IO_START = 0x0A00;
    private static final int CONTROLLER_IO_END_EXCLUSIVE = 0x0B00;
    private static final int HOST_TO_CONTROLLER_CYCLE_RATIO = 2;

    private final int slot;
    private final byte[] controllerRom;
    private final byte[] controllerRam = new byte[CONTROLLER_RAM_SIZE];
    private final boolean[] hostTouchedControllerRam = new boolean[CONTROLLER_RAM_SIZE];
    private final byte[] controllerIo = new byte[0x100];
    private final Apple2SwimController swimController = new Apple2SwimController();
    private final Drive35State drive35State = new Drive35State();
    private final Mos6502Cpu controllerCpu;
    private int bankSelect;
    private int selectedTrack;
    private int selectedSide;
    private boolean diagnosticLedOn;
    private boolean mediaInserted;
    private long controllerCycleCredit;
    private long controllerInstructions;
    private String controllerFault;
    private Apple2SuperDriveTraceSink traceSink = Apple2SuperDriveTraceSink.NONE;

    public Apple2SuperDriveController(int slot, byte[] controllerRom) {
        validateSlot(slot);
        Objects.requireNonNull(controllerRom, "controllerRom");
        if (controllerRom.length != CONTROLLER_ROM_SIZE) {
            throw new IllegalArgumentException("SuperDrive controller ROM must be exactly 32 KB");
        }
        this.slot = slot;
        this.controllerRom = Arrays.copyOf(controllerRom, controllerRom.length);
        swimController.setDrive35Signals(drive35State);
        this.controllerCpu = new Mos6502Cpu(new ControllerCpuBus(), Mos6502Variant.CMOS_65C02);
        reset();
    }

    public void reset() {
        bankSelect = 0;
        selectedTrack = 0;
        selectedSide = 0;
        diagnosticLedOn = false;
        drive35State.reset(mediaInserted);
        Arrays.fill(controllerIo, (byte) 0);
        Arrays.fill(hostTouchedControllerRam, false);
        swimController.reset();
        controllerCycleCredit = 0;
        controllerInstructions = 0;
        controllerFault = null;
        controllerCpu.reset();
    }

    public int slot() {
        return slot;
    }

    public int slotIndex() {
        return slot << 4;
    }

    public int bankSelect() {
        return bankSelect;
    }

    public int controllerRomByte(int address) {
        return Byte.toUnsignedInt(controllerRom[address & (CONTROLLER_ROM_SIZE - 1)]);
    }

    public int controllerRamByte(int address) {
        return Byte.toUnsignedInt(controllerRam[address & (CONTROLLER_RAM_SIZE - 1)]);
    }

    public int controllerIoByte(int offset) {
        int normalized = offset & 0xFF;
        if (normalized < 0x10) {
            return switch (normalized) {
                case 0x00 -> swimController.dataRegister();
                case 0x0C -> swimController.handshakeRegister();
                case 0x0E -> swimController.statusRegister();
                case 0x0F -> swimController.modeRegister();
                default -> Byte.toUnsignedInt(controllerIo[normalized]);
            };
        }
        return Byte.toUnsignedInt(controllerIo[normalized]);
    }

    public void insertSwimMediaStream(Apple2SwimMediaStream mediaStream) {
        swimController.insertMediaStream(mediaStream);
        mediaInserted = mediaStream != null;
        drive35State.setDiskInserted(mediaInserted);
    }

    public void insertGcr35Media(Apple2Gcr35Media media) {
        Objects.requireNonNull(media, "media");
        insertSwimMediaStream(new Apple2SwimMediaStream() {
            @Override
            public int nextByte() {
                return media.nextByte(selectedTrack, selectedSide);
            }

            @Override
            public void reset() {
                media.reset();
            }
        });
    }

    public boolean swimDataReady() {
        return swimController.dataReady();
    }

    public int selectedTrack() {
        return selectedTrack;
    }

    public int selectedSide() {
        return selectedSide;
    }

    public boolean diskInserted() {
        return drive35State.diskInserted;
    }

    public boolean diagnosticLedOn() {
        return diagnosticLedOn;
    }

    public int controllerCpuPc() {
        return controllerCpu.registers().pc();
    }

    public long controllerInstructions() {
        return controllerInstructions;
    }

    public String controllerFault() {
        return controllerFault;
    }

    public void setTraceSink(Apple2SuperDriveTraceSink traceSink) {
        this.traceSink = traceSink == null ? Apple2SuperDriveTraceSink.NONE : traceSink;
    }

    public void onHostTStatesElapsed(int hostTStates) {
        if (controllerFault != null) {
            return;
        }
        controllerCycleCredit += (long) hostTStates * HOST_TO_CONTROLLER_CYCLE_RATIO;
        try {
            while (controllerCycleCredit > 0) {
                int cycles = controllerCpu.runInstruction();
                swimController.onControllerCyclesElapsed(cycles);
                controllerCycleCredit -= cycles;
                controllerInstructions++;
            }
        } catch (RuntimeException failure) {
            controllerFault = failure.getMessage();
        }
    }

    private void ejectMedia() {
        swimController.insertMediaStream(null);
        mediaInserted = false;
        drive35State.setDiskInserted(false);
    }

    @Override
    public int readC0x(IoAccess access) {
        bankSelect = access.offset() & 0x0F;
        traceHostAccess(true, "c0x", access.address(), access.offset(), 0x00);
        return 0x00;
    }

    @Override
    public void writeC0x(IoAccess access, int value) {
        bankSelect = access.offset() & 0x0F;
        traceHostAccess(false, "c0x", access.address(), access.offset(), value);
    }

    @Override
    public int readCnxx(int offset) {
        int normalized = offset & 0xFF;
        int value = Byte.toUnsignedInt(controllerRam[CNXX_RAM_OFFSET + normalized]);
        traceHostAccess(true, "cnxx", 0xC000 | (slot << 8) | normalized, normalized, value);
        return value;
    }

    @Override
    public void writeCnxx(int offset, int value) {
        int normalized = offset & 0xFF;
        int byteValue = value & 0xFF;
        traceHostAccess(false, "cnxx", 0xC000 | (slot << 8) | normalized, normalized, byteValue);
    }

    @Override
    public boolean hasCnxxRom() {
        return true;
    }

    @Override
    public int readC800(int offset) {
        int normalized = offset & 0x07FF;
        int value = Byte.toUnsignedInt(controllerRam[c800RamAddress(normalized)]);
        traceHostAccess(true, "c800", 0xC800 + normalized, normalized, value);
        return value;
    }

    @Override
    public void writeC800(int offset, int value) {
        int normalized = offset & 0x07FF;
        int byteValue = value & 0xFF;
        int ramAddress = c800RamAddress(normalized);
        controllerRam[ramAddress] = (byte) byteValue;
        hostTouchedControllerRam[ramAddress] = true;
        traceHostAccess(false, "c800", 0xC800 + normalized, normalized, byteValue);
    }

    @Override
    public boolean usesC800ExpansionRom() {
        return true;
    }

    private int c800RamAddress(int offset) {
        int normalized = offset & 0x07FF;
        if (normalized < C800_BANK_SIZE) {
            return (bankSelect * C800_BANK_SIZE) + normalized;
        }
        return C800_FIXED_RAM_OFFSET + normalized - C800_BANK_SIZE;
    }

    private void traceHostAccess(boolean read, String region, int address, int offset, int value) {
        traceSink.traceSuperDrive(new Apple2SuperDriveTraceEvent(
                Apple2SuperDriveTraceEvent.Source.HOST,
                region,
                read,
                address,
                offset,
                controllerCpu.registers().pc(),
                controllerInstructions,
                value,
                bankSelect
        ));
    }

    private static void validateSlot(int slot) {
        if (slot < 1 || slot > 7) {
            throw new IllegalArgumentException("SuperDrive controller slot must be in range 1..7: " + slot);
        }
    }

    private final class Drive35State implements Apple2SwimController.Drive35Signals {
        private boolean stepDirectionInward;
        private boolean motorOn;
        private boolean diskInserted = true;
        private boolean diskChanged;
        private boolean mfmMode;
        private boolean tachometer;

        private void reset(boolean mediaInserted) {
            stepDirectionInward = true;
            motorOn = false;
            diskInserted = mediaInserted;
            diskChanged = true;
            mfmMode = true;
            tachometer = false;
        }

        private void setDiskInserted(boolean inserted) {
            if (diskInserted != inserted) {
                diskChanged = true;
            }
            diskInserted = inserted;
            if (!inserted) {
                motorOn = false;
            }
        }

        private void selectSide(int side) {
            selectedSide = side & 0x01;
        }

        @Override
        public int statusBit(int phases, boolean active) {
            if (!active) {
                return -1;
            }
            return switch (selectedFunction(phases)) {
                case 0x00 -> stepDirectionInward ? 0 : 1;
                case 0x01 -> 1;
                case 0x02 -> motorOn ? 0 : 1;
                case 0x03 -> diskChanged ? 0 : 1;
                case 0x04, 0x0C -> motorOn ? tachometerPulse() : 1;
                case 0x05 -> 1;
                case 0x06 -> 1;
                case 0x07 -> 0;
                case 0x08 -> diskInserted ? 0 : 1;
                case 0x09 -> 1;
                case 0x0A -> selectedTrack == 0 ? 0 : 1;
                case 0x0B -> tachometerPulse();
                case 0x0D -> mfmMode ? 1 : 0;
                case 0x0E -> diskInserted && motorOn ? 0 : 1;
                case 0x0F -> diskInserted ? 1 : 0;
                default -> 1;
            };
        }

        @Override
        public void phaseChanged(int phase, boolean high, int phases, boolean active) {
            if (!active || phase != 3 || !high) {
                return;
            }
            switch (selectedFunction(phases)) {
                case 0x00 -> stepDirectionInward = true;
                case 0x01 -> selectedTrack = Math.max(0, Math.min(79,
                        selectedTrack + (stepDirectionInward ? 1 : -1)));
                case 0x02 -> motorOn = true;
                case 0x04 -> stepDirectionInward = false;
                case 0x06 -> motorOn = false;
                case 0x07 -> {
                    ejectMedia();
                }
                case 0x09 -> mfmMode = true;
                case 0x0C -> diskChanged = true;
                case 0x0D -> mfmMode = false;
                default -> {
                }
            }
        }

        private int selectedFunction(int phases) {
            return (phases & 0x07) | (selectedSide != 0 ? 0x08 : 0x00);
        }

        private int tachometerPulse() {
            tachometer = !tachometer;
            return tachometer ? 1 : 0;
        }
    }

    private final class ControllerCpuBus implements CpuBus {
        @Override
        public int readMemory(int address) {
            int normalized = address & 0xFFFF;
            if (isControllerIo(normalized)) {
                return readControllerIo(normalized - CONTROLLER_IO_START);
            }
            if (normalized < CONTROLLER_ROM_START) {
                int value = Byte.toUnsignedInt(controllerRam[normalized]);
                traceControllerRamAccess(true, normalized, value);
                return value;
            }
            return Byte.toUnsignedInt(controllerRom[normalized - CONTROLLER_ROM_START]);
        }

        @Override
        public void writeMemory(int address, int value) {
            int normalized = address & 0xFFFF;
            if (isControllerIo(normalized)) {
                writeControllerIo(normalized - CONTROLLER_IO_START, value);
                return;
            }
            if (normalized < CONTROLLER_ROM_START) {
                int byteValue = value & 0xFF;
                controllerRam[normalized] = (byte) byteValue;
                traceControllerRamAccess(false, normalized, byteValue);
            }
        }

        private boolean isControllerIo(int address) {
            return address >= CONTROLLER_IO_START && address < CONTROLLER_IO_END_EXCLUSIVE;
        }

        private int readControllerIo(int offset) {
            int normalized = offset & 0xFF;
            int value;
            if (normalized < 0x10) {
                value = swimController.read(normalized);
            } else {
                applyAuxiliaryIoReadSideEffects(normalized);
                value = Byte.toUnsignedInt(controllerIo[normalized]);
            }
            traceControllerIo(true, normalized, value);
            return value;
        }

        private void writeControllerIo(int offset, int value) {
            int normalized = offset & 0xFF;
            int byteValue = value & 0xFF;
            if (normalized < 0x10) {
                swimController.write(normalized, byteValue);
            } else {
                controllerIo[normalized] = (byte) byteValue;
                applyAuxiliaryIoWriteSideEffects(normalized);
            }
            traceControllerIo(false, normalized, byteValue);
        }

        private void applyAuxiliaryIoReadSideEffects(int offset) {
            switch (offset) {
                case 0x40 -> drive35State.selectSide(0);
                case 0x41 -> drive35State.selectSide(1);
                case 0x80 -> diagnosticLedOn = true;
                case 0x81 -> diagnosticLedOn = false;
                default -> {
                }
            }
        }

        private void applyAuxiliaryIoWriteSideEffects(int offset) {
            switch (offset) {
                case 0x40 -> drive35State.selectSide(0);
                case 0x41 -> drive35State.selectSide(1);
                case 0x80 -> diagnosticLedOn = true;
                case 0x81 -> diagnosticLedOn = false;
                default -> {
                }
            }
        }

        private void traceControllerIo(boolean read, int offset, int value) {
            traceSink.traceSuperDrive(new Apple2SuperDriveTraceEvent(
                    Apple2SuperDriveTraceEvent.Source.CONTROLLER,
                    "controller-io",
                    read,
                    CONTROLLER_IO_START + offset,
                    offset,
                    controllerCpu.registers().pc(),
                    controllerInstructions,
                    value,
                    bankSelect
            ));
        }

        private void traceControllerRamAccess(boolean read, int address, int value) {
            if (!hostTouchedControllerRam[address & (CONTROLLER_RAM_SIZE - 1)]) {
                return;
            }
            if (read && address == 0x0000 && (value & 0xFF) == 0xFF) {
                return;
            }
            traceSink.traceSuperDrive(new Apple2SuperDriveTraceEvent(
                    Apple2SuperDriveTraceEvent.Source.CONTROLLER,
                    "controller-ram-host-touched",
                    read,
                    address,
                    address,
                    controllerCpu.registers().pc(),
                    controllerInstructions,
                    value,
                    bankSelect
            ));
        }
    }
}
