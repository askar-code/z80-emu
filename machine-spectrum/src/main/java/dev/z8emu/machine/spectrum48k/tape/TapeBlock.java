package dev.z8emu.machine.spectrum48k.tape;

import java.util.Arrays;

public record TapeBlock(
        int[] prefixPulseLengthsTStates,
        int zeroBitPulseLengthTStates,
        int oneBitPulseLengthTStates,
        int usedBitsInLastByte,
        int pauseAfterMillis,
        boolean stopTapeAfterBlock,
        byte[] data
) {
    public TapeBlock {
        prefixPulseLengthsTStates = prefixPulseLengthsTStates == null
                ? new int[0]
                : Arrays.copyOf(prefixPulseLengthsTStates, prefixPulseLengthsTStates.length);
        data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);

        if (usedBitsInLastByte < 0 || usedBitsInLastByte > 8) {
            throw new IllegalArgumentException("usedBitsInLastByte must be in range 0..8");
        }
        if (data.length > 0 && usedBitsInLastByte == 0) {
            throw new IllegalArgumentException("usedBitsInLastByte must be non-zero when data is present");
        }
    }

    public static TapeBlock dataBlock(
            int[] prefixPulseLengthsTStates,
            int zeroBitPulseLengthTStates,
            int oneBitPulseLengthTStates,
            int usedBitsInLastByte,
            int pauseAfterMillis,
            byte[] data
    ) {
        return new TapeBlock(
                prefixPulseLengthsTStates,
                zeroBitPulseLengthTStates,
                oneBitPulseLengthTStates,
                usedBitsInLastByte,
                pauseAfterMillis,
                false,
                data
        );
    }

    public static TapeBlock pauseBlock(int pauseAfterMillis, boolean stopTapeAfterBlock) {
        return new TapeBlock(new int[0], 0, 0, 0, pauseAfterMillis, stopTapeAfterBlock, new byte[0]);
    }

    public boolean hasPrefixPulses() {
        return prefixPulseLengthsTStates.length > 0;
    }

    public boolean hasData() {
        return data.length > 0;
    }

    public int totalDataBits() {
        if (data.length == 0) {
            return 0;
        }
        return ((data.length - 1) * 8) + usedBitsInLastByte;
    }
}
