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
    private int[] dataQueue = new int[0];
    private int dataQueueHead;
    private int dataQueueCount;
    private int[] resultQueue = new int[0];
    private int resultQueueHead;
    private int resultQueueCount;
    private final Queue<SeekCompletion> seekCompletions = new ArrayDeque<>();
    private final List<Integer> commandBytes = new ArrayList<>(9);
    private int expectedCommandLength;
    private TraceSink traceSink;
    private int executingCommandByte;
    private int pendingResultCommandByte;
    private int pendingResultSt0;
    private boolean pendingResultTrace;

    public void reset() {
        motorOn = false;
        Arrays.fill(presentCylinderByDrive, 0);
        dataQueueHead = 0;
        dataQueueCount = 0;
        resultQueueHead = 0;
        resultQueueCount = 0;
        seekCompletions.clear();
        commandBytes.clear();
        expectedCommandLength = 0;
        pendingResultTrace = false;
    }

    public void insertDisk(CpcDskImage disk) {
        this.disk = Objects.requireNonNull(disk, "disk");
    }

    public void ejectDisk() {
        disk = null;
        dataQueueHead = 0;
        dataQueueCount = 0;
        resultQueueHead = 0;
        resultQueueCount = 0;
        pendingResultTrace = false;
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
        if (dataQueueCount != 0) {
            return MSR_REQUEST_FOR_MASTER | MSR_DATA_TO_CPU | MSR_EXECUTION_MODE | MSR_FDC_BUSY;
        }
        if (resultQueueCount != 0) {
            return MSR_REQUEST_FOR_MASTER | MSR_DATA_TO_CPU | MSR_FDC_BUSY;
        }
        if (expectedCommandLength != 0) {
            return MSR_REQUEST_FOR_MASTER | MSR_FDC_BUSY;
        }
        return MSR_REQUEST_FOR_MASTER;
    }

    public int readDataRegister() {
        if (dataQueueCount != 0) {
            int value = dataQueue[dataQueueHead];
            dataQueueHead++;
            if (dataQueueHead == dataQueue.length) {
                dataQueueHead = 0;
            }
            dataQueueCount--;
            if (dataQueueCount == 0) {
                dataQueueHead = 0;
                emitPendingResultTrace();
            }
            return value;
        }
        if (resultQueueCount != 0) {
            int value = resultQueue[resultQueueHead];
            resultQueueHead++;
            if (resultQueueHead == resultQueue.length) {
                resultQueueHead = 0;
            }
            resultQueueCount--;
            if (resultQueueCount == 0) {
                resultQueueHead = 0;
                expectedCommandLength = 0;
            }
            return value;
        }
        return 0xFF;
    }

    public void writeDataRegister(int value) {
        if (dataQueueCount != 0 || resultQueueCount != 0) {
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
            if (dataQueueCount == 0 && resultQueueCount == 0) {
                expectedCommandLength = 0;
            }
        }
    }

    public void setTraceSink(TraceSink traceSink) {
        this.traceSink = traceSink;
        if (traceSink == null) {
            pendingResultTrace = false;
        }
    }

    private void executeCommand() {
        executingCommandByte = commandBytes.get(0) & 0xFF;
        TraceSink sink = traceSink;
        if (sink != null) {
            int[] args = new int[commandBytes.size() - 1];
            for (int index = 1; index < commandBytes.size(); index++) {
                args[index - 1] = commandBytes.get(index) & 0xFF;
            }
            sink.command(executingCommandByte, args);
        }
        int command = executingCommandByte & 0x1F;
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
                    queueSectorResult(st0(0x00, drive, head), lastSector);
                }
                return;
            }

            lastSector = sector.get();
            queueSectorData(lastSector, transferLength(sizeCode, dataLength, lastSector));
            if (currentRecord == endOfTrack) {
                queueSectorResult(st0(0x00, drive, head), lastSector);
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
        queueSectorResult(st0(0x00, drive, head), firstSector);
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
        if (length <= 0) {
            return;
        }

        ensureDataQueueCapacity(dataQueueCount + length);
        int tail = (dataQueueHead + dataQueueCount) % dataQueue.length;
        for (int i = 0; i < copyLength; i++) {
            dataQueue[tail] = data[i] & 0xFF;
            tail++;
            if (tail == dataQueue.length) {
                tail = 0;
            }
        }
        for (int i = copyLength; i < length; i++) {
            dataQueue[tail] = 0x00;
            tail++;
            if (tail == dataQueue.length) {
                tail = 0;
            }
        }
        dataQueueCount += length;
    }

    private int transferLength(int sizeCode, int dataLength, CpcDiskSector sector) {
        if (sizeCode == 0) {
            return dataLength == 0 ? sector.dataLength() : dataLength;
        }
        return Math.min(128 << Math.min(sizeCode, 6), Math.max(sector.dataLength(), sector.declaredSize()));
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

    private void queueSectorResult(int st0, CpcDiskSector sector) {
        queueResult(
                st0,
                sector.status1(),
                sector.status2(),
                sector.track(),
                sector.side(),
                sector.sectorId(),
                sector.sizeCode()
        );
    }

    private void queueResult(int... values) {
        if (values.length == 0) {
            return;
        }

        ensureResultQueueCapacity(resultQueueCount + values.length);
        int tail = (resultQueueHead + resultQueueCount) % resultQueue.length;
        for (int value : values) {
            resultQueue[tail] = value & 0xFF;
            tail++;
            if (tail == resultQueue.length) {
                tail = 0;
            }
        }
        resultQueueCount += values.length;
        TraceSink sink = traceSink;
        if (sink != null) {
            if (dataQueueCount == 0) {
                sink.result(executingCommandByte, values[0] & 0xFF);
            } else {
                pendingResultCommandByte = executingCommandByte;
                pendingResultSt0 = values[0] & 0xFF;
                pendingResultTrace = true;
            }
        }
    }

    private void emitPendingResultTrace() {
        if (!pendingResultTrace || resultQueueCount == 0) {
            return;
        }
        pendingResultTrace = false;
        TraceSink sink = traceSink;
        if (sink != null) {
            sink.result(pendingResultCommandByte, pendingResultSt0);
        }
    }

    private void ensureDataQueueCapacity(int requiredCapacity) {
        if (requiredCapacity <= dataQueue.length) {
            return;
        }

        int oldCapacity = dataQueue.length;
        int newCapacity = Math.max(requiredCapacity, Math.max(16, oldCapacity * 2));
        int[] grown = Arrays.copyOf(dataQueue, newCapacity);
        if (dataQueueCount > 0 && dataQueueHead + dataQueueCount > oldCapacity) {
            int wrappedCount = dataQueueHead + dataQueueCount - oldCapacity;
            System.arraycopy(grown, 0, grown, oldCapacity, wrappedCount);
        }
        dataQueue = grown;
    }

    private void ensureResultQueueCapacity(int requiredCapacity) {
        if (requiredCapacity <= resultQueue.length) {
            return;
        }

        int oldCapacity = resultQueue.length;
        int newCapacity = Math.max(requiredCapacity, Math.max(8, oldCapacity * 2));
        int[] grown = Arrays.copyOf(resultQueue, newCapacity);
        if (resultQueueCount > 0 && resultQueueHead + resultQueueCount > oldCapacity) {
            int wrappedCount = resultQueueHead + resultQueueCount - oldCapacity;
            System.arraycopy(grown, 0, grown, oldCapacity, wrappedCount);
        }
        resultQueue = grown;
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

    public interface TraceSink {
        void command(int commandByte, int[] args);

        void result(int commandByte, int st0);
    }
}
