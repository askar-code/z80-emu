package dev.z8emu.machine.spectrum48k.tape;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class TzxLoader {
    private static final byte[] SIGNATURE = {'Z', 'X', 'T', 'a', 'p', 'e', '!', 0x1A};
    private static final int MAX_TZX_FILE_BYTES = 128 * 1024 * 1024;
    private static final int MAX_CSW_ENCODED_BYTES = 64 * 1024 * 1024;
    private static final int MAX_CSW_PULSES = 8_000_000;

    /**
     * Deterministic headless policy used by {@link #load(InputStream)}: choose the first option declared by a
     * TZX 0x28 select block. Execution budgets still apply to the selected path.
     */
    public static final SelectResolver FIRST_OPTION = context -> 0;

    public static final ExecutionLimits DEFAULT_EXECUTION_LIMITS = new ExecutionLimits(
            1_000_000,
            1_000_000,
            100_000,
            8_000_000
    );

    private TzxLoader() {
    }

    public static TapeFile load(InputStream input) throws IOException {
        return load(input, FIRST_OPTION, DEFAULT_EXECUTION_LIMITS);
    }

    public static TapeFile load(InputStream input, SelectResolver selectResolver) throws IOException {
        return load(input, selectResolver, DEFAULT_EXECUTION_LIMITS);
    }

    public static TapeFile load(InputStream input, ExecutionLimits limits) throws IOException {
        return load(input, FIRST_OPTION, limits);
    }

    public static TapeFile load(
            InputStream input,
            SelectResolver selectResolver,
            ExecutionLimits limits
    ) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(selectResolver, "selectResolver");
        Objects.requireNonNull(limits, "limits");
        byte[] bytes = input.readNBytes(MAX_TZX_FILE_BYTES + 1);
        if (bytes.length > MAX_TZX_FILE_BYTES) {
            throw new IOException("TZX file exceeds the 128 MiB safety limit");
        }
        Cursor cursor = new Cursor(bytes);
        validateHeader(cursor);

        List<ProgramBlock> program = readProgram(cursor, limits);
        int[] matchingLoopBlock = validateProgram(program);
        return executeProgram(program, matchingLoopBlock, selectResolver, limits);
    }

    /** A caller-controlled, explicit policy for TZX 0x28 select blocks. */
    @FunctionalInterface
    public interface SelectResolver {
        int resolve(SelectContext context) throws IOException;
    }

    /** The physical block index and immutable choices passed to a {@link SelectResolver}. */
    public record SelectContext(int blockIndex, List<SelectOption> options) {
        public SelectContext {
            options = List.copyOf(options);
        }
    }

    /** A TZX 0x28 choice. The signed offset is relative to the select block itself. */
    public record SelectOption(int relativeOffset, String description) {
        public SelectOption {
            description = Objects.requireNonNull(description, "description");
        }
    }

    /** Hard bounds applied while parsing and executing a TZX control-flow program. */
    public record ExecutionLimits(
            int maxProgramBlocks,
            int maxInstructions,
            int maxOutputBlocks,
            int maxPendingPulses
    ) {
        public ExecutionLimits {
            if (maxProgramBlocks <= 0) {
                throw new IllegalArgumentException("maxProgramBlocks must be positive");
            }
            if (maxInstructions <= 0) {
                throw new IllegalArgumentException("maxInstructions must be positive");
            }
            if (maxOutputBlocks <= 0) {
                throw new IllegalArgumentException("maxOutputBlocks must be positive");
            }
            if (maxPendingPulses <= 0) {
                throw new IllegalArgumentException("maxPendingPulses must be positive");
            }
        }
    }

    private static List<ProgramBlock> readProgram(Cursor cursor, ExecutionLimits limits) throws IOException {
        List<ProgramBlock> program = new ArrayList<>();
        while (cursor.hasRemaining()) {
            if (program.size() >= limits.maxProgramBlocks()) {
                throw new IOException(
                        "TZX program block budget exceeded: " + limits.maxProgramBlocks()
                );
            }
            int blockIndex = program.size();
            int blockId = cursor.readU8();
            try {
                program.add(readProgramBlock(blockId, cursor));
            } catch (IOException error) {
                throw new IOException(
                        "Invalid TZX block #%d (0x%02X): %s".formatted(blockIndex, blockId, error.getMessage()),
                        error
                );
            }
        }
        return List.copyOf(program);
    }

    private static ProgramBlock readProgramBlock(int blockId, Cursor cursor) throws IOException {
        return switch (blockId) {
            case 0x10 -> new EmitBlock(readStandardSpeedData(cursor));
            case 0x11 -> new EmitBlock(readTurboSpeedData(cursor));
            case 0x12 -> new PrefixPulsesBlock(readPureTone(cursor));
            case 0x13 -> new PrefixPulsesBlock(readPulseSequence(cursor));
            case 0x14 -> readPureData(cursor);
            case 0x15 -> new EmitBlock(readDirectRecording(cursor));
            case 0x18 -> new EmitBlock(readCswRecording(cursor));
            case 0x20 -> new EmitBlock(readPause(cursor));
            case 0x21 -> {
                cursor.skip(cursor.readU8());
                yield NoOpBlock.INSTANCE;
            }
            case 0x22 -> NoOpBlock.INSTANCE;
            case 0x23 -> new JumpBlock(cursor.readS16());
            case 0x24 -> new LoopStartBlock(cursor.readU16());
            case 0x25 -> LoopEndBlock.INSTANCE;
            case 0x26 -> readCallSequence(cursor);
            case 0x27 -> ReturnBlock.INSTANCE;
            case 0x28 -> readSelect(cursor);
            case 0x2A -> {
                cursor.skip(4);
                yield new EmitBlock(TapeBlock.stopTapeIf48kModeBlock());
            }
            case 0x2B -> new EmitBlock(readSetSignalLevel(cursor));
            case 0x30 -> {
                cursor.skip(cursor.readU8());
                yield NoOpBlock.INSTANCE;
            }
            case 0x31 -> {
                cursor.skip(1);
                cursor.skip(cursor.readU8());
                yield NoOpBlock.INSTANCE;
            }
            case 0x32 -> {
                cursor.skip(cursor.readU16());
                yield NoOpBlock.INSTANCE;
            }
            case 0x33 -> {
                cursor.skip(cursor.readU8() * 3L);
                yield NoOpBlock.INSTANCE;
            }
            case 0x35 -> {
                cursor.skip(10);
                cursor.skip(cursor.readU32Long());
                yield NoOpBlock.INSTANCE;
            }
            case 0x5A -> {
                cursor.skip(9);
                yield NoOpBlock.INSTANCE;
            }
            default -> throw new IOException("Unsupported TZX block 0x%02X".formatted(blockId));
        };
    }

    private static CallSequenceBlock readCallSequence(Cursor cursor) throws IOException {
        int count = cursor.readU16();
        int[] relativeOffsets = new int[count];
        for (int i = 0; i < count; i++) {
            relativeOffsets[i] = cursor.readS16();
        }
        return new CallSequenceBlock(relativeOffsets);
    }

    private static SelectBlock readSelect(Cursor cursor) throws IOException {
        Cursor payload = cursor.readSubCursor(cursor.readU16());
        int optionCount = payload.readU8();
        if (optionCount == 0) {
            throw new IOException("TZX select block has no options");
        }

        List<SelectOption> options = new ArrayList<>(optionCount);
        for (int i = 0; i < optionCount; i++) {
            int relativeOffset = payload.readS16();
            int descriptionLength = payload.readU8();
            String description = new String(
                    payload.readBytes(descriptionLength),
                    StandardCharsets.ISO_8859_1
            );
            options.add(new SelectOption(relativeOffset, description));
        }
        if (payload.hasRemaining()) {
            throw new IOException("Trailing bytes in TZX select block payload: " + payload.remaining());
        }
        return new SelectBlock(options);
    }

    private static int[] validateProgram(List<ProgramBlock> program) throws IOException {
        int[] matchingLoopBlock = new int[program.size()];
        Arrays.fill(matchingLoopBlock, -1);
        int openLoop = -1;

        for (int blockIndex = 0; blockIndex < program.size(); blockIndex++) {
            ProgramBlock block = program.get(blockIndex);
            if (block instanceof JumpBlock jump) {
                validateTarget(program, blockIndex, jump.relativeOffset(), "jump");
            } else if (block instanceof CallSequenceBlock call) {
                for (int offset : call.relativeOffsets()) {
                    validateTarget(program, blockIndex, offset, "call");
                }
            } else if (block instanceof SelectBlock select) {
                for (SelectOption option : select.options()) {
                    validateTarget(program, blockIndex, option.relativeOffset(), "select");
                }
            } else if (block instanceof LoopStartBlock loopStart) {
                if (loopStart.repetitions() <= 1) {
                    throw new IOException(
                            "Invalid TZX loop repetition count at block #" + blockIndex
                                    + ": " + loopStart.repetitions() + " (must be greater than 1)"
                    );
                }
                if (openLoop >= 0) {
                    throw new IOException(
                            "Nested TZX loop at block #" + blockIndex
                                    + " inside loop starting at block #" + openLoop
                    );
                }
                openLoop = blockIndex;
            } else if (block instanceof LoopEndBlock) {
                if (openLoop < 0) {
                    throw new IOException("Unmatched TZX loop end at block #" + blockIndex);
                }
                matchingLoopBlock[openLoop] = blockIndex;
                matchingLoopBlock[blockIndex] = openLoop;
                openLoop = -1;
            }
        }

        if (openLoop >= 0) {
            throw new IOException("Unmatched TZX loop start at block #" + openLoop);
        }
        return matchingLoopBlock;
    }

    private static void validateTarget(
            List<ProgramBlock> program,
            int blockIndex,
            int relativeOffset,
            String kind
    ) throws IOException {
        long target = (long) blockIndex + relativeOffset;
        if (target < 0 || target >= program.size()) {
            throw new IOException(
                    "Invalid TZX %s target from block #%d: offset %+d resolves to block #%d"
                            .formatted(kind, blockIndex, relativeOffset, target)
            );
        }
    }

    private static TapeFile executeProgram(
            List<ProgramBlock> program,
            int[] matchingLoopBlock,
            SelectResolver selectResolver,
            ExecutionLimits limits
    ) throws IOException {
        List<TapeBlock> output = new ArrayList<>();
        PulseAccumulator pendingPulses = new PulseAccumulator();
        ArrayDeque<LoopFrame> loopStack = new ArrayDeque<>();
        ArrayDeque<CallFrame> callStack = new ArrayDeque<>();
        int instructions = 0;
        int programCounter = 0;

        while (programCounter < program.size()) {
            if (instructions >= limits.maxInstructions()) {
                throw new IOException(
                        "TZX execution instruction budget exceeded at block #" + programCounter
                                + ": " + limits.maxInstructions()
                );
            }
            instructions++;

            ProgramBlock block = program.get(programCounter);
            if (block instanceof EmitBlock emit) {
                flushPendingPrefixPulses(output, pendingPulses, limits);
                addOutputBlock(output, emit.block(), limits, programCounter);
                programCounter++;
            } else if (block instanceof PrefixPulsesBlock prefix) {
                pendingPulses.append(prefix.pulses(), limits.maxPendingPulses(), programCounter);
                programCounter++;
            } else if (block instanceof PureDataBlock data) {
                addOutputBlock(output, data.toTapeBlock(pendingPulses.take()), limits, programCounter);
                programCounter++;
            } else if (block instanceof NoOpBlock) {
                programCounter++;
            } else if (block instanceof JumpBlock jump) {
                programCounter += jump.relativeOffset();
            } else if (block instanceof LoopStartBlock loopStart) {
                if (!loopStack.isEmpty()) {
                    throw new IOException("Nested TZX loop reached at block #" + programCounter);
                }
                loopStack.push(new LoopFrame(
                        programCounter,
                        matchingLoopBlock[programCounter],
                        loopStart.repetitions()
                ));
                programCounter++;
            } else if (block instanceof LoopEndBlock) {
                if (loopStack.isEmpty()) {
                    throw new IOException("TZX loop end reached without its start at block #" + programCounter);
                }
                LoopFrame frame = loopStack.peek();
                if (frame.endBlockIndex != programCounter) {
                    throw new IOException(
                            "TZX loop end at block #" + programCounter
                                    + " does not match active loop start at block #" + frame.startBlockIndex
                    );
                }
                if (frame.remainingIterations > 1) {
                    frame.remainingIterations--;
                    programCounter = frame.startBlockIndex + 1;
                } else {
                    loopStack.pop();
                    programCounter++;
                }
            } else if (block instanceof CallSequenceBlock call) {
                if (call.relativeOffsets().length == 0) {
                    programCounter++;
                    continue;
                }
                if (!callStack.isEmpty()) {
                    throw new IOException("Nested TZX call sequence reached at block #" + programCounter);
                }
                CallFrame frame = new CallFrame(
                        programCounter,
                        call.relativeOffsets(),
                        loopStack.size()
                );
                callStack.push(frame);
                programCounter = frame.targetForCurrentCall();
            } else if (block instanceof ReturnBlock) {
                if (callStack.isEmpty()) {
                    throw new IOException("Unmatched TZX return at block #" + programCounter);
                }
                CallFrame frame = callStack.peek();
                if (loopStack.size() != frame.loopDepthAtCall) {
                    throw new IOException(
                            "TZX return at block #" + programCounter
                                    + " changes the active loop stack of call block #" + frame.callBlockIndex
                    );
                }
                if (frame.hasNextCall()) {
                    frame.advance();
                    programCounter = frame.targetForCurrentCall();
                } else {
                    callStack.pop();
                    programCounter = frame.callBlockIndex + 1;
                }
            } else if (block instanceof SelectBlock select) {
                SelectContext context = new SelectContext(programCounter, select.options());
                int selectedIndex;
                try {
                    selectedIndex = selectResolver.resolve(context);
                } catch (IOException error) {
                    throw new IOException(
                            "TZX select resolver failed at block #" + programCounter + ": " + error.getMessage(),
                            error
                    );
                } catch (RuntimeException error) {
                    throw new IOException("TZX select resolver failed at block #" + programCounter, error);
                }
                if (selectedIndex < 0 || selectedIndex >= select.options().size()) {
                    throw new IOException(
                            "TZX select resolver returned invalid option " + selectedIndex
                                    + " at block #" + programCounter
                                    + " (option count " + select.options().size() + ")"
                    );
                }
                programCounter += select.options().get(selectedIndex).relativeOffset();
            } else {
                throw new IOException("Unknown parsed TZX program block at #" + programCounter);
            }
        }

        if (!loopStack.isEmpty()) {
            throw new IOException(
                    "TZX execution ended with an active loop from block #" + loopStack.peek().startBlockIndex
            );
        }
        if (!callStack.isEmpty()) {
            throw new IOException(
                    "TZX call sequence at block #" + callStack.peek().callBlockIndex
                            + " ended without a return"
            );
        }
        flushPendingPrefixPulses(output, pendingPulses, limits);
        return new TapeFile(List.copyOf(output));
    }

    private static void addOutputBlock(
            List<TapeBlock> output,
            TapeBlock block,
            ExecutionLimits limits,
            int programCounter
    ) throws IOException {
        if (output.size() >= limits.maxOutputBlocks()) {
            throw new IOException(
                    "TZX output block budget exceeded at program block #" + programCounter
                            + ": " + limits.maxOutputBlocks()
            );
        }
        output.add(block);
    }

    private static void flushPendingPrefixPulses(
            List<TapeBlock> output,
            PulseAccumulator pendingPulses,
            ExecutionLimits limits
    ) throws IOException {
        if (pendingPulses.isEmpty()) {
            return;
        }
        addOutputBlock(
                output,
                TapeBlock.dataBlock(pendingPulses.take(), 0, 0, 0, 0, new byte[0]),
                limits,
                -1
        );
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
                TapLoader.buildStandardDataPulses(
                        header ? TapLoader.HEADER_PILOT_PULSES : TapLoader.DATA_PILOT_PULSES
                ),
                TapLoader.ZERO_BIT_PULSE_LENGTH,
                TapLoader.ONE_BIT_PULSE_LENGTH,
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

    private static int[] readPureTone(Cursor cursor) throws IOException {
        int pulseLength = cursor.readU16();
        int pulseCount = cursor.readU16();
        return repeatPulse(pulseLength, pulseCount);
    }

    private static int[] readPulseSequence(Cursor cursor) throws IOException {
        int count = cursor.readU8();
        int[] pulses = new int[count];
        for (int i = 0; i < count; i++) {
            pulses[i] = cursor.readU16();
        }
        return pulses;
    }

    private static PureDataBlock readPureData(Cursor cursor) throws IOException {
        int zeroBitPulseLength = cursor.readU16();
        int oneBitPulseLength = cursor.readU16();
        int usedBitsInLastByte = normalizeUsedBits(cursor.readU8());
        int pauseAfterMillis = cursor.readU16();
        int length = cursor.readU24();
        byte[] data = cursor.readBytes(length);
        return new PureDataBlock(
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

    private static TapeBlock readDirectRecording(Cursor cursor) throws IOException {
        int tStatesPerSample = cursor.readU16();
        if (tStatesPerSample == 0) {
            throw new IOException("Invalid TZX direct-recording sample duration: 0");
        }
        int pauseAfterMillis = cursor.readU16();
        int usedBitsInLastByte = cursor.readU8();
        // TZX 1.20 defines 0x15 strictly as 1..8; the legacy 0-as-8 convention belongs to other blocks.
        if (usedBitsInLastByte < 1 || usedBitsInLastByte > 8) {
            throw new IOException("Invalid TZX direct-recording used-bits value: " + usedBitsInLastByte);
        }
        int length = cursor.readU24();
        return TapeBlock.directRecordingBlock(
                tStatesPerSample,
                usedBitsInLastByte,
                pauseAfterMillis,
                cursor.readBytes(length)
        );
    }

    private static TapeBlock readCswRecording(Cursor cursor) throws IOException {
        long blockLength = cursor.readU32Long();
        if (blockLength < 10) {
            throw new IOException("Invalid TZX CSW block length: " + blockLength);
        }
        long encodedLength = blockLength - 10;
        if (encodedLength > MAX_CSW_ENCODED_BYTES) {
            throw new IOException("TZX CSW data exceeds the 64 MiB safety limit");
        }

        int pauseAfterMillis = cursor.readU16();
        int samplingRateHz = cursor.readU24();
        if (samplingRateHz == 0) {
            throw new IOException("Invalid TZX CSW sampling rate: 0");
        }
        int compressionType = cursor.readU8();
        long declaredPulseCount = cursor.readU32Long();
        if (declaredPulseCount > MAX_CSW_PULSES) {
            throw new IOException("TZX CSW pulse count exceeds the 8000000-pulse safety limit");
        }
        long maximumRleLength = declaredPulseCount * 5;
        if (compressionType == 1
                && (encodedLength < declaredPulseCount || encodedLength > maximumRleLength)) {
            throw new IOException("TZX CSW RLE length is inconsistent with its declared pulse count");
        }
        if (compressionType == 2) {
            long maximumZrleLength = Math.max(64, maximumRleLength + (maximumRleLength / 16) + 64);
            if (encodedLength > maximumZrleLength) {
                throw new IOException("TZX CSW Z-RLE data is too large for its declared pulse count");
            }
        }

        byte[] encoded = cursor.readBytes(Math.toIntExact(encodedLength));
        byte[] rleData = switch (compressionType) {
            case 1 -> encoded;
            case 2 -> inflateZrle(encoded, Math.toIntExact(declaredPulseCount));
            default -> throw new IOException("Unsupported TZX CSW compression type: " + compressionType);
        };
        int[] pulses = decodeCswRle(rleData, Math.toIntExact(declaredPulseCount));
        return TapeBlock.cswRecordingBlock(samplingRateHz, pulses, pauseAfterMillis);
    }

    private static byte[] inflateZrle(byte[] encoded, int declaredPulseCount) throws IOException {
        long maximumDecodedLength = Math.min(
                (long) MAX_CSW_PULSES * 5,
                (long) declaredPulseCount * 5
        );
        Inflater inflater = new Inflater();
        inflater.setInput(encoded);
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(encoded.length * 2, 64 * 1024));
        byte[] buffer = new byte[8 * 1024];
        try {
            while (!inflater.finished()) {
                int count;
                try {
                    count = inflater.inflate(buffer);
                } catch (DataFormatException error) {
                    throw new IOException("Invalid TZX CSW Z-RLE stream", error);
                }
                if (count > 0) {
                    if ((long) output.size() + count > maximumDecodedLength) {
                        throw new IOException("TZX CSW Z-RLE stream exceeds its declared pulse bound");
                    }
                    output.write(buffer, 0, count);
                    continue;
                }
                if (inflater.finished()) {
                    break;
                }
                if (inflater.needsDictionary()) {
                    throw new IOException("TZX CSW Z-RLE stream requires an unsupported dictionary");
                }
                if (inflater.needsInput()) {
                    throw new IOException("Truncated TZX CSW Z-RLE stream");
                }
                throw new IOException("TZX CSW Z-RLE inflater made no progress");
            }
            if (inflater.getRemaining() != 0) {
                throw new IOException("Trailing data after TZX CSW Z-RLE stream");
            }
            return output.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static int[] decodeCswRle(byte[] rleData, int declaredPulseCount) throws IOException {
        int[] pulses = new int[declaredPulseCount];
        int inputIndex = 0;
        int pulseIndex = 0;
        while (inputIndex < rleData.length) {
            if (pulseIndex >= declaredPulseCount) {
                throw new IOException("TZX CSW pulse count exceeds the declared value " + declaredPulseCount);
            }

            int pulseLength = rleData[inputIndex++] & 0xFF;
            if (pulseLength == 0) {
                if (rleData.length - inputIndex < 4) {
                    throw new IOException("Truncated extended TZX CSW pulse length");
                }
                long extendedLength = (rleData[inputIndex] & 0xFFL)
                        | ((rleData[inputIndex + 1] & 0xFFL) << 8)
                        | ((rleData[inputIndex + 2] & 0xFFL) << 16)
                        | ((rleData[inputIndex + 3] & 0xFFL) << 24);
                inputIndex += 4;
                if (extendedLength == 0 || extendedLength > Integer.MAX_VALUE) {
                    throw new IOException("Invalid extended TZX CSW pulse length: " + extendedLength);
                }
                pulseLength = (int) extendedLength;
            }
            pulses[pulseIndex++] = pulseLength;
        }

        if (pulseIndex != declaredPulseCount) {
            throw new IOException(
                    "TZX CSW pulse count mismatch: declared " + declaredPulseCount + ", decoded " + pulseIndex
            );
        }
        return pulses;
    }

    private static TapeBlock readSetSignalLevel(Cursor cursor) throws IOException {
        long blockLength = cursor.readU32Long();
        if (blockLength != 1) {
            throw new IOException("Invalid TZX set-signal-level block length: " + blockLength);
        }

        int signalLevel = cursor.readU8();
        if (signalLevel > 1) {
            throw new IOException("Invalid TZX signal level: " + signalLevel);
        }
        return TapeBlock.signalLevelBlock(signalLevel == 1);
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

    private sealed interface ProgramBlock permits
            EmitBlock,
            PrefixPulsesBlock,
            PureDataBlock,
            NoOpBlock,
            JumpBlock,
            LoopStartBlock,
            LoopEndBlock,
            CallSequenceBlock,
            ReturnBlock,
            SelectBlock {
    }

    private record EmitBlock(TapeBlock block) implements ProgramBlock {
    }

    private record PrefixPulsesBlock(int[] pulses) implements ProgramBlock {
        private PrefixPulsesBlock {
            pulses = Arrays.copyOf(pulses, pulses.length);
        }
    }

    private record PureDataBlock(
            int zeroBitPulseLength,
            int oneBitPulseLength,
            int usedBitsInLastByte,
            int pauseAfterMillis,
            byte[] data
    ) implements ProgramBlock {
        private PureDataBlock {
            data = Arrays.copyOf(data, data.length);
        }

        private TapeBlock toTapeBlock(int[] prefixPulses) {
            return TapeBlock.dataBlock(
                    prefixPulses,
                    zeroBitPulseLength,
                    oneBitPulseLength,
                    usedBitsInLastByte,
                    pauseAfterMillis,
                    data
            );
        }
    }

    private enum NoOpBlock implements ProgramBlock {
        INSTANCE
    }

    private record JumpBlock(int relativeOffset) implements ProgramBlock {
    }

    private record LoopStartBlock(int repetitions) implements ProgramBlock {
    }

    private enum LoopEndBlock implements ProgramBlock {
        INSTANCE
    }

    private record CallSequenceBlock(int[] relativeOffsets) implements ProgramBlock {
        private CallSequenceBlock {
            relativeOffsets = Arrays.copyOf(relativeOffsets, relativeOffsets.length);
        }
    }

    private enum ReturnBlock implements ProgramBlock {
        INSTANCE
    }

    private record SelectBlock(List<SelectOption> options) implements ProgramBlock {
        private SelectBlock {
            options = List.copyOf(options);
        }
    }

    private static final class LoopFrame {
        private final int startBlockIndex;
        private final int endBlockIndex;
        private int remainingIterations;

        private LoopFrame(int startBlockIndex, int endBlockIndex, int remainingIterations) {
            this.startBlockIndex = startBlockIndex;
            this.endBlockIndex = endBlockIndex;
            this.remainingIterations = remainingIterations;
        }
    }

    private static final class CallFrame {
        private final int callBlockIndex;
        private final int[] relativeOffsets;
        private final int loopDepthAtCall;
        private int callIndex;

        private CallFrame(int callBlockIndex, int[] relativeOffsets, int loopDepthAtCall) {
            this.callBlockIndex = callBlockIndex;
            this.relativeOffsets = Arrays.copyOf(relativeOffsets, relativeOffsets.length);
            this.loopDepthAtCall = loopDepthAtCall;
        }

        private int targetForCurrentCall() {
            return callBlockIndex + relativeOffsets[callIndex];
        }

        private boolean hasNextCall() {
            return callIndex + 1 < relativeOffsets.length;
        }

        private void advance() {
            callIndex++;
        }
    }

    private static final class PulseAccumulator {
        private int[] pulses = new int[16];
        private int size;

        private void append(int[] additionalPulses, int limit, int programCounter) throws IOException {
            long requiredSize = (long) size + additionalPulses.length;
            if (requiredSize > limit) {
                throw new IOException(
                        "TZX pending-pulse budget exceeded at block #" + programCounter + ": " + limit
                );
            }
            ensureCapacity((int) requiredSize, limit);
            System.arraycopy(additionalPulses, 0, pulses, size, additionalPulses.length);
            size = (int) requiredSize;
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private int[] take() {
            int[] result = Arrays.copyOf(pulses, size);
            size = 0;
            return result;
        }

        private void ensureCapacity(int requiredSize, int limit) {
            if (requiredSize <= pulses.length) {
                return;
            }
            long doubled = Math.max(16L, (long) pulses.length * 2);
            int newSize = (int) Math.min(limit, Math.max(requiredSize, doubled));
            pulses = Arrays.copyOf(pulses, newSize);
        }
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

        int readS16() throws IOException {
            return (short) readU16();
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

        long readU32Long() throws IOException {
            return readU32() & 0xFFFF_FFFFL;
        }

        byte[] readBytes(int length) throws IOException {
            ensureAvailable(length);
            byte[] result = new byte[length];
            System.arraycopy(bytes, index, result, 0, length);
            index += length;
            return result;
        }

        Cursor readSubCursor(int length) throws IOException {
            return new Cursor(readBytes(length));
        }

        void skip(long length) throws IOException {
            ensureAvailable(length);
            index += (int) length;
        }

        private void ensureAvailable(long length) throws IOException {
            if (length < 0 || remaining() < length) {
                throw new IOException("Unexpected EOF in TZX block");
            }
        }
    }
}
