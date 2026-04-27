package dev.z8emu.machine.apple2.disk;

/**
 * Minimal SWIM/IWM register core used by the Apple II 3.5 / SuperDrive card.
 *
 * <p>This models the register-selection behavior needed by the card firmware
 * self-test plus a byte-oriented incoming media stream. It is not yet a
 * bit-cell-accurate 3.5-inch drive/SWIM model.
 */
final class Apple2SwimController {
    interface Drive35Signals {
        Drive35Signals NONE = new Drive35Signals() {
            @Override
            public int statusBit(int phases, boolean active) {
                return -1;
            }

            @Override
            public void phaseChanged(int phase, boolean high, int phases, boolean active) {
            }
        };

        int statusBit(int phases, boolean active);

        void phaseChanged(int phase, boolean high, int phases, boolean active);
    }

    private static final int ISM_MODE = 0x40;
    private static final int RAW_BYTE_CONTROLLER_CYCLES = 32;
    private static final int[] SELF_TEST_SHIFT_PATTERN = {
            0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80,
            0xFE, 0xFD, 0xFB, 0xF7, 0xEF, 0xDF, 0xBF, 0x7F
    };

    private final int[] ismParameters = new int[16];
    private Apple2SwimMediaStream mediaStream;
    private int readDataRegister;
    // Writes program the disk-control side; they must not become incoming media bytes.
    private int writeDataRegister;
    private int modeRegister;
    private int ismModeRegister;
    private int ismSetupRegister;
    private int ismPhasesRegister;
    private int ismParameterIndex;
    private int iwmToIsmCounter;
    private int shiftPatternIndex;
    private int mediaCycleCredit;
    private int phaseRegister;
    private Drive35Signals drive35Signals = Drive35Signals.NONE;
    private boolean modeInitialized;
    private boolean dataReady;
    private boolean active;
    private boolean drive2Selected;
    private boolean q6;
    private boolean q7;

    void reset() {
        readDataRegister = 0xFF;
        writeDataRegister = 0xFF;
        modeRegister = 0x1F;
        ismModeRegister = 0x00;
        ismSetupRegister = 0x00;
        ismPhasesRegister = 0xF0;
        ismParameterIndex = 0;
        iwmToIsmCounter = 0;
        shiftPatternIndex = 0;
        mediaCycleCredit = 0;
        phaseRegister = 0;
        modeInitialized = false;
        dataReady = false;
        active = false;
        drive2Selected = false;
        q6 = false;
        q7 = false;
        for (int i = 0; i < ismParameters.length; i++) {
            ismParameters[i] = 0;
        }
        if (mediaStream != null) {
            mediaStream.reset();
        }
    }

    void setDrive35Signals(Drive35Signals drive35Signals) {
        this.drive35Signals = drive35Signals == null ? Drive35Signals.NONE : drive35Signals;
    }

    void insertMediaStream(Apple2SwimMediaStream mediaStream) {
        this.mediaStream = mediaStream;
        if (mediaStream != null) {
            mediaStream.reset();
        }
        readDataRegister = 0xFF;
        mediaCycleCredit = 0;
        dataReady = false;
    }

    void onControllerCyclesElapsed(int cycles) {
        if (mediaStream == null || dataReady || cycles <= 0 || !mediaStreamReadable()) {
            return;
        }
        mediaCycleCredit += cycles;
        if (mediaCycleCredit < RAW_BYTE_CONTROLLER_CYCLES) {
            return;
        }
        mediaCycleCredit -= RAW_BYTE_CONTROLLER_CYCLES;
        readDataRegister = mediaStream.nextByte() & 0xFF;
        dataReady = true;
    }

    int dataRegister() {
        return readDataRegister;
    }

    boolean dataReady() {
        return dataReady;
    }

    int modeRegister() {
        if (ismModeActive()) {
            return ismModeRegister;
        }
        return modeRegister;
    }

    int read(int offset) {
        int normalized = offset & 0x0F;
        if (ismModeActive()) {
            return readIsmRegister(normalized & 0x07);
        }
        updateSwitch(normalized);
        if (normalized == 0x0B) {
            return readShiftPattern();
        }
        if (normalized == 0x0C) {
            if (mediaStream != null) {
                return dataReady ? readDataRegister() : 0x30;
            }
            return handshakeRegister();
        }
        if (normalized == 0x0E) {
            return readStatusRegister();
        }
        if (!q6 && !q7) {
            return readDataRegister();
        }
        if (q6 && !q7) {
            return iwmStatusRegister();
        }
        if (!q6) {
            return handshakeRegister();
        }
        return modeRegister;
    }

    void write(int offset, int value) {
        int normalizedOffset = offset & 0x0F;
        int normalized = value & 0xFF;
        if (normalizedOffset == 0x06 && normalized == 0xF8) {
            resetIwmSelfTestState();
            return;
        }
        if (ismModeActive()) {
            writeIsmRegister(normalizedOffset & 0x07, normalized);
            return;
        }
        updateSwitch(normalizedOffset);
        if (normalizedOffset == 0x06) {
            shiftPatternIndex = 0;
            return;
        }
        if (!q6 && !q7) {
            writeDataRegister = normalized;
        } else if (q6 && q7) {
            if (iwmFastModeActive() && normalizedOffset == 0x0D) {
                writeDataRegister = normalized;
                dataReady = false;
                mediaCycleCredit = 0;
            } else {
                modeRegister = normalized & 0x1F;
                modeInitialized = true;
            }
        }
        updateIwmToIsmSwitch(normalizedOffset, normalized);
    }

    private void resetIwmSelfTestState() {
        shiftPatternIndex = 0;
        readDataRegister = 0xFF;
        writeDataRegister = 0xFF;
        modeRegister = 0x1F;
        ismModeRegister = 0x00;
        ismSetupRegister = 0x00;
        ismPhasesRegister = 0xF0;
        ismParameterIndex = 0;
        iwmToIsmCounter = 0;
        modeInitialized = false;
        mediaCycleCredit = 0;
        phaseRegister = 0;
        dataReady = false;
        active = false;
        drive2Selected = false;
        q6 = false;
        q7 = false;
        for (int i = 0; i < ismParameters.length; i++) {
            ismParameters[i] = 0;
        }
    }

    int statusRegister() {
        if (ismModeActive()) {
            return ismModeRegister;
        }
        if (!modeInitialized) {
            return 0xFF;
        }
        return iwmStatusRegister();
    }

    private int readStatusRegister() {
        if (!modeInitialized) {
            return 0xFF;
        }
        return iwmStatusRegister();
    }

    private int iwmStatusRegister() {
        int externalStatusBit = drive35Signals.statusBit(phaseRegister, active);
        int readyOrSense = externalStatusBit >= 0
                ? ((externalStatusBit & 0x01) << 7)
                : (dataReady ? 0x80 : 0x00);
        return readyOrSense | 0x40 | (active ? 0x20 : 0x00) | modeRegister;
    }

    int handshakeRegister() {
        if (ismModeActive()) {
            return ismHandshakeRegister();
        }
        if (modeInitialized && modeRegister == 0x08) {
            return 0xB0;
        }
        return 0xF0;
    }

    private boolean iwmFastModeActive() {
        return modeInitialized && modeRegister == 0x08;
    }

    private boolean ismModeActive() {
        return (ismModeRegister & ISM_MODE) != 0;
    }

    private boolean mediaStreamReadable() {
        if (!ismModeActive()) {
            return true;
        }
        return (ismModeRegister & 0x08) != 0;
    }

    private int readIsmRegister(int offset) {
        return switch (offset & 0x07) {
            case 0x0, 0x1 -> readDataRegister();
            case 0x2 -> 0x00;
            case 0x3 -> readIsmParameter();
            case 0x4 -> ismPhasesRegister;
            case 0x5 -> ismSetupRegister;
            case 0x6 -> ismModeRegister;
            case 0x7 -> ismHandshakeRegister();
            default -> throw new IllegalStateException("Unexpected SWIM ISM register offset");
        };
    }

    private void writeIsmRegister(int offset, int value) {
        int normalized = value & 0xFF;
        switch (offset & 0x07) {
            case 0x0, 0x1 -> writeDataRegister = normalized;
            case 0x2 -> {
            }
            case 0x3 -> writeIsmParameter(normalized);
            case 0x4 -> ismPhasesRegister = normalized;
            case 0x5 -> ismSetupRegister = normalized;
            case 0x6 -> {
                ismModeRegister &= ~normalized;
                ismParameterIndex = 0;
            }
            case 0x7 -> ismModeRegister |= normalized;
            default -> throw new IllegalStateException("Unexpected SWIM ISM register offset");
        }
        if ((ismModeRegister & 0x01) != 0) {
            dataReady = false;
            ismParameterIndex = 0;
        }
        if (!mediaStreamReadable()) {
            dataReady = false;
            mediaCycleCredit = 0;
        }
    }

    private int readIsmParameter() {
        int value = ismParameters[ismParameterIndex] & 0xFF;
        ismParameterIndex = (ismParameterIndex + 1) & 0x0F;
        return value;
    }

    private void writeIsmParameter(int value) {
        ismParameters[ismParameterIndex] = value & 0xFF;
        ismParameterIndex = (ismParameterIndex + 1) & 0x0F;
    }

    private int ismHandshakeRegister() {
        int handshake = 0x0C;
        if (dataReady) {
            handshake |= 0x80;
        }
        if ((ismModeRegister & 0x10) != 0 && !dataReady) {
            handshake |= 0xC0;
        }
        return handshake;
    }

    private void updateIwmToIsmSwitch(int offset, int value) {
        if ((offset & 0x0F) != 0x0F) {
            iwmToIsmCounter = 0;
            return;
        }

        boolean bitSixSet = (value & ISM_MODE) != 0;
        switch (iwmToIsmCounter) {
            case 0 -> iwmToIsmCounter = bitSixSet ? 1 : 0;
            case 1 -> iwmToIsmCounter = bitSixSet ? 0 : 2;
            case 2 -> iwmToIsmCounter = bitSixSet ? 3 : 0;
            case 3 -> {
                if (bitSixSet) {
                    ismModeRegister |= ISM_MODE;
                }
                iwmToIsmCounter = 0;
            }
            default -> iwmToIsmCounter = 0;
        }
    }

    private int readShiftPattern() {
        if (shiftPatternIndex >= SELF_TEST_SHIFT_PATTERN.length) {
            return 0xFF;
        }
        return SELF_TEST_SHIFT_PATTERN[shiftPatternIndex++] & 0xFF;
    }

    private int readDataRegister() {
        int value = readDataRegister;
        dataReady = false;
        return value;
    }

    private void updateSwitch(int offset) {
        switch (offset & 0x0F) {
            case 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7 -> updatePhaseSwitch(offset & 0x07);
            case 0x8 -> active = false;
            case 0x9 -> active = true;
            case 0xA -> drive2Selected = false;
            case 0xB -> drive2Selected = true;
            case 0xC -> q6 = false;
            case 0xD -> q6 = true;
            case 0xE -> q7 = false;
            case 0xF -> q7 = true;
            default -> throw new IllegalStateException("Unexpected SWIM register offset");
        }
    }

    private void updatePhaseSwitch(int offset) {
        int phase = (offset >>> 1) & 0x03;
        if ((offset & 0x01) != 0) {
            phaseRegister |= 1 << phase;
        } else {
            phaseRegister &= ~(1 << phase);
        }
        drive35Signals.phaseChanged(phase, (offset & 0x01) != 0, phaseRegister, active);
    }
}
