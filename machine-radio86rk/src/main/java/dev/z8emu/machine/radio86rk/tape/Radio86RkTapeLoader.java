package dev.z8emu.machine.radio86rk.tape;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Radio86RkTapeLoader {
    private static final int HEADER_ZERO_BYTES = 256;
    private static final int SYNC_BYTE = 0xE6;
    private static final int RK20_HALF_WAVE_TSTATES = 808;

    private Radio86RkTapeLoader() {
    }

    public static Radio86TapeFile load(InputStream input) throws IOException {
        byte[] payload = readAllBytes(input);
        int totalBytes = HEADER_ZERO_BYTES + 1 + payload.length;
        boolean[] levels = new boolean[totalBytes * 16];
        int[] durations = new int[totalBytes * 16];
        int index = 0;

        for (int i = 0; i < HEADER_ZERO_BYTES; i++) {
            index = appendByte(index, 0x00, levels, durations);
        }
        index = appendByte(index, SYNC_BYTE, levels, durations);
        for (byte value : payload) {
            index = appendByte(index, Byte.toUnsignedInt(value), levels, durations);
        }

        return new Radio86TapeFile(levels, durations);
    }

    private static int appendByte(int index, int value, boolean[] levels, int[] durations) {
        for (int bit = 7; bit >= 0; bit--) {
            boolean one = ((value >>> bit) & 0x01) != 0;
            levels[index] = !one;
            durations[index] = RK20_HALF_WAVE_TSTATES;
            index++;
            levels[index] = one;
            durations[index] = RK20_HALF_WAVE_TSTATES;
            index++;
        }
        return index;
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }
}
