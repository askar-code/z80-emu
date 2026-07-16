package dev.z8emu.app.desktop;

import dev.z8emu.app.desktop.ProbeOutput.FrameProbeResult;
import dev.z8emu.machine.cpc.CpcKeyboardTyper;
import dev.z8emu.machine.cpc.CpcMachine;
import dev.z8emu.machine.cpc.device.CpcFdcDevice;
import dev.z8emu.machine.cpc.device.CpcKeyMap;
import dev.z8emu.machine.cpc.disk.CpcDskImage;
import dev.z8emu.machine.cpc.disk.CpcDskLoader;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.video.FrameBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static dev.z8emu.app.desktop.ProbeOutput.decodeScript;
import static dev.z8emu.app.desktop.ProbeOutput.frameCrc32;
import static dev.z8emu.app.desktop.ProbeOutput.hex16;
import static dev.z8emu.app.desktop.ProbeOutput.hex8;
import static dev.z8emu.app.desktop.ProbeOutput.isPcMatch;
import static dev.z8emu.app.desktop.ProbeOutput.parseAddresses;
import static dev.z8emu.app.desktop.ProbeOutput.parseCrc32;
import static dev.z8emu.app.desktop.ProbeOutput.printable;
import static dev.z8emu.app.desktop.ProbeOutput.printFrameResult;
import static dev.z8emu.app.desktop.ProbeOutput.printPcProfile;
import static dev.z8emu.app.desktop.ProbeOutput.writePng;

public final class CpcRomProbeLauncher {
    private static final long DEFAULT_MAX_INSTRUCTIONS = 3_000_000L;
    private static final long DEFAULT_KEYS_AFTER_FRAMES = 300;
    private static final String USAGE = "Usage: CpcRomProbeLauncher <rom-file> [max-instructions] [--disk=<dsk>] [--keys=<script>] [--keys-after-frames=<n>] [--press-key-after-frames=<n>:<char>] [--hold-key=<start-frame>:<hold-frames>:<name>] [--expect-frame-crc=<crc32>] [--dump-frame=<png>] [--stop-pc=<hex[,hex...]>] [--watch-addr=<hex[,hex...]>] [--profile-pc-top=<count>] [--trace-io] [--trace-fdc] [--trace-limit=<count>] [--trace-tail]";

    private CpcRomProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        ProbeConfig config;
        try {
            config = parseArgs(args);
        } catch (IllegalArgumentException malformedArguments) {
            config = null;
        }
        if (config == null) {
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        byte[] romImage = Files.readAllBytes(config.romPath());
        if (!CpcMachine.isSupportedCombinedRomSize(romImage.length)) {
            throw new IllegalArgumentException(
                    "CPC 6128 ROM bundle must be exactly 16 KB, 32 KB, or 48 KB: " + config.romPath()
            );
        }
        CpcDskImage diskImage = config.diskPath() == null ? null : CpcDskLoader.load(config.diskPath());
        CpcMachine machine = new CpcMachine(romImage, diskImage);
        TraceCollector traceCollector = new TraceCollector(machine, config.traceOptions());
        traceCollector.install();
        ProbeState state = new ProbeState(config.profilePcTop());

        try {
            runScheduledInput(machine, config, state);
            if (state.stopPc < 0 && state.steps < config.maxInstructions()) {
                runTail(machine, config, state);
            }

            traceCollector.pause();
            FrameProbeResult frameResult = renderFrame(
                    machine,
                    config.dumpFramePath(),
                    config.expectedFrameCrc32()
            );
            boolean frameExpectationFailed = config.expectedFrameCrc32() != null
                    && frameResult.crc32() != config.expectedFrameCrc32();
            System.out.println("status=" + status(state.stopPc, config.expectedFrameCrc32(), frameExpectationFailed));
            printProbeReport(machine, config, state, traceCollector, frameResult);
            if (frameExpectationFailed) {
                System.exit(1);
            }
        } catch (Throwable failure) {
            traceCollector.pause();
            System.out.println("status=failure");
            try {
                printProbeReport(
                        machine,
                        config,
                        state,
                        traceCollector,
                        renderFrame(machine, config.dumpFramePath(), config.expectedFrameCrc32())
                );
            } catch (Throwable reportFailure) {
                failure.addSuppressed(reportFailure);
            }
            System.out.println("failure=" + failure.getClass().getName() + ": " + failure.getMessage());
            rethrow(failure);
        }
    }

    private static ProbeConfig parseArgs(String[] args) {
        if (args.length == 0) {
            return null;
        }

        List<String> positional = new ArrayList<>(2);
        Path diskPath = null;
        String keyScriptArgument = null;
        long keysAfterFrames = DEFAULT_KEYS_AFTER_FRAMES;
        boolean keysAfterFramesSpecified = false;
        List<PressKey> pressKeys = new ArrayList<>();
        List<HoldKeyEvent> holdKeyEvents = new ArrayList<>();
        Long expectedFrameCrc32 = null;
        Path dumpFramePath = null;
        int[] stopPcs = new int[0];
        int[] watchAddrs = new int[0];
        int profilePcTop = 0;
        boolean traceIo = false;
        boolean traceFdc = false;
        int traceLimit = TraceOptions.DEFAULT_LIMIT;
        boolean traceTail = false;
        for (String arg : args) {
            if (arg.startsWith("--disk=")) {
                diskPath = normalizedPath(arg.substring("--disk=".length()));
            } else if (arg.startsWith("--keys=")) {
                keyScriptArgument = arg.substring("--keys=".length());
            } else if (arg.startsWith("--keys-after-frames=")) {
                keysAfterFrames = parseNonNegativeLong(arg.substring("--keys-after-frames=".length()));
                keysAfterFramesSpecified = true;
            } else if (arg.startsWith("--press-key-after-frames=")) {
                PressKey pressKey = parsePressKey(arg.substring("--press-key-after-frames=".length()));
                if (pressKey == null) {
                    return null;
                }
                pressKeys.add(pressKey);
            } else if (arg.startsWith("--hold-key=")) {
                HoldKey holdKey = parseHoldKey(arg.substring("--hold-key=".length()));
                if (holdKey == null) {
                    return null;
                }
                holdKeyEvents.add(new HoldKeyEvent(holdKey.startFrame(), holdKey.chord(), true));
                holdKeyEvents.add(new HoldKeyEvent(holdKey.endFrame(), holdKey.chord(), false));
            } else if (arg.startsWith("--expect-frame-crc=")) {
                expectedFrameCrc32 = parseCrc32(arg.substring("--expect-frame-crc=".length()));
            } else if (arg.startsWith("--dump-frame=")) {
                dumpFramePath = normalizedPath(arg.substring("--dump-frame=".length()));
            } else if (arg.startsWith("--stop-pc=")) {
                stopPcs = parseAddresses(arg.substring("--stop-pc=".length()));
            } else if (arg.startsWith("--watch-addr=")) {
                watchAddrs = parseAddresses(arg.substring("--watch-addr=".length()));
            } else if (arg.startsWith("--profile-pc-top=")) {
                profilePcTop = Integer.parseInt(arg.substring("--profile-pc-top=".length()));
                if (profilePcTop < 0) {
                    return null;
                }
            } else if (arg.equals("--trace-io")) {
                traceIo = true;
            } else if (arg.equals("--trace-fdc")) {
                traceFdc = true;
            } else if (arg.startsWith("--trace-limit=")) {
                traceLimit = Integer.parseInt(arg.substring("--trace-limit=".length()));
                if (traceLimit < 0) {
                    return null;
                }
            } else if (arg.equals("--trace-tail")) {
                traceTail = true;
            } else if (arg.startsWith("--")) {
                return null;
            } else {
                positional.add(arg);
            }
        }
        if (positional.isEmpty()
                || positional.size() > 2
                || (keysAfterFramesSpecified && keyScriptArgument == null)) {
            return null;
        }

        long maxInstructions = positional.size() == 2
                ? Long.parseLong(positional.get(1))
                : DEFAULT_MAX_INSTRUCTIONS;
        if (maxInstructions <= 0) {
            return null;
        }
        String keyScript = keyScriptArgument == null ? null : decodeScript(keyScriptArgument);
        pressKeys.sort(Comparator.comparingLong(PressKey::frame));
        holdKeyEvents.sort(Comparator.comparingLong(HoldKeyEvent::frame));
        return new ProbeConfig(
                normalizedPath(positional.get(0)),
                maxInstructions,
                diskPath,
                keyScript,
                keysAfterFrames,
                List.copyOf(pressKeys),
                List.copyOf(holdKeyEvents),
                expectedFrameCrc32,
                dumpFramePath,
                stopPcs,
                watchAddrs,
                profilePcTop,
                new TraceOptions(traceIo, traceFdc, traceLimit, traceTail)
        );
    }

    private static PressKey parsePressKey(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        long frame = parseNonNegativeLong(value.substring(0, separator));
        String rawScript = value.substring(separator + 1);
        String decoded = decodeScript(rawScript);
        if (decoded.length() != 1 || CpcKeyMap.forCharacterOrNull(decoded.charAt(0)) == null) {
            return null;
        }
        return new PressKey(frame, rawScript, decoded.charAt(0));
    }

    private static HoldKey parseHoldKey(String value) {
        int firstSeparator = value.indexOf(':');
        int secondSeparator = firstSeparator < 0 ? -1 : value.indexOf(':', firstSeparator + 1);
        if (firstSeparator <= 0 || secondSeparator <= firstSeparator + 1) {
            return null;
        }
        long startFrame = parseNonNegativeLong(value.substring(0, firstSeparator));
        long holdFrames = Long.parseLong(value.substring(firstSeparator + 1, secondSeparator));
        if (holdFrames <= 0) {
            return null;
        }
        CpcKeyMap.KeyChord chord = CpcKeyMap.forNameOrNull(value.substring(secondSeparator + 1));
        if (chord == null) {
            return null;
        }
        try {
            return new HoldKey(startFrame, Math.addExact(startFrame, holdFrames), chord);
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    private static long parseNonNegativeLong(String value) {
        long parsed = Long.parseLong(value);
        if (parsed < 0) {
            throw new IllegalArgumentException("Negative frame count");
        }
        return parsed;
    }

    private static Path normalizedPath(String value) {
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static void runScheduledInput(CpcMachine machine, ProbeConfig config, ProbeState state) {
        boolean keysPending = config.keyScript() != null;
        int pressKeyIndex = 0;
        int holdKeyEventIndex = 0;
        while ((keysPending
                || pressKeyIndex < config.pressKeys().size()
                || holdKeyEventIndex < config.holdKeyEvents().size())
                && state.stopPc < 0
                && state.steps < config.maxInstructions()) {
            long nextKeysFrame = keysPending ? config.keysAfterFrames() : Long.MAX_VALUE;
            long nextPressFrame = pressKeyIndex < config.pressKeys().size()
                    ? config.pressKeys().get(pressKeyIndex).frame()
                    : Long.MAX_VALUE;
            long nextHoldKeyEventFrame = holdKeyEventIndex < config.holdKeyEvents().size()
                    ? config.holdKeyEvents().get(holdKeyEventIndex).frame()
                    : Long.MAX_VALUE;
            runUntilFrame(
                    machine,
                    config,
                    state,
                    Math.min(nextKeysFrame, Math.min(nextPressFrame, nextHoldKeyEventFrame))
            );
            if (state.stopPc >= 0 || state.steps >= config.maxInstructions()) {
                break;
            }

            while (holdKeyEventIndex < config.holdKeyEvents().size()
                    && config.holdKeyEvents().get(holdKeyEventIndex).frame() <= state.framesRun) {
                HoldKeyEvent event = config.holdKeyEvents().get(holdKeyEventIndex++);
                setChordState(machine, event.chord(), event.pressed());
                state.holdKeyEventsApplied++;
            }

            if (keysPending && config.keysAfterFrames() <= state.framesRun) {
                keysPending = false;
                state.keysTypedAtFrame = state.framesRun;
                typeScript(machine, config, state, config.keyScript(), true);
            }
            while (pressKeyIndex < config.pressKeys().size()
                    && config.pressKeys().get(pressKeyIndex).frame() <= state.framesRun
                    && state.stopPc < 0
                    && state.steps < config.maxInstructions()) {
                PressKey pressKey = config.pressKeys().get(pressKeyIndex++);
                typeScript(machine, config, state, String.valueOf(pressKey.character()), false);
                state.pressKeysTyped++;
            }
        }
    }

    private static void setChordState(CpcMachine machine, CpcKeyMap.KeyChord chord, boolean pressed) {
        for (CpcKeyMap.MatrixKey key : chord.keys()) {
            machine.board().keyboard().setKeyPressed(key.line(), key.bit(), pressed);
        }
    }

    private static void typeScript(
            CpcMachine machine,
            ProbeConfig config,
            ProbeState state,
            String script,
            boolean countKeys
    ) {
        CpcKeyboardTyper.FrameRunner frameRunner = frames -> runFrames(machine, config, state, frames);
        for (int keyIndex = 0; keyIndex < script.length(); keyIndex++) {
            if (state.stopPc >= 0 || state.steps >= config.maxInstructions()) {
                return;
            }
            long startFrame = state.framesRun;
            CpcKeyboardTyper.typeCharacter(machine, script.charAt(keyIndex), frameRunner);
            if (state.framesRun - startFrame < CpcKeyboardTyper.PRESS_FRAMES + CpcKeyboardTyper.GAP_FRAMES) {
                return;
            }
            if (countKeys) {
                state.keysTyped++;
            }
        }
    }

    private static void runUntilFrame(
            CpcMachine machine,
            ProbeConfig config,
            ProbeState state,
            long targetFrame
    ) {
        while (state.framesRun < targetFrame
                && state.stopPc < 0
                && state.steps < config.maxInstructions()) {
            runFrames(machine, config, state, 1);
        }
    }

    private static long runFrames(
            CpcMachine machine,
            ProbeConfig config,
            ProbeState state,
            int frames
    ) {
        long startSteps = state.steps;
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            long targetTState = machine.currentTState() + machine.frameTStates();
            while (machine.currentTState() < targetTState) {
                if (!runInstruction(machine, config, state)) {
                    return state.steps - startSteps;
                }
            }
            state.framesRun++;
        }
        return state.steps - startSteps;
    }

    private static void runTail(CpcMachine machine, ProbeConfig config, ProbeState state) {
        while (state.steps < config.maxInstructions() && state.stopPc < 0) {
            runFrames(machine, config, state, 1);
        }
    }

    private static boolean runInstruction(CpcMachine machine, ProbeConfig config, ProbeState state) {
        if (state.steps >= config.maxInstructions()) {
            return false;
        }
        int pc = machine.cpu().registers().pc();
        if (isPcMatch(pc, config.stopPcs())) {
            state.stopPc = pc;
            return false;
        }
        if (state.pcHits != null) {
            state.pcHits[pc]++;
        }
        state.lastExecutedPc = pc;
        state.lastExecutedOpcode = machine.board().cpuBus().readMemory(pc);
        machine.runInstruction();
        state.steps++;
        return true;
    }

    private static String status(int stopPc, Long expectedFrameCrc32, boolean frameExpectationFailed) {
        if (frameExpectationFailed) {
            return stopPc >= 0 ? "stop-pc-reached-frame-expectation-not-met" : "frame-expectation-not-met";
        }
        if (expectedFrameCrc32 != null) {
            return "frame-expectation-met";
        }
        if (stopPc >= 0) {
            return "stop-pc-reached";
        }
        return "max-instructions-reached";
    }

    private static void printProbeReport(
            CpcMachine machine,
            ProbeConfig config,
            ProbeState state,
            TraceCollector traceCollector,
            FrameProbeResult frameResult
    ) {
        int pc = machine.cpu().registers().pc();
        int opcode = machine.board().cpuBus().readMemory(pc);
        System.out.println("source=" + config.romPath());
        if (config.diskPath() != null) {
            System.out.println("diskSource=" + config.diskPath());
        }
        System.out.println("steps=" + state.steps);
        System.out.println("framesRun=" + state.framesRun);
        System.out.println("keyScript=" + (config.keyScript() == null ? "absent" : printable(config.keyScript())));
        System.out.println("keysTyped=" + state.keysTyped + "/" + (config.keyScript() == null ? 0 : config.keyScript().length()));
        System.out.println("keysTypedAtFrame=" + (state.keysTypedAtFrame < 0 ? "absent" : state.keysTypedAtFrame));
        System.out.println("pressKeys=" + printablePressKeys(config.pressKeys()));
        System.out.println("pressKeysTyped=" + state.pressKeysTyped + "/" + config.pressKeys().size());
        System.out.println("holdKeyEvents=" + state.holdKeyEventsApplied + "/" + config.holdKeyEvents().size());
        System.out.println("pc=0x" + hex16(pc));
        System.out.println("opcode=0x" + hex8(opcode));
        if (state.lastExecutedPc >= 0) {
            System.out.println("lastPc=0x" + hex16(state.lastExecutedPc));
            System.out.println("lastOpcode=0x" + hex8(state.lastExecutedOpcode));
        }
        System.out.println("af=0x" + hex16(machine.cpu().registers().af()));
        System.out.println("bc=0x" + hex16(machine.cpu().registers().bc()));
        System.out.println("de=0x" + hex16(machine.cpu().registers().de()));
        System.out.println("hl=0x" + hex16(machine.cpu().registers().hl()));
        System.out.println("ix=0x" + hex16(machine.cpu().registers().ix()));
        System.out.println("iy=0x" + hex16(machine.cpu().registers().iy()));
        System.out.println("sp=0x" + hex16(machine.cpu().registers().sp()));
        System.out.println("t=" + machine.currentTState());

        System.out.println("bytesAroundPc:");
        for (int address = pc - 16; address <= pc + 16; address++) {
            int normalized = address & 0xFFFF;
            System.out.println(hex16(normalized) + ": " + hex8(machine.board().cpuBus().readMemory(normalized)));
        }
        if (config.watchAddrs().length > 0) {
            System.out.println("watchedBytes:");
            for (int address : config.watchAddrs()) {
                System.out.println(hex16(address) + ": " + hex8(machine.board().cpuBus().readMemory(address)));
            }
        }
        if (state.stopPc >= 0) {
            System.out.println("stopPc=0x" + hex16(state.stopPc));
        }
        printPcProfile(state.pcHits, config.profilePcTop());
        traceCollector.print();
        printFrameResult(frameResult);
    }

    private static String printablePressKeys(List<PressKey> pressKeys) {
        if (pressKeys.isEmpty()) {
            return "absent";
        }
        StringBuilder result = new StringBuilder();
        for (PressKey pressKey : pressKeys) {
            if (!result.isEmpty()) {
                result.append(',');
            }
            result.append(pressKey.frame()).append(':').append(printable(pressKey.rawScript()));
        }
        return result.toString();
    }

    private static FrameProbeResult renderFrame(
            CpcMachine machine,
            Path dumpFramePath,
            Long expectedFrameCrc32
    ) throws IOException {
        FrameBuffer frame = machine.board().renderVideoFrame();
        if (dumpFramePath != null) {
            Path parent = dumpFramePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writePng(frame, dumpFramePath);
        }
        return new FrameProbeResult(
                dumpFramePath,
                frame.width(),
                frame.height(),
                frameCrc32(frame),
                expectedFrameCrc32
        );
    }

    private static void rethrow(Throwable failure) throws IOException {
        if (failure instanceof IOException ioFailure) {
            throw ioFailure;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IOException(failure);
    }

    private static final class ProbeState {
        private long steps;
        private long framesRun;
        private int stopPc = -1;
        private int lastExecutedPc = -1;
        private int lastExecutedOpcode = -1;
        private final long[] pcHits;
        private int keysTyped;
        private long keysTypedAtFrame = -1;
        private int pressKeysTyped;
        private int holdKeyEventsApplied;

        private ProbeState(int profilePcTop) {
            pcHits = profilePcTop > 0 ? new long[0x10000] : null;
        }
    }

    private static final class TraceCollector implements CpcFdcDevice.TraceSink {
        private final CpcMachine machine;
        private final TraceOptions options;
        private final List<String> events = new ArrayList<>();
        private long dropped;
        private int nextTailIndex;
        private boolean recording = true;

        private TraceCollector(CpcMachine machine, TraceOptions options) {
            this.machine = machine;
            this.options = options;
        }

        void install() {
            if (options.traceIo()) {
                machine.board().setIoTraceSink(this::recordIo);
            }
            if (options.traceFdc()) {
                machine.board().fdc().setTraceSink(this);
            }
        }

        void pause() {
            recording = false;
        }

        private void recordIo(String mappingName, boolean read, IoAccess access, int value) {
            if (!recording) {
                return;
            }
            record("io|t=%d pc=0x%s %s name=%s addr=0x%s off=0x%s value=0x%s".formatted(
                    access.tState(),
                    hex16(machine.cpu().registers().pc()),
                    read ? "R" : "W",
                    mappingName,
                    hex16(access.address()),
                    hex16(access.offset()),
                    hex8(value)
            ));
        }

        @Override
        public void command(int commandByte, int[] args) {
            if (!recording) {
                return;
            }
            StringBuilder line = new StringBuilder();
            line.append("fdc|t=").append(machine.currentTState())
                    .append(" pc=0x").append(hex16(machine.cpu().registers().pc()))
                    .append(" command=0x").append(hex8(commandByte))
                    .append(" args=");
            for (int index = 0; index < args.length; index++) {
                if (index > 0) {
                    line.append(',');
                }
                line.append("0x").append(hex8(args[index]));
            }
            record(line.toString());
        }

        @Override
        public void result(int commandByte, int st0) {
            if (!recording) {
                return;
            }
            record("fdc|t=%d pc=0x%s result command=0x%s st0=0x%s".formatted(
                    machine.currentTState(),
                    hex16(machine.cpu().registers().pc()),
                    hex8(commandByte),
                    hex8(st0)
            ));
        }

        private void record(String line) {
            if (options.limit() == 0) {
                dropped++;
                return;
            }
            if (events.size() < options.limit()) {
                events.add(line);
            } else if (options.tail()) {
                events.set(nextTailIndex, line);
                nextTailIndex = (nextTailIndex + 1) % options.limit();
                dropped++;
            } else {
                dropped++;
            }
        }

        void print() {
            if (!options.traceIo() && !options.traceFdc()) {
                return;
            }
            System.out.println("traceEvents=" + events.size());
            if (dropped > 0) {
                System.out.println("traceDropped=" + dropped);
            }
            if (options.tail()) {
                System.out.println("traceMode=tail");
            }
            System.out.println("trace:");
            int start = options.tail() && dropped > 0 ? nextTailIndex : 0;
            for (int index = 0; index < events.size(); index++) {
                int eventIndex = (start + index) % events.size();
                System.out.println("%04d|%s".formatted(index + 1, events.get(eventIndex)));
            }
        }
    }

    private record ProbeConfig(
            Path romPath,
            long maxInstructions,
            Path diskPath,
            String keyScript,
            long keysAfterFrames,
            List<PressKey> pressKeys,
            List<HoldKeyEvent> holdKeyEvents,
            Long expectedFrameCrc32,
            Path dumpFramePath,
            int[] stopPcs,
            int[] watchAddrs,
            int profilePcTop,
            TraceOptions traceOptions
    ) {
    }

    private record PressKey(long frame, String rawScript, char character) {
    }

    private record HoldKey(long startFrame, long endFrame, CpcKeyMap.KeyChord chord) {
    }

    private record HoldKeyEvent(long frame, CpcKeyMap.KeyChord chord, boolean pressed) {
    }

    private record TraceOptions(boolean traceIo, boolean traceFdc, int limit, boolean tail) {
        private static final int DEFAULT_LIMIT = 256;
    }
}
