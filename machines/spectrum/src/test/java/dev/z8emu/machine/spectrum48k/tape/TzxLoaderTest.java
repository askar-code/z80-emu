package dev.z8emu.machine.spectrum48k.tape;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TzxLoaderTest {
    @Test
    void parsesStandardSpeedDataBlock() throws Exception {
        byte[] tzx = tzx(
                block(0x10, le16(1_000), le16(3), bytes(0x00, 0x11, 0x22))
        );

        TapeFile tapeFile = TzxLoader.load(new ByteArrayInputStream(tzx));
        TapeBlock block = tapeFile.blocks().get(0);

        assertEquals(1, tapeFile.blocks().size());
        assertEquals(8_065, block.prefixPulseLengthsTStates().length);
        assertEquals(2_168, block.prefixPulseLengthsTStates()[0]);
        assertEquals(667, block.prefixPulseLengthsTStates()[8_063]);
        assertEquals(735, block.prefixPulseLengthsTStates()[8_064]);
        assertEquals(855, block.zeroBitPulseLengthTStates());
        assertEquals(1_710, block.oneBitPulseLengthTStates());
        assertEquals(8, block.usedBitsInLastByte());
        assertEquals(1_000, block.pauseAfterMillis());
        assertArrayEquals(bytes(0x00, 0x11, 0x22), block.data());
    }

    @Test
    void parsesTurboPureTonePulseSequencePureDataAndPauseBlocks() throws Exception {
        byte[] tzx = tzx(
                block(0x30, bytes(3), bytes('f', 'o', 'o')),
                block(0x11,
                        le16(2_000),
                        le16(500),
                        le16(600),
                        le16(700),
                        le16(1_400),
                        le16(10),
                        bytes(8),
                        le16(333),
                        le24(2),
                        bytes(0xAA, 0x55)),
                block(0x14, le16(400), le16(800), bytes(3), le16(250), le24(1), bytes(0xE0)),
                block(0x20, le16(0))
        );

        TapeFile tapeFile = TzxLoader.load(new ByteArrayInputStream(tzx));

        assertEquals(3, tapeFile.blocks().size());

        TapeBlock turbo = tapeFile.blocks().get(0);
        assertEquals(12, turbo.prefixPulseLengthsTStates().length);
        assertEquals(2_000, turbo.prefixPulseLengthsTStates()[0]);
        assertEquals(500, turbo.prefixPulseLengthsTStates()[10]);
        assertEquals(600, turbo.prefixPulseLengthsTStates()[11]);
        assertEquals(700, turbo.zeroBitPulseLengthTStates());
        assertEquals(1_400, turbo.oneBitPulseLengthTStates());
        assertEquals(8, turbo.usedBitsInLastByte());
        assertEquals(333, turbo.pauseAfterMillis());
        assertArrayEquals(bytes(0xAA, 0x55), turbo.data());

        TapeBlock pureData = tapeFile.blocks().get(1);
        assertEquals(0, pureData.prefixPulseLengthsTStates().length);
        assertEquals(400, pureData.zeroBitPulseLengthTStates());
        assertEquals(800, pureData.oneBitPulseLengthTStates());
        assertEquals(3, pureData.usedBitsInLastByte());
        assertEquals(3, pureData.totalDataBits());
        assertEquals(250, pureData.pauseAfterMillis());
        assertArrayEquals(bytes(0xE0), pureData.data());

        TapeBlock pause = tapeFile.blocks().get(2);
        assertTrue(pause.stopTapeAfterBlock());
        assertEquals(0, pause.pauseAfterMillis());
        assertFalse(pause.hasData());
    }

    @Test
    void mergesPureToneAndPulseSequenceIntoFollowingPureDataBlock() throws Exception {
        byte[] tzx = tzx(
                block(0x12, le16(1_000), le16(3)),
                block(0x13, bytes(3), le16(100), le16(200), le16(300)),
                block(0x14, le16(400), le16(800), bytes(3), le16(250), le24(1), bytes(0xE0))
        );

        TapeFile tapeFile = TzxLoader.load(new ByteArrayInputStream(tzx));
        TapeBlock block = tapeFile.blocks().get(0);

        assertEquals(1, tapeFile.blocks().size());
        assertArrayEquals(new int[]{1_000, 1_000, 1_000, 100, 200, 300}, block.prefixPulseLengthsTStates());
        assertEquals(400, block.zeroBitPulseLengthTStates());
        assertEquals(800, block.oneBitPulseLengthTStates());
        assertEquals(250, block.pauseAfterMillis());
        assertArrayEquals(bytes(0xE0), block.data());
    }

    @Test
    void parsesStopTapeIf48kModeBlockSeparatelyFromHardStop() throws Exception {
        byte[] tzx = tzx(
                block(0x2A, bytes(0, 0, 0, 0))
        );

        TapeFile tapeFile = TzxLoader.load(new ByteArrayInputStream(tzx));
        TapeBlock block = tapeFile.blocks().get(0);

        assertEquals(1, tapeFile.blocks().size());
        assertFalse(block.stopTapeAfterBlock());
        assertTrue(block.stopTapeIf48kMode());
        assertEquals(0, block.pauseAfterMillis());
    }

    @Test
    void parsesDirectRecordingWithExplicitMsbFirstSampleLevels() throws Exception {
        byte[] tzx = tzx(
                block(0x15, le16(79), le16(250), bytes(5), le24(1), bytes(0xB0))
        );

        TapeBlock block = TzxLoader.load(new ByteArrayInputStream(tzx)).blocks().get(0);
        TapeBlock.DirectRecording recording = assertInstanceOf(TapeBlock.DirectRecording.class, block.rawSignal());

        assertEquals(79, recording.tStatesPerSample());
        assertEquals(5, recording.usedBitsInLastByte());
        assertEquals(5, recording.totalSamples());
        assertEquals(250, block.pauseAfterMillis());
        assertArrayEquals(bytes(0xB0), recording.samples());
        assertTrue(recording.sampleHigh(0));
        assertFalse(recording.sampleHigh(1));
        assertTrue(recording.sampleHigh(2));
        assertTrue(recording.sampleHigh(3));
        assertFalse(recording.sampleHigh(4));
    }

    @Test
    void rejectsMalformedDirectRecordingFields() throws Exception {
        byte[] zeroSampleDuration = tzx(
                block(0x15, le16(0), le16(0), bytes(8), le24(1), bytes(0))
        );
        byte[] zeroUsedBits = tzx(
                block(0x15, le16(79), le16(0), bytes(0), le24(1), bytes(0))
        );
        byte[] tooManyUsedBits = tzx(
                block(0x15, le16(79), le16(0), bytes(9), le24(1), bytes(0))
        );

        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(zeroSampleDuration)));
        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(zeroUsedBits)));
        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(tooManyUsedBits)));
    }

    @Test
    void parsesCswRleIncludingExtendedPulseLengths() throws Exception {
        byte[] tzx = tzx(
                block(0x18,
                        le32(16),
                        le16(250),
                        le24(44_100),
                        bytes(1),
                        le32(2),
                        bytes(3, 0, 0x2C, 0x01, 0, 0))
        );

        TapeBlock block = TzxLoader.load(new ByteArrayInputStream(tzx)).blocks().get(0);
        TapeBlock.CswRecording recording = assertInstanceOf(TapeBlock.CswRecording.class, block.rawSignal());

        assertEquals(44_100, recording.samplingRateHz());
        assertArrayEquals(new int[]{3, 300}, recording.pulseLengthsInSamples());
        assertEquals(250, block.pauseAfterMillis());
    }

    @Test
    void parsesCanonicalCswZrleStream() throws Exception {
        byte[] zrle = bytes(
                0x78, 0xDA, 0x63, 0x66, 0xD0, 0x61, 0x64,
                0x60, 0x00, 0x00, 0x00, 0xCB, 0x00, 0x31
        );
        byte[] tzx = tzx(
                block(0x18,
                        le32(10 + zrle.length),
                        le16(0),
                        le24(44_100),
                        bytes(2),
                        le32(2),
                        zrle)
        );

        TapeBlock block = TzxLoader.load(new ByteArrayInputStream(tzx)).blocks().get(0);
        TapeBlock.CswRecording recording = assertInstanceOf(TapeBlock.CswRecording.class, block.rawSignal());

        assertArrayEquals(new int[]{3, 300}, recording.pulseLengthsInSamples());
    }

    @Test
    void acceptsAValidEmptyCswZrleStreamWhenZeroPulsesAreDeclared() throws Exception {
        byte[] emptyZrle = bytes(0x78, 0xDA, 0x03, 0x00, 0x00, 0x00, 0x00, 0x01);
        byte[] tzx = tzx(
                block(0x18,
                        le32(10 + emptyZrle.length),
                        le16(0),
                        le24(44_100),
                        bytes(2),
                        le32(0),
                        emptyZrle)
        );

        TapeBlock block = TzxLoader.load(new ByteArrayInputStream(tzx)).blocks().get(0);
        TapeBlock.CswRecording recording = assertInstanceOf(TapeBlock.CswRecording.class, block.rawSignal());

        assertArrayEquals(new int[0], recording.pulseLengthsInSamples());
    }

    @Test
    void validatesCswPulseCountAndHostileBounds() throws Exception {
        byte[] countMismatch = tzx(
                block(0x18, le32(12), le16(0), le24(44_100), bytes(1), le32(3), bytes(3, 4))
        );
        byte[] zeroExtendedPulse = tzx(
                block(0x18, le32(15), le16(0), le24(44_100), bytes(1), le32(1), bytes(0, 0, 0, 0, 0))
        );
        byte[] pulseCountOverflow = tzx(
                block(0x18, le32(10), le16(0), le24(44_100), bytes(1), le32(8_000_001))
        );
        byte[] encodedLengthOverflow = tzx(
                block(0x18, le32((64 * 1024 * 1024) + 11))
        );
        byte[] zrleExpansionPastDeclaredBound = tzx(
                block(0x18,
                        le32(21),
                        le16(0),
                        le24(44_100),
                        bytes(2),
                        le32(1),
                        bytes(0x78, 0xDA, 0x63, 0x64, 0x04, 0x01, 0x00, 0x00, 0x1B, 0x00, 0x07))
        );

        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(countMismatch)));
        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(zeroExtendedPulse)));
        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(pulseCountOverflow)));
        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(encodedLengthOverflow)));
        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(zrleExpansionPastDeclaredBound)));
    }

    @Test
    void rejectsInvalidCswHeaderAndCompression() throws Exception {
        byte[] shortBlock = tzx(
                block(0x18, le32(9))
        );
        byte[] zeroSamplingRate = tzx(
                block(0x18, le32(10), le16(0), le24(0), bytes(1), le32(0))
        );
        byte[] unknownCompression = tzx(
                block(0x18, le32(10), le16(0), le24(44_100), bytes(3), le32(0))
        );

        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(shortBlock)));
        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(zeroSamplingRate)));
        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(unknownCompression)));
    }

    @Test
    void parsesSetSignalLevelBlock() throws Exception {
        byte[] tzx = tzx(
                block(0x2B, le32(1), bytes(1))
        );

        TapeFile tapeFile = TzxLoader.load(new ByteArrayInputStream(tzx));
        TapeBlock block = tapeFile.blocks().get(0);

        assertEquals(1, tapeFile.blocks().size());
        assertTrue(block.setsSignalLevel());
        assertEquals(TapeBlock.SignalLevel.HIGH, block.signalLevel());
        assertFalse(block.hasData());
    }

    @Test
    void rejectsMalformedSetSignalLevelBlock() throws Exception {
        byte[] invalidLength = tzx(
                block(0x2B, le32(2), bytes(1, 0))
        );
        byte[] invalidLevel = tzx(
                block(0x2B, le32(1), bytes(2))
        );

        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(invalidLength)));
        assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(invalidLevel)));
    }

    @Test
    void executesSignedRelativeJumpsUsingPhysicalBlockIndexesIncludingMetadata() throws Exception {
        byte[] tzx = tzx(
                block(0x23, le16(4)),
                signalLevel(0),
                block(0x30, bytes(3), bytes('f', 'o', 'o')),
                block(0x23, le16(3)),
                signalLevel(1),
                block(0x23, le16(-4)),
                block(0x20, le16(7))
        );

        TapeFile tape = TzxLoader.load(new ByteArrayInputStream(tzx));

        assertEquals(3, tape.blocks().size());
        assertEquals(TapeBlock.SignalLevel.HIGH, tape.blocks().get(0).signalLevel());
        assertEquals(TapeBlock.SignalLevel.LOW, tape.blocks().get(1).signalLevel());
        assertEquals(7, tape.blocks().get(2).pauseAfterMillis());
    }

    @Test
    void repeatsLoopBodyTheDeclaredNumberOfTimes() throws Exception {
        byte[] tzx = tzx(
                block(0x24, le16(3)),
                signalLevel(1),
                block(0x25),
                block(0x20, le16(11))
        );

        TapeFile tape = TzxLoader.load(new ByteArrayInputStream(tzx));

        assertEquals(4, tape.blocks().size());
        assertEquals(TapeBlock.SignalLevel.HIGH, tape.blocks().get(0).signalLevel());
        assertEquals(TapeBlock.SignalLevel.HIGH, tape.blocks().get(1).signalLevel());
        assertEquals(TapeBlock.SignalLevel.HIGH, tape.blocks().get(2).signalLevel());
        assertEquals(11, tape.blocks().get(3).pauseAfterMillis());
    }

    @Test
    void executesEveryCallSequenceTargetAndReturnsToTheFollowingBlock() throws Exception {
        byte[] tzx = tzx(
                block(0x26, le16(2), le16(3), le16(5)),
                signalLevel(0),
                block(0x23, le16(5)),
                signalLevel(1),
                block(0x27),
                block(0x20, le16(9)),
                block(0x27),
                block(0x20, le16(1))
        );

        TapeFile tape = TzxLoader.load(new ByteArrayInputStream(tzx));

        assertEquals(4, tape.blocks().size());
        assertEquals(TapeBlock.SignalLevel.HIGH, tape.blocks().get(0).signalLevel());
        assertEquals(9, tape.blocks().get(1).pauseAfterMillis());
        assertEquals(TapeBlock.SignalLevel.LOW, tape.blocks().get(2).signalLevel());
        assertEquals(1, tape.blocks().get(3).pauseAfterMillis());
    }

    @Test
    void supportsCallsInsideLoopsAndLoopsInsideCalledSequences() throws Exception {
        byte[] callInsideLoop = tzx(
                block(0x24, le16(2)),
                block(0x26, le16(1), le16(4)),
                signalLevel(0),
                block(0x25),
                block(0x23, le16(4)),
                signalLevel(1),
                block(0x27),
                block(0x22),
                block(0x20, le16(2))
        );
        byte[] loopInsideCall = tzx(
                block(0x26, le16(1), le16(3)),
                signalLevel(0),
                block(0x23, le16(6)),
                block(0x24, le16(2)),
                signalLevel(1),
                block(0x25),
                block(0x27),
                block(0x22),
                block(0x20, le16(3))
        );

        TapeFile outerLoop = TzxLoader.load(new ByteArrayInputStream(callInsideLoop));
        TapeFile outerCall = TzxLoader.load(new ByteArrayInputStream(loopInsideCall));

        assertEquals(5, outerLoop.blocks().size());
        assertEquals(TapeBlock.SignalLevel.HIGH, outerLoop.blocks().get(0).signalLevel());
        assertEquals(TapeBlock.SignalLevel.LOW, outerLoop.blocks().get(1).signalLevel());
        assertEquals(TapeBlock.SignalLevel.HIGH, outerLoop.blocks().get(2).signalLevel());
        assertEquals(TapeBlock.SignalLevel.LOW, outerLoop.blocks().get(3).signalLevel());
        assertEquals(2, outerLoop.blocks().get(4).pauseAfterMillis());

        assertEquals(4, outerCall.blocks().size());
        assertEquals(TapeBlock.SignalLevel.HIGH, outerCall.blocks().get(0).signalLevel());
        assertEquals(TapeBlock.SignalLevel.HIGH, outerCall.blocks().get(1).signalLevel());
        assertEquals(TapeBlock.SignalLevel.LOW, outerCall.blocks().get(2).signalLevel());
        assertEquals(3, outerCall.blocks().get(3).pauseAfterMillis());
    }

    @Test
    void selectsFirstOptionByDefaultAndAllowsAnExplicitResolver() throws Exception {
        byte[] tzx = tzx(
                selectBlock(new int[]{1, 3}, "First", "Second"),
                signalLevel(1),
                block(0x23, le16(4)),
                signalLevel(0),
                block(0x23, le16(2)),
                block(0x22),
                block(0x20, le16(6))
        );

        TapeFile defaultSelection = TzxLoader.load(new ByteArrayInputStream(tzx));
        TapeFile explicitSelection = TzxLoader.load(new ByteArrayInputStream(tzx), context -> {
            assertEquals(0, context.blockIndex());
            assertEquals(2, context.options().size());
            assertEquals(1, context.options().get(0).relativeOffset());
            assertEquals("First", context.options().get(0).description());
            assertEquals(3, context.options().get(1).relativeOffset());
            assertEquals("Second", context.options().get(1).description());
            return 1;
        });

        assertEquals(TapeBlock.SignalLevel.HIGH, defaultSelection.blocks().get(0).signalLevel());
        assertEquals(6, defaultSelection.blocks().get(1).pauseAfterMillis());
        assertEquals(TapeBlock.SignalLevel.LOW, explicitSelection.blocks().get(0).signalLevel());
        assertEquals(6, explicitSelection.blocks().get(1).pauseAfterMillis());
    }

    @Test
    void rejectsInvalidJumpCallAndSelectTargetsBeforeResolvingASelection() throws Exception {
        byte[] invalidJump = tzx(block(0x23, le16(2)));
        byte[] invalidCall = tzx(block(0x26, le16(1), le16(-1)));
        byte[] invalidSelect = tzx(selectBlock(new int[]{2}, "Outside"));

        IOException jumpError = assertThrows(
                IOException.class,
                () -> TzxLoader.load(new ByteArrayInputStream(invalidJump))
        );
        IOException callError = assertThrows(
                IOException.class,
                () -> TzxLoader.load(new ByteArrayInputStream(invalidCall))
        );
        IOException selectError = assertThrows(
                IOException.class,
                () -> TzxLoader.load(new ByteArrayInputStream(invalidSelect), context -> {
                    throw new AssertionError("resolver must not run for a statically invalid target");
                })
        );

        assertTrue(jumpError.getMessage().contains("Invalid TZX jump target"));
        assertTrue(callError.getMessage().contains("Invalid TZX call target"));
        assertTrue(selectError.getMessage().contains("Invalid TZX select target"));
    }

    @Test
    void rejectsMalformedLoopAndCallStacks() throws Exception {
        byte[] unmatchedLoopEnd = tzx(block(0x25));
        byte[] unmatchedLoopStart = tzx(block(0x24, le16(2)), block(0x22));
        byte[] invalidLoopCount = tzx(block(0x24, le16(1)), block(0x25));
        byte[] nestedLoop = tzx(
                block(0x24, le16(2)),
                block(0x24, le16(2)),
                block(0x25),
                block(0x25)
        );
        byte[] unmatchedReturn = tzx(block(0x27));
        byte[] missingReturn = tzx(
                block(0x26, le16(1), le16(1)),
                signalLevel(1)
        );
        byte[] nestedCall = tzx(
                block(0x26, le16(1), le16(2)),
                signalLevel(0),
                block(0x26, le16(1), le16(2)),
                block(0x27),
                block(0x27)
        );

        assertTrue(loadError(unmatchedLoopEnd).getMessage().contains("Unmatched TZX loop end"));
        assertTrue(loadError(unmatchedLoopStart).getMessage().contains("Unmatched TZX loop start"));
        assertTrue(loadError(invalidLoopCount).getMessage().contains("must be greater than 1"));
        assertTrue(loadError(nestedLoop).getMessage().contains("Nested TZX loop"));
        assertTrue(loadError(unmatchedReturn).getMessage().contains("Unmatched TZX return"));
        assertTrue(loadError(missingReturn).getMessage().contains("ended without a return"));
        assertTrue(loadError(nestedCall).getMessage().contains("Nested TZX call sequence"));
    }

    @Test
    void rejectsTruncatedCallAndSelectPayloadsAndInvalidResolverResults() throws Exception {
        byte[] truncatedCall = tzx(block(0x26, le16(2), le16(1)));
        byte[] truncatedSelect = tzx(block(0x28, le16(5), bytes(1, 1, 0, 3)));
        byte[] select = tzx(selectBlock(new int[]{1}, "Only"), block(0x22));

        assertTrue(loadError(truncatedCall).getMessage().contains("Unexpected EOF"));
        assertTrue(loadError(truncatedSelect).getMessage().contains("Unexpected EOF"));
        IOException resolverError = assertThrows(
                IOException.class,
                () -> TzxLoader.load(new ByteArrayInputStream(select), context -> 1)
        );
        assertTrue(resolverError.getMessage().contains("invalid option 1"));
    }

    @Test
    void enforcesInstructionOutputAndPendingPulseBudgetsBeforePlayback() throws Exception {
        byte[] runawayJump = tzx(block(0x23, le16(0)));
        byte[] runawayCall = tzx(
                block(0x26, le16(1), le16(1)),
                block(0x23, le16(0))
        );
        byte[] excessiveOutput = tzx(
                block(0x24, le16(3)),
                signalLevel(1),
                block(0x25)
        );
        byte[] excessivePendingPulses = tzx(
                block(0x24, le16(3)),
                block(0x12, le16(100), le16(2)),
                block(0x25),
                block(0x14, le16(100), le16(200), bytes(8), le16(0), le24(1), bytes(0))
        );

        IOException instructionError = loadError(
                runawayJump,
                new TzxLoader.ExecutionLimits(10, 3, 10, 10)
        );
        IOException callInstructionError = loadError(
                runawayCall,
                new TzxLoader.ExecutionLimits(10, 3, 10, 10)
        );
        IOException outputError = loadError(
                excessiveOutput,
                new TzxLoader.ExecutionLimits(10, 100, 2, 10)
        );
        IOException pulseError = loadError(
                excessivePendingPulses,
                new TzxLoader.ExecutionLimits(10, 100, 10, 5)
        );

        assertTrue(instructionError.getMessage().contains("instruction budget exceeded"));
        assertTrue(callInstructionError.getMessage().contains("instruction budget exceeded"));
        assertTrue(outputError.getMessage().contains("output block budget exceeded"));
        assertTrue(pulseError.getMessage().contains("pending-pulse budget exceeded"));
    }

    @Test
    void enforcesPhysicalProgramBlockBudgetWhileParsing() throws Exception {
        byte[] tzx = tzx(block(0x22), block(0x22));

        IOException error = loadError(
                tzx,
                new TzxLoader.ExecutionLimits(1, 10, 10, 10)
        );

        assertTrue(error.getMessage().contains("program block budget exceeded"));
    }

    private static byte[] tzx(byte[]... blocks) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes('Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1A, 0x01, 0x20));
        for (byte[] block : blocks) {
            out.write(block);
        }
        return out.toByteArray();
    }

    private static byte[] block(int id, byte[]... parts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(id);
        for (byte[] part : parts) {
            out.write(part);
        }
        return out.toByteArray();
    }

    private static byte[] signalLevel(int level) throws IOException {
        return block(0x2B, le32(1), bytes(level));
    }

    private static byte[] selectBlock(int[] relativeOffsets, String... descriptions) throws IOException {
        if (relativeOffsets.length != descriptions.length) {
            throw new IllegalArgumentException("Every select offset needs a description");
        }
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(relativeOffsets.length);
        for (int i = 0; i < relativeOffsets.length; i++) {
            byte[] description = descriptions[i].getBytes(StandardCharsets.ISO_8859_1);
            payload.write(le16(relativeOffsets[i]));
            payload.write(description.length);
            payload.write(description);
        }
        byte[] payloadBytes = payload.toByteArray();
        return block(0x28, le16(payloadBytes.length), payloadBytes);
    }

    private static IOException loadError(byte[] tzx) {
        return assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(tzx)));
    }

    private static IOException loadError(byte[] tzx, TzxLoader.ExecutionLimits limits) {
        return assertThrows(IOException.class, () -> TzxLoader.load(new ByteArrayInputStream(tzx), limits));
    }

    private static byte[] le16(int value) {
        return bytes(value & 0xFF, (value >>> 8) & 0xFF);
    }

    private static byte[] le24(int value) {
        return bytes(value & 0xFF, (value >>> 8) & 0xFF, (value >>> 16) & 0xFF);
    }

    private static byte[] le32(int value) {
        return bytes(
                value & 0xFF,
                (value >>> 8) & 0xFF,
                (value >>> 16) & 0xFF,
                (value >>> 24) & 0xFF
        );
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
