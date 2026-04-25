package dev.z8emu.machine.cpc.device;

import dev.z8emu.machine.cpc.disk.CpcDiskSector;
import dev.z8emu.machine.cpc.disk.CpcDiskTrack;
import dev.z8emu.machine.cpc.disk.CpcDskImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;

public final class CpcFdcDevice {
    private static final int MSR_FDC_BUSY = 0x10;
    private static final int MSR_EXECUTION_MODE = 0x20;
    private static final int MSR_DATA_TO_CPU = 0x40;
    private static final int MSR_REQUEST_FOR_MASTER = 0x80;

    private static final int ST0_NORMAL_SEEK_END = 0x20;
    private static final int ST0_ABNORMAL_TERMINATION = 0x40;
    private static final int ST0_INVALID_COMMAND = 0x80;
    private static final int ST0_NOT_READY = 0x08;
    private static final int ST1_NO_DATA = 0x04;
    private static final int ST1_MISSING_ADDRESS_MARK = 0x01;
    private static final int ST3_READY = 0x20;
    private static final int ST3_TRACK_ZERO = 0x10;
    private static final int ST3_TWO_SIDE = 0x08;

    private CpcDskImage disk;
    private boolean motorOn;
    private final int[] presentCylinderByDrive = new int[4];
    private final Queue<Integer> dataQueue = new ArrayDeque<>();
    private final Queue<Integer> resultQueue = new ArrayDeque<>();
    private final Queue<SeekCompletion> seekCompletions = new ArrayDeque<>();
    private final List<Integer> commandBytes = new ArrayList<>(9);
    private int expectedCommandLength;

    public void reset() {
        motorOn = false;
        Arrays.fill(presentCylinderByDrive, 0);
        dataQueue.clear();
        resultQueue.clear();
        seekCompletions.clear();
        commandBytes.clear();
        expectedCommandLength = 0;
    }

    public void insertDisk(CpcDskImage disk) {
        this.disk = Objects.requireNonNull(disk, "disk");
    }

    public void ejectDisk() {
        disk = null;
        dataQueue.clear();
        resultQueue.clear();
    }

    public boolean diskPresent() {
        return disk != null;
    }

    public void writeMotorControl(int value) {
        motorOn = (value & 0x01) != 0;
    }

    public boolean motorOn() {
        return motorOn;
    }

    public int readMainStatusRegister() {
        if (!dataQueue.isEmpty()) {
            return MSR_REQUEST_FOR_MASTER | MSR_DATA_TO_CPU | MSR_EXECUTION_MODE | MSR_FDC_BUSY;
        }
        if (!resultQueue.isEmpty()) {
            return MSR_REQUEST_FOR_MASTER | MSR_DATA_TO_CPU | MSR_FDC_BUSY;
        }
        if (expectedCommandLength != 0) {
            return MSR_REQUEST_FOR_MASTER | MSR_FDC_BUSY;
        }
        return MSR_REQUEST_FOR_MASTER;
    }

    public int readDataRegister() {
        if (!dataQueue.isEmpty()) {
            return dataQueue.remove();
        }
        if (!resultQueue.isEmpty()) {
            int value = resultQueue.remove();
            if (resultQueue.isEmpty()) {
                expectedCommandLength = 0;
            }
            return value;
        }
        return 0xFF;
    }

    public void writeDataRegister(int value) {
        if (!dataQueue.isEmpty() || !resultQueue.isEmpty()) {
            return;
        }

        int normalized = value & 0xFF;
        if (expectedCommandLength == 0) {
            commandBytes.clear();
            expectedCommandLength = commandLength(normalized);
        }
        commandBytes.add(normalized);

        if (commandBytes.size() == expectedCommandLength) {
            executeCommand();
            commandBytes.clear();
            if (dataQueue.isEmpty() && resultQueue.isEmpty()) {
                expectedCommandLength = 0;
            }
        }
    }

    private void executeCommand() {
        int command = commandBytes.get(0) & 0x1F;
        switch (command) {
            case 0x03 -> {
                // Specify only programs timing/non-DMA flags. The current model is polling-only.
            }
            case 0x04 -> executeSenseDriveStatus();
            case 0x06 -> executeReadData();
            case 0x07 -> executeRecalibrate();
            case 0x08 -> executeSenseInterruptStatus();
            case 0x0A -> executeReadId();
            case 0x0F -> executeSeek();
            default -> queueResult(ST0_INVALID_COMMAND);
        }
    }

    private void executeReadData() {
        int drive = driveNumber(commandBytes.get(1));
        int head = headNumber(commandBytes.get(1));
        int cylinder = commandBytes.get(2) & 0xFF;
        int idHead = commandBytes.get(3) & 0xFF;
        int record = commandBytes.get(4) & 0xFF;
        int sizeCode = commandBytes.get(5) & 0xFF;
        int endOfTrack = commandBytes.get(6) & 0xFF;
        int dataLength = commandBytes.get(8) & 0xFF;

        if (!driveReady(drive)) {
            queueResult(st0(ST0_ABNORMAL_TERMINATION | ST0_NOT_READY, drive, head), ST1_NO_DATA, 0x00,
                    cylinder, idHead, record, sizeCode);
            return;
        }

        int currentRecord = record;
        CpcDiskSector lastSector = null;
        while (true) {
            Optional<CpcDiskSector> sector = disk.findSector(
                    presentCylinderByDrive[drive],
                    head,
                    cylinder,
                    idHead,
                    currentRecord,
                    sizeCode
            );
            if (sector.isEmpty()) {
                if (lastSector == null) {
                    queueResult(st0(ST0_ABNORMAL_TERMINATION, drive, head),
                            ST1_NO_DATA | ST1_MISSING_ADDRESS_MARK,
                            0x00,
                            cylinder,
                            idHead,
                            currentRecord,
                            sizeCode);
                } else {
                    queueResult(st0(0x00, drive, head),
                            lastSector.status1(),
                            lastSector.status2(),
                            lastSector.track(),
                            lastSector.side(),
                            lastSector.sectorId(),
                            lastSector.sizeCode());
                }
                return;
            }

            lastSector = sector.get();
            queueSectorData(lastSector, transferLength(sizeCode, dataLength, lastSector));
            if (currentRecord == endOfTrack) {
                queueResult(st0(0x00, drive, head),
                        lastSector.status1(),
                        lastSector.status2(),
                        lastSector.track(),
                        lastSector.side(),
                        lastSector.sectorId(),
                        lastSector.sizeCode());
                return;
            }
            currentRecord = (currentRecord + 1) & 0xFF;
        }
    }

    private void executeReadId() {
        int drive = driveNumber(commandBytes.get(1));
        int head = headNumber(commandBytes.get(1));
        if (!driveReady(drive)) {
            queueResult(st0(ST0_ABNORMAL_TERMINATION | ST0_NOT_READY, drive, head), ST1_NO_DATA, 0x00, 0, head, 0, 2);
            return;
        }

        Optional<CpcDiskSector> sector = disk.track(presentCylinderByDrive[drive], head)
                .flatMap(CpcDiskTrack::firstSector);
        if (sector.isEmpty()) {
            queueResult(st0(ST0_ABNORMAL_TERMINATION, drive, head),
                    ST1_NO_DATA | ST1_MISSING_ADDRESS_MARK,
                    0x00,
                    presentCylinderByDrive[drive],
                    head,
                    0,
                    2);
            return;
        }

        CpcDiskSector firstSector = sector.get();
        queueResult(st0(0x00, drive, head),
                firstSector.status1(),
                firstSector.status2(),
                firstSector.track(),
                firstSector.side(),
                firstSector.sectorId(),
                firstSector.sizeCode());
    }

    private void executeRecalibrate() {
        int drive = driveNumber(commandBytes.get(1));
        presentCylinderByDrive[drive] = 0;
        seekCompletions.add(new SeekCompletion(st0(ST0_NORMAL_SEEK_END, drive, 0), 0));
    }

    private void executeSeek() {
        int drive = driveNumber(commandBytes.get(1));
        int head = headNumber(commandBytes.get(1));
        int newCylinder = commandBytes.get(2) & 0xFF;
        presentCylinderByDrive[drive] = newCylinder;
        seekCompletions.add(new SeekCompletion(st0(ST0_NORMAL_SEEK_END, drive, head), newCylinder));
    }

    private void executeSenseInterruptStatus() {
        SeekCompletion completion = seekCompletions.poll();
        if (completion == null) {
            queueResult(ST0_INVALID_COMMAND, 0x00);
        } else {
            queueResult(completion.status0(), completion.presentCylinder());
        }
    }

    private void executeSenseDriveStatus() {
        int drive = driveNumber(commandBytes.get(1));
        int head = headNumber(commandBytes.get(1));
        int status = drive | (head << 2);
        if (driveReady(drive)) {
            status |= ST3_READY;
        }
        if (presentCylinderByDrive[drive] == 0) {
            status |= ST3_TRACK_ZERO;
        }
        if (disk != null && disk.sideCount() > 1) {
            status |= ST3_TWO_SIDE;
        }
        queueResult(status);
    }

    private void queueSectorData(CpcDiskSector sector, int length) {
        byte[] data = sector.data();
        int copyLength = Math.min(length, data.length);
        for (int i = 0; i < copyLength; i++) {
            dataQueue.add(data[i] & 0xFF);
        }
        for (int i = copyLength; i < length; i++) {
            dataQueue.add(0x00);
        }
    }

    private int transferLength(int sizeCode, int dataLength, CpcDiskSector sector) {
        if (sizeCode == 0) {
            return dataLength == 0 ? sector.data().length : dataLength;
        }
        return Math.min(128 << Math.min(sizeCode, 6), Math.max(sector.data().length, sector.declaredSize()));
    }

    private boolean driveReady(int drive) {
        return drive < 2 && disk != null && motorOn;
    }

    private static int commandLength(int commandByte) {
        return switch (commandByte & 0x1F) {
            case 0x03 -> 3; // Specify
            case 0x04 -> 2; // Sense Drive Status
            case 0x05, 0x06, 0x0C -> 9; // Write Data, Read Data, Read Deleted Data
            case 0x07 -> 2; // Recalibrate
            case 0x08 -> 1; // Sense Interrupt Status
            case 0x0A -> 2; // Read ID
            case 0x0F -> 3; // Seek
            default -> 1;
        };
    }

    private void queueResult(int... values) {
        for (int value : values) {
            resultQueue.add(value & 0xFF);
        }
    }

    private static int driveNumber(int driveAndHead) {
        return driveAndHead & 0x03;
    }

    private static int headNumber(int driveAndHead) {
        return (driveAndHead >>> 2) & 0x01;
    }

    private static int st0(int base, int drive, int head) {
        return (base | (head << 2) | drive) & 0xFF;
    }

    private record SeekCompletion(int status0, int presentCylinder) {
    }
}
