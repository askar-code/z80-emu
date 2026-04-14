package dev.z8emu.machine.spectrum48k.tape;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class TzxLoader {
    private static final byte[] SIGNATURE = {'Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1A};

    private TzxLoader() {
    }

    public static TapeFile load(InputStream input) throws IOException {
        byte[] bytes = input.readAllBytes();
        Cursor cursor = new Cursor(bytes);
        validateHeader(cursor);

        List<TapeBlock> blocks = new ArrayList<>();
        while (cursor.hasRemaining()) {
            int blockId = cursor.readU8();
            switch (blockId) {
                case 0x10 -> blocks.add(readStandardSpeedData(cursor));
                case 0x11 -> blocks.add(readTurboSpeedData(cursor));
                case 0x12 -> blocks.add(readPureTone(cursor));
                case 0x13 -> blocks.add(readPulseSequence(cursor));
                case 0x14 -> blocks.add(readPureData(cursor));
                case 0x20 -> blocks.add(readPause(cursor));
                case 0x21 -> cursor.skip(cursor.readU8());
                case 0x22 -> {
                    // Group end has no payload.
                }
                case 0x2A -> {
                    cursor.skip(4);
                    blocks.add(TapeBlock.pauseBlock(0, true));
                }
                case 0x30 -> cursor.skip(cursor.readU8());
                case 0x31 -> {
                    cursor.skip(1);
                    cursor.skip(cursor.readU8());
                }
                case 0x32 -> cursor.skip(cursor.readU16());
                case 0x33 -> cursor.skip(cursor.readU8() * 3);
                case 0x35 -> {
                    cursor.skip(10);
                    cursor.skip(cursor.readU32());
                }
                case 0x5A -> cursor.skip(9);
                default -> throw new IOException("Unsupported TZX block 0x%02X".formatted(blockId));
            }
        }

        return new TapeFile(List.copyOf(blocks));
    }

    private static void validateHeader(Cursor cursor) throws IOException {
        if (cursor.remaining() < SIGNATURE.length + 2) {
            throw new IOException("Incomplete TZX header");
        }

        for (byte expected : SIGNATURE) {
            if (cursor.readU8() != (expected & 0xFF)) {
                throw new IOException("Invalid TZX signature");
            }
        }

        cursor.skip(2);
    }

    private static TapeBlock readStandardSpeedData(Cursor cursor) throws IOException {
        int pauseAfterMillis = cursor.readU16();
        int length = cursor.readU16();
        byte[] data = cursor.readBytes(length);
        boolean header = length > 0 && (data[0] & 0xFF) == 0x00;
        return TapeBlock.dataBlock(
                TapLoader.buildStandardDataPulses(header ? 8_063 : 3_223),
                855,
                1_710,
                8,
                pauseAfterMillis,
                data
        );
    }

    private static TapeBlock readTurboSpeedData(Cursor cursor) throws IOException {
        int pilotPulseLength = cursor.readU16();
        int syncFirstPulseLength = cursor.readU16();
        int syncSecondPulseLength = cursor.readU16();
        int zeroBitPulseLength = cursor.readU16();
        int oneBitPulseLength = cursor.readU16();
        int pilotTonePulses = cursor.readU16();
        int usedBitsInLastByte = normalizeUsedBits(cursor.readU8());
        int pauseAfterMillis = cursor.readU16();
        int length = cursor.readU24();
        byte[] data = cursor.readBytes(length);
        return TapeBlock.dataBlock(
                buildTurboPrefixPulses(pilotPulseLength, pilotTonePulses, syncFirstPulseLength, syncSecondPulseLength),
                zeroBitPulseLength,
                oneBitPulseLength,
                usedBitsInLastByte,
                pauseAfterMillis,
                data
        );
    }

    private static TapeBlock readPureTone(Cursor cursor) throws IOException {
        int pulseLength = cursor.readU16();
        int pulseCount = cursor.readU16();
        return TapeBlock.dataBlock(repeatPulse(pulseLength, pulseCount), 0, 0, 0, 0, new byte[0]);
    }

    private static TapeBlock readPulseSequence(Cursor cursor) throws IOException {
        int count = cursor.readU8();
        int[] pulses = new int[count];
        for (int i = 0; i < count; i++) {
            pulses[i] = cursor.readU16();
        }
        return TapeBlock.dataBlock(pulses, 0, 0, 0, 0, new byte[0]);
    }

    private static TapeBlock readPureData(Cursor cursor) throws IOException {
        int zeroBitPulseLength = cursor.readU16();
        int oneBitPulseLength = cursor.readU16();
        int usedBitsInLastByte = normalizeUsedBits(cursor.readU8());
        int pauseAfterMillis = cursor.readU16();
        int length = cursor.readU24();
        byte[] data = cursor.readBytes(length);
        return TapeBlock.dataBlock(
                new int[0],
                zeroBitPulseLength,
                oneBitPulseLength,
                usedBitsInLastByte,
                pauseAfterMillis,
                data
        );
    }

    private static TapeBlock readPause(Cursor cursor) throws IOException {
        int pauseAfterMillis = cursor.readU16();
        return TapeBlock.pauseBlock(pauseAfterMillis, pauseAfterMillis == 0);
    }

    private static int[] buildTurboPrefixPulses(
            int pilotPulseLength,
            int pilotTonePulses,
            int syncFirstPulseLength,
            int syncSecondPulseLength
    ) {
        int[] pulses = new int[pilotTonePulses + 2];
        for (int i = 0; i < pilotTonePulses; i++) {
            pulses[i] = pilotPulseLength;
        }
        pulses[pilotTonePulses] = syncFirstPulseLength;
        pulses[pilotTonePulses + 1] = syncSecondPulseLength;
        return pulses;
    }

    private static int[] repeatPulse(int pulseLength, int pulseCount) {
        int[] pulses = new int[pulseCount];
        for (int i = 0; i < pulseCount; i++) {
            pulses[i] = pulseLength;
        }
        return pulses;
    }

    private static int normalizeUsedBits(int usedBitsInLastByte) {
        return usedBitsInLastByte == 0 ? 8 : usedBitsInLastByte;
    }

    private static final class Cursor {
        private final byte[] bytes;
        private int index;

        private Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        boolean hasRemaining() {
            return index < bytes.length;
        }

        int remaining() {
            return bytes.length - index;
        }

        int readU8() throws IOException {
            ensureAvailable(1);
            return bytes[index++] & 0xFF;
        }

        int readU16() throws IOException {
            int low = readU8();
            int high = readU8();
            return low | (high << 8);
        }

        int readU24() throws IOException {
            int b0 = readU8();
            int b1 = readU8();
            int b2 = readU8();
            return b0 | (b1 << 8) | (b2 << 16);
        }

        int readU32() throws IOException {
            int b0 = readU8();
            int b1 = readU8();
            int b2 = readU8();
            int b3 = readU8();
            return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }

        byte[] readBytes(int length) throws IOException {
            ensureAvailable(length);
            byte[] result = new byte[length];
            System.arraycopy(bytes, index, result, 0, length);
            index += length;
            return result;
        }

        void skip(int length) throws IOException {
            ensureAvailable(length);
            index += length;
        }

        private void ensureAvailable(int length) throws IOException {
            if (length < 0 || remaining() < length) {
                throw new IOException("Unexpected EOF in TZX block");
            }
        }
    }
}
