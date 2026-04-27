package dev.z8emu.machine.apple2.disk;

import dev.z8emu.machine.apple2.Apple2SlotCard;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Arrays;
import java.util.Objects;

public final class Apple2Disk2Controller implements Apple2SlotCard {
    private static final int RAW_BYTE_TSTATES = 32;
    private static final long MOTOR_COAST_TSTATES = 1_000_000L;
    private static final int EMPTY_SLOT_ROM_BYTE = 0xFF;

    public static final int SLOT = 6;
    public static final int SLOT_INDEX = SLOT << 4;
    public static final int SLOT_ROM_START = 0xC000 | (SLOT << 8);
    public static final int SLOT_ROM_SIZE = 0x100;
    public static final int SLOT_ROM_END_EXCLUSIVE = SLOT_ROM_START + 0x100;

    private final TStateCounter clock;
    private Apple2DosDiskImage diskImage;
    private Apple2WozDiskImage wozImage;
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
    private Apple2Disk2TraceSink traceSink = Apple2Disk2TraceSink.NONE;

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

    public void setTraceSink(Apple2Disk2TraceSink traceSink) {
        this.traceSink = traceSink == null ? Apple2Disk2TraceSink.NONE : traceSink;
    }

    public void insertDisk(Apple2DosDiskImage diskImage) {
        this.diskImage = Objects.requireNonNull(diskImage, "diskImage");
        this.wozImage = null;
        resetDiskStream();
    }

    public void insertDisk(Apple2WozDiskImage wozImage) {
        this.diskImage = null;
        this.wozImage = Objects.requireNonNull(wozImage, "wozImage");
        resetDiskStream();
    }

    private void resetDiskStream() {
        Arrays.fill(trackStreams, null);
        Arrays.fill(trackPositions, 0);
        halfTrack = 0;
        nextRawByteTState = clock.value();
    }

    public boolean hasDisk() {
        return diskImage != null || wozImage != null;
    }

    @Override
    public int readCnxx(int offset) {
        if (slotRom == null) {
            return EMPTY_SLOT_ROM_BYTE;
        }
        return Byte.toUnsignedInt(slotRom[offset & 0xFF]);
    }

    @Override
    public boolean hasCnxxRom() {
        return slotRom != null;
    }

    @Override
    public int readC0x(IoAccess access) {
        return accessSwitch(access, true);
    }

    @Override
    public void writeC0x(IoAccess access, int value) {
        accessSwitch(access, false);
    }

    public int currentTrack() {
        return Math.min(Apple2DosDiskImage.TRACK_COUNT - 1, (halfTrack + 1) / 2);
    }

    private int accessSwitch(IoAccess access, boolean read) {
        int offset = access.offset() & 0x0F;
        int value;
        switch (offset) {
            case 0x0, 0x2, 0x4, 0x6 -> {
                value = latch;
            }
            case 0x1, 0x3, 0x5, 0x7 -> {
                turnPhaseOn(offset >>> 1);
                value = latch;
            }
            case 0x8 -> {
                turnMotorOff();
                value = latch;
            }
            case 0x9 -> {
                turnMotorOn();
                value = latch;
            }
            case 0xA -> {
                drive1Selected = true;
                value = latch;
            }
            case 0xB -> {
                drive1Selected = false;
                value = latch;
            }
            case 0xC -> {
                q6 = false;
                value = read ? readDataLatch() : latch;
            }
            case 0xD -> {
                q6 = true;
                value = read ? readWriteProtectSense() : latch;
            }
            case 0xE -> {
                q7 = false;
                value = latch;
            }
            case 0xF -> {
                q7 = true;
                value = latch;
            }
            default -> throw new IllegalStateException("Unexpected Disk II switch offset: " + offset);
        }
        trace(access, read, value);
        return value;
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
        if (!hasDisk() || !isMediaSpinning() || !drive1Selected || q6 || q7) {
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
        if (!hasDisk() || q7) {
            latch = 0x00;
        } else {
            latch = isWriteProtected() ? 0x80 : 0x00;
        }
        return latch;
    }

    private boolean isWriteProtected() {
        return diskImage != null || wozImage.writeProtected();
    }

    private byte[] trackStream(int track) {
        byte[] stream = trackStreams[track];
        if (stream == null) {
            stream = diskImage != null
                    ? Apple2DiskNibblizer.buildTrack(diskImage, track)
                    : wozImage.trackStream(track);
            trackStreams[track] = stream;
        }
        return stream;
    }

    int currentTrackPosition() {
        return trackPositions[currentTrack()];
    }

    private void trace(IoAccess access, boolean read, int value) {
        if (traceSink == Apple2Disk2TraceSink.NONE) {
            return;
        }
        int track = currentTrack();
        int trackPosition = hasDisk() ? trackPositions[track] : 0;
        traceSink.traceDisk2(new Apple2Disk2TraceEvent(
                switchName(access.offset()),
                read,
                access.address(),
                access.offset() & 0x0F,
                access.tState(),
                value & 0xFF,
                track,
                halfTrack,
                trackPosition,
                motorOn,
                isMediaSpinning(),
                drive1Selected,
                q6,
                q7
        ));
    }

    private static String switchName(int offset) {
        return switch (offset & 0x0F) {
            case 0x0 -> "phase0-off";
            case 0x1 -> "phase0-on";
            case 0x2 -> "phase1-off";
            case 0x3 -> "phase1-on";
            case 0x4 -> "phase2-off";
            case 0x5 -> "phase2-on";
            case 0x6 -> "phase3-off";
            case 0x7 -> "phase3-on";
            case 0x8 -> "motor-off";
            case 0x9 -> "motor-on";
            case 0xA -> "drive1";
            case 0xB -> "drive2";
            case 0xC -> "q6-low-read-latch";
            case 0xD -> "q6-high-write-protect";
            case 0xE -> "q7-low";
            case 0xF -> "q7-high";
            default -> throw new IllegalStateException("Unexpected Disk II switch offset");
        };
    }
}
