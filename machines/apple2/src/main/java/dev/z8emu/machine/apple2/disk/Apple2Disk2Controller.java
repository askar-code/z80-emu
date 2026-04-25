package dev.z8emu.machine.apple2.disk;

import dev.z8emu.platform.time.TStateCounter;
import java.util.Arrays;
import java.util.Objects;

public final class Apple2Disk2Controller {
    private static final int RAW_BYTE_TSTATES = 32;
    private static final long MOTOR_COAST_TSTATES = 1_000_000L;
    private static final int EMPTY_SLOT_ROM_BYTE = 0xFF;

    public static final int SLOT = 6;
    public static final int SLOT_INDEX = SLOT << 4;
    public static final int SLOT_ROM_START = 0xC000 | (SLOT << 8);
    public static final int SLOT_ROM_SIZE = 0x100;
    public static final int SLOT_ROM_END_EXCLUSIVE = SLOT_ROM_START + 0x100;

    private static final int SLOT_IO_START = 0xC080 | SLOT_INDEX;
    private static final int SLOT_IO_END_EXCLUSIVE = SLOT_IO_START + 0x10;

    private final TStateCounter clock;
    private Apple2DosDiskImage diskImage;
    private byte[] slotRom;
    private final byte[][] trackStreams = new byte[Apple2DosDiskImage.TRACK_COUNT][];
    private final int[] trackPositions = new int[Apple2DosDiskImage.TRACK_COUNT];
    private boolean motorOn;
    private boolean drive1Selected;
    private boolean q6;
    private boolean q7;
    private int activePhase;
    private int halfTrack;
    private int latch;
    private long nextRawByteTState;
    private long motorCoastUntilTState;

    public Apple2Disk2Controller(TStateCounter clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        reset();
    }

    public void reset() {
        motorOn = false;
        drive1Selected = true;
        q6 = false;
        q7 = false;
        activePhase = 0;
        halfTrack = 0;
        latch = 0x00;
        nextRawByteTState = 0;
        motorCoastUntilTState = 0;
        Arrays.fill(trackPositions, 0);
    }

    public void loadSlotRom(byte[] slotRom) {
        Objects.requireNonNull(slotRom, "slotRom");
        if (slotRom.length != SLOT_ROM_SIZE) {
            throw new IllegalArgumentException("Disk II slot ROM must be exactly 256 bytes");
        }
        this.slotRom = Arrays.copyOf(slotRom, slotRom.length);
    }

    public void insertDisk(Apple2DosDiskImage diskImage) {
        this.diskImage = Objects.requireNonNull(diskImage, "diskImage");
        Arrays.fill(trackStreams, null);
        Arrays.fill(trackPositions, 0);
        halfTrack = 0;
        nextRawByteTState = clock.value();
    }

    public boolean hasDisk() {
        return diskImage != null;
    }

    public boolean handlesIo(int address) {
        int normalized = address & 0xFFFF;
        return normalized >= SLOT_IO_START && normalized < SLOT_IO_END_EXCLUSIVE;
    }

    public boolean handlesSlotRom(int address) {
        int normalized = address & 0xFFFF;
        return normalized >= SLOT_ROM_START && normalized < SLOT_ROM_END_EXCLUSIVE;
    }

    public int readSlotRom(int address) {
        if (slotRom == null) {
            return EMPTY_SLOT_ROM_BYTE;
        }
        return Byte.toUnsignedInt(slotRom[(address - SLOT_ROM_START) & 0xFF]);
    }

    public int readIo(int address) {
        return accessSwitch(address & 0xFFFF, true);
    }

    public void writeIo(int address, int value) {
        accessSwitch(address & 0xFFFF, false);
    }

    public int currentTrack() {
        return Math.min(Apple2DosDiskImage.TRACK_COUNT - 1, (halfTrack + 1) / 2);
    }

    private int accessSwitch(int address, boolean read) {
        int offset = address & 0x0F;
        switch (offset) {
            case 0x0, 0x2, 0x4, 0x6 -> {
                return latch;
            }
            case 0x1, 0x3, 0x5, 0x7 -> {
                turnPhaseOn(offset >>> 1);
                return latch;
            }
            case 0x8 -> {
                turnMotorOff();
                return latch;
            }
            case 0x9 -> {
                turnMotorOn();
                return latch;
            }
            case 0xA -> {
                drive1Selected = true;
                return latch;
            }
            case 0xB -> {
                drive1Selected = false;
                return latch;
            }
            case 0xC -> {
                q6 = false;
                return read ? readDataLatch() : latch;
            }
            case 0xD -> {
                q6 = true;
                return read ? readWriteProtectSense() : latch;
            }
            case 0xE -> {
                q7 = false;
                return latch;
            }
            case 0xF -> {
                q7 = true;
                return latch;
            }
            default -> throw new IllegalStateException("Unexpected Disk II switch offset: " + offset);
        }
    }

    private void turnMotorOn() {
        boolean wasSpinning = isMediaSpinning();
        if (!wasSpinning) {
            nextRawByteTState = clock.value();
        }
        motorOn = true;
        motorCoastUntilTState = 0;
    }

    private void turnMotorOff() {
        if (motorOn) {
            motorCoastUntilTState = clock.value() + MOTOR_COAST_TSTATES;
        }
        motorOn = false;
    }

    private boolean isMediaSpinning() {
        return motorOn || clock.value() < motorCoastUntilTState;
    }

    private void turnPhaseOn(int phase) {
        int delta = (phase - activePhase) & 0x03;
        if (delta == 1) {
            halfTrack++;
        } else if (delta == 3) {
            halfTrack--;
        }
        if (halfTrack < 0) {
            halfTrack = 0;
        }
        int maxHalfTrack = (Apple2DosDiskImage.TRACK_COUNT - 1) * 2;
        if (halfTrack > maxHalfTrack) {
            halfTrack = maxHalfTrack;
        }
        activePhase = phase;
    }

    private int readDataLatch() {
        if (diskImage == null || !isMediaSpinning() || !drive1Selected || q6 || q7) {
            latch = 0x00;
            return latch;
        }

        long now = clock.value();
        if (now < nextRawByteTState) {
            return latch & 0x7F;
        }

        int track = currentTrack();
        byte[] stream = trackStream(track);
        int position = trackPositions[track];
        long rawBytesReady = ((now - nextRawByteTState) / RAW_BYTE_TSTATES) + 1;
        int advance = (int) (rawBytesReady % stream.length);
        int latchOffset = (int) ((rawBytesReady - 1) % stream.length);
        int latchPosition = (position + latchOffset) % stream.length;
        latch = Byte.toUnsignedInt(stream[latchPosition]);
        trackPositions[track] = (position + advance) % stream.length;
        nextRawByteTState += rawBytesReady * RAW_BYTE_TSTATES;
        return latch;
    }

    private int readWriteProtectSense() {
        if (diskImage == null || q7) {
            latch = 0x00;
        } else {
            latch = 0x80;
        }
        return latch;
    }

    private byte[] trackStream(int track) {
        byte[] stream = trackStreams[track];
        if (stream == null) {
            stream = Apple2DiskNibblizer.buildTrack(diskImage, track);
            trackStreams[track] = stream;
        }
        return stream;
    }

    int currentTrackPosition() {
        return trackPositions[currentTrack()];
    }
}
