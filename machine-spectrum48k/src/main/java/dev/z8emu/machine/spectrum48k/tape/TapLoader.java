package dev.z8emu.machine.spectrum48k.tape;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class TapLoader {
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
            blocks.add(new TapeBlock(
                    2168,
                    667,
                    735,
                    855,
                    1710,
                    header ? HEADER_PILOT_PULSES : DATA_PILOT_PULSES,
                    1000,
                    data
            ));
        }

        return new TapeFile(List.copyOf(blocks));
    }
}
