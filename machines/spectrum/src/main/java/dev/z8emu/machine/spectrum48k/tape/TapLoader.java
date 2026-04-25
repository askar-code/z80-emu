package dev.z8emu.machine.spectrum48k.tape;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class TapLoader {
    private static final int PILOT_PULSE_LENGTH = 2_168;
    private static final int SYNC_FIRST_PULSE_LENGTH = 667;
    private static final int SYNC_SECOND_PULSE_LENGTH = 735;
    private static final int ZERO_BIT_PULSE_LENGTH = 855;
    private static final int ONE_BIT_PULSE_LENGTH = 1_710;
    private static final int HEADER_PILOT_PULSES = 8_063;
    private static final int DATA_PILOT_PULSES = 3_223;

    private TapLoader() {
    }

    public static TapeFile load(InputStream input) throws IOException {
        List<TapeBlock> blocks = new ArrayList<>();

        while (true) {
            int low = input.read();
            if (low < 0) {
                break;
            }

            int high = input.read();
            if (high < 0) {
                throw new IOException("Unexpected EOF in TAP block length");
            }

            int length = low | (high << 8);
            byte[] data = input.readNBytes(length);
            if (data.length != length) {
                throw new IOException("Unexpected EOF in TAP block data");
            }

            boolean header = length > 0 && (data[0] & 0xFF) == 0x00;
            int[] prefixPulses = buildStandardDataPulses(header ? HEADER_PILOT_PULSES : DATA_PILOT_PULSES);
            blocks.add(TapeBlock.dataBlock(
                    prefixPulses,
                    ZERO_BIT_PULSE_LENGTH,
                    ONE_BIT_PULSE_LENGTH,
                    8,
                    1_000,
                    data
            ));
        }

        return new TapeFile(List.copyOf(blocks));
    }

    static int[] buildStandardDataPulses(int pilotTonePulses) {
        int[] pulses = new int[pilotTonePulses + 2];
        for (int i = 0; i < pilotTonePulses; i++) {
            pulses[i] = PILOT_PULSE_LENGTH;
        }
        pulses[pilotTonePulses] = SYNC_FIRST_PULSE_LENGTH;
        pulses[pilotTonePulses + 1] = SYNC_SECOND_PULSE_LENGTH;
        return pulses;
    }
}
