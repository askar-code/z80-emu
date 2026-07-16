package dev.z8emu.app.desktop;

import dev.z8emu.app.desktop.ProbeOutput.FrameProbeResult;
import dev.z8emu.machine.c64.C64KeyboardTyper;
import dev.z8emu.machine.c64.C64Machine;
import dev.z8emu.machine.c64.C64PrgLoader;
import dev.z8emu.machine.c64.C64ScreenText;
import dev.z8emu.machine.c64.media.C64PrgImage;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.video.FrameBuffer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.z8emu.app.desktop.ProbeOutput.countVisibleCharacters;
import static dev.z8emu.app.desktop.ProbeOutput.decodeScript;
import static dev.z8emu.app.desktop.ProbeOutput.frameCrc32;
import static dev.z8emu.app.desktop.ProbeOutput.hex16;
import static dev.z8emu.app.desktop.ProbeOutput.hex8;
import static dev.z8emu.app.desktop.ProbeOutput.isPcMatch;
import static dev.z8emu.app.desktop.ProbeOutput.parseAddress;
import static dev.z8emu.app.desktop.ProbeOutput.parseAddresses;
import static dev.z8emu.app.desktop.ProbeOutput.parseCrc32;
import static dev.z8emu.app.desktop.ProbeOutput.printable;
import static dev.z8emu.app.desktop.ProbeOutput.printFrameResult;
import static dev.z8emu.app.desktop.ProbeOutput.printPcProfile;
import static dev.z8emu.app.desktop.ProbeOutput.writePng;

public final class C64RomProbeLauncher {
    private static final long DEFAULT_MAX_INSTRUCTIONS = 10_000_000L;

    private C64RomProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        ProbeConfig config = parseArgs(args);
        if (config == null) {
            System.err.println("Usage: C64RomProbeLauncher <rom-dir-or-rom-file> [max-instructions] [--keys=<script>] [--type-after-screen=<text>] [--prg=<path>] [--prg-sys=<hex-addr>] [--expect-screen=<text>] [--dump-frame=<png>] [--expect-frame-crc=<crc32>] [--stop-pc=<hex[,hex...]>] [--watch-addr=<hex[,hex...]>] [--profile-pc-top=<count>] [--trace-io] [--trace-limit=<count>] [--trace-tail]");
            System.exit(2);
            return;
        }

        C64RomImageLoader.C64RomSet roms = C64RomImageLoader.load(config.romPath());
        C64PrgImage prgImage = config.prgPath() == null ? null : C64PrgImage.load(config.prgPath());
        C64Machine machine = new C64Machine(roms.basic(), roms.kernal(), roms.chargen());
        TraceCollector traceCollector = new TraceCollector(machine, config.traceOptions());
        traceCollector.install();
        String keyScript = config.keyScript() == null ? null : decodeScript(config.keyScript());
        String typeAfterScreen = config.typeAfterScreen() == null ? null : decodeScript(config.typeAfterScreen());
        String expectedScreen = config.expectedScreen() == null ? null : decodeScript(config.expectedScreen());
        String prgCommand = prgImage == null ? null : C64PrgLoader.startCommand(prgImage, config.prgSysAddress());
        ProbeState state = new ProbeState(config.profilePcTop());

        try {
            if (keyScript != null && typeAfterScreen != null) {
                state.typeAfterScreenFound = runUntilScreenText(machine, config, state, typeAfterScreen);
                if (!state.typeAfterScreenFound && state.steps >= config.maxInstructions()) {
                    reportFailureAndExit(
                            machine,
                            config,
                            roms,
                            state,
                            keyScript,
                            typeAfterScreen,
                            prgImage,
                            prgCommand,
                            expectedScreen,
                            traceCollector,
                            "type-screen-not-found"
                    );
                    return;
                }
            }

            if (prgImage != null) {
                state.readyFound = runUntilScreenText(machine, config, state, "READY.");
                if (!state.readyFound && state.steps >= config.maxInstructions()) {
                    reportFailureAndExit(
                            machine,
                            config,
                            roms,
                            state,
                            keyScript,
                            typeAfterScreen,
                            prgImage,
                            prgCommand,
                            expectedScreen,
                            traceCollector,
                            "ready-not-found"
                    );
                    return;
                }

                if (state.readyFound && state.stopPc < 0) {
                    C64PrgLoader.inject(machine, prgImage);
                    for (int keyIndex = 0; keyIndex < prgCommand.length(); keyIndex++) {
                        state.steps += C64KeyboardTyper.typeCharacter(machine, prgCommand.charAt(keyIndex));
                    }
                }
            }

            if (keyScript != null
                    && (typeAfterScreen == null || state.typeAfterScreenFound)
                    && state.stopPc < 0) {
                for (int keyIndex = 0; keyIndex < keyScript.length(); keyIndex++) {
                    state.steps += C64KeyboardTyper.typeCharacter(machine, keyScript.charAt(keyIndex));
                    state.keysTyped++;
                }
            }

            if (state.stopPc < 0) {
                state.expectationMet = runUntilScreenText(machine, config, state, expectedScreen);
            }

            traceCollector.pause();
            FrameProbeResult frameResult = renderFrame(
                    machine,
                    config.dumpFramePath(),
                    config.expectedFrameCrc32()
            );
            boolean screenExpectationFailed = expectedScreen != null && !state.expectationMet;
            boolean frameExpectationFailed = config.expectedFrameCrc32() != null
                    && frameResult.crc32() != config.expectedFrameCrc32();
            System.out.println("status=" + status(
                    state.expectationMet,
                    state.stopPc,
                    config.expectedFrameCrc32(),
                    screenExpectationFailed,
                    frameExpectationFailed
            ));
            printProbeReport(
                    machine,
                    config,
                    roms,
                    state,
                    keyScript,
                    typeAfterScreen,
                    prgImage,
                    prgCommand,
                    expectedScreen,
                    traceCollector,
                    frameResult
            );
            if (screenExpectationFailed || frameExpectationFailed) {
                System.exit(1);
            }
        } catch (Throwable failure) {
            traceCollector.pause();
            System.out.println("status=failure");
            try {
                printProbeReport(
                        machine,
                        config,
                        roms,
                        state,
                        keyScript,
                        typeAfterScreen,
                        prgImage,
                        prgCommand,
                        expectedScreen,
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
        String keyScript = null;
        String typeAfterScreen = null;
        Path prgPath = null;
        Integer prgSysAddress = null;
        String expectedScreen = null;
        Path dumpFramePath = null;
        Long expectedFrameCrc32 = null;
        int[] stopPcs = new int[0];
        int[] watchAddrs = new int[0];
        int profilePcTop = 0;
        boolean traceIo = false;
        int traceLimit = TraceOptions.DEFAULT_LIMIT;
        boolean traceTail = false;
        for (String arg : args) {
            if (arg.startsWith("--keys=")) {
                keyScript = arg.substring("--keys=".length());
            } else if (arg.startsWith("--type-after-screen=")) {
                typeAfterScreen = arg.substring("--type-after-screen=".length());
            } else if (arg.startsWith("--prg=")) {
                prgPath = Path.of(arg.substring("--prg=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--prg-sys=")) {
                prgSysAddress = parseAddress(arg.substring("--prg-sys=".length()));
            } else if (arg.startsWith("--expect-screen=")) {
                expectedScreen = arg.substring("--expect-screen=".length());
            } else if (arg.startsWith("--dump-frame=")) {
                dumpFramePath = Path.of(arg.substring("--dump-frame=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--expect-frame-crc=")) {
                expectedFrameCrc32 = parseCrc32(arg.substring("--expect-frame-crc=".length()));
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
                || (typeAfterScreen != null && keyScript == null)
                || (prgPath != null && (keyScript != null || typeAfterScreen != null))
                || (prgSysAddress != null && prgPath == null)) {
            return null;
        }
        long maxInstructions = positional.size() == 2
                ? Long.parseLong(positional.get(1))
                : DEFAULT_MAX_INSTRUCTIONS;
        return new ProbeConfig(
                Path.of(positional.get(0)).toAbsolutePath().normalize(),
                maxInstructions,
                keyScript,
                typeAfterScreen,
                prgPath,
                prgSysAddress,
                expectedScreen,
                dumpFramePath,
                expectedFrameCrc32,
                stopPcs,
                watchAddrs,
                profilePcTop,
                new TraceOptions(traceIo, traceLimit, traceTail)
        );
    }

    private static String status(
            boolean expectationMet,
            int stopPc,
            Long expectedFrameCrc32,
            boolean screenExpectationFailed,
            boolean frameExpectationFailed
    ) {
        if (screenExpectationFailed) {
            return stopPc >= 0 ? "stop-pc-reached-expectation-not-met" : "expectation-not-met";
        }
        if (frameExpectationFailed) {
            return stopPc >= 0 ? "stop-pc-reached-frame-expectation-not-met" : "frame-expectation-not-met";
        }
        if (expectationMet) {
            return "expectation-met";
        }
        if (expectedFrameCrc32 != null) {
            return "frame-expectation-met";
        }
        if (stopPc >= 0) {
            return "stop-pc-reached";
        }
        return "max-instructions-reached";
    }

    private static boolean runUntilScreenText(
            C64Machine machine,
            ProbeConfig config,
            ProbeState state,
            String awaitedScreenTextOrNull
    ) {
        while (state.steps < config.maxInstructions()) {
            int pc = machine.cpu().registers().pc();
            if (isPcMatch(pc, config.stopPcs())) {
                state.stopPc = pc;
                break;
            }
            if (state.pcHits != null) {
                state.pcHits[pc]++;
            }
            state.lastExecutedPc = pc;
            state.lastExecutedOpcode = machine.board().cpuBus().readMemory(pc);
            machine.runInstruction();
            state.steps++;

            if (awaitedScreenTextOrNull != null
                    && (state.steps % 100) == 0
                    && C64ScreenText.contains(machine, awaitedScreenTextOrNull)) {
                return true;
            }
        }
        return false;
    }

    private static void reportFailureAndExit(
            C64Machine machine,
            ProbeConfig config,
            C64RomImageLoader.C64RomSet roms,
            ProbeState state,
            String keyScript,
            String typeAfterScreen,
            C64PrgImage prgImage,
            String prgCommand,
            String expectedScreen,
            TraceCollector traceCollector,
            String status
    ) throws IOException {
        traceCollector.pause();
        FrameProbeResult frameResult = renderFrame(
                machine,
                config.dumpFramePath(),
                config.expectedFrameCrc32()
        );
        System.out.println("status=" + status);
        printProbeReport(
                machine,
                config,
                roms,
                state,
                keyScript,
                typeAfterScreen,
                prgImage,
                prgCommand,
                expectedScreen,
                traceCollector,
                frameResult
        );
        System.exit(1);
    }

    private static void printProbeReport(
            C64Machine machine,
            ProbeConfig config,
            C64RomImageLoader.C64RomSet roms,
            ProbeState state,
            String keyScript,
            String typeAfterScreen,
            C64PrgImage prgImage,
            String prgCommand,
            String expectedScreen,
            TraceCollector traceCollector,
            FrameProbeResult frameResult
    ) {
        int pc = machine.cpu().registers().pc();
        int opcode = machine.board().cpuBus().readMemory(pc);
        String[] visibleLines = C64ScreenText.visibleLines(machine);
        String visibleText = String.join("\n", visibleLines);
        int d018 = machine.board().video().readRegister(0x18);
        int vicBank = (~machine.board().cia2().readRegister(0x00)) & 0x03;
        int matrix = vicBank * 0x4000 + ((d018 >> 4) & 0x0F) * 0x400;

        System.out.println("source=" + config.romPath());
        System.out.println("basicBytes=" + roms.basic().length);
        System.out.println("kernalBytes=" + roms.kernal().length);
        System.out.println("chargenBytes=" + roms.chargen().length);
        System.out.println("steps=" + state.steps);
        System.out.println("keysTyped=" + state.keysTyped + "/" + (keyScript == null ? 0 : keyScript.length()));
        if (typeAfterScreen != null) {
            System.out.println("typeAfterScreen=" + printable(typeAfterScreen));
        }
        System.out.println("typeAfterScreenFound=" + state.typeAfterScreenFound);
        if (prgImage != null) {
            System.out.println("prgSource=" + config.prgPath());
            System.out.println("prgLoadAddress=0x" + hex16(prgImage.loadAddress()));
            System.out.println("prgBytes=" + prgImage.payload().length);
            System.out.println("prgCommand=" + printable(prgCommand));
            System.out.println("readyFound=" + state.readyFound);
        }
        System.out.println("pc=0x" + hex16(pc));
        System.out.println("opcode=0x" + hex8(opcode));
        if (state.lastExecutedPc >= 0) {
            System.out.println("lastPc=0x" + hex16(state.lastExecutedPc));
            System.out.println("lastOpcode=0x" + hex8(state.lastExecutedOpcode));
        }
        System.out.println("a=0x" + hex8(machine.cpu().registers().a()));
        System.out.println("x=0x" + hex8(machine.cpu().registers().x()));
        System.out.println("y=0x" + hex8(machine.cpu().registers().y()));
        System.out.println("sp=0x" + hex8(machine.cpu().registers().sp()));
        System.out.println("p=0x" + hex8(machine.cpu().registers().p()));
        System.out.println("t=" + machine.currentTState());
        System.out.println("rasterLine=" + machine.board().video().rasterLine());
        System.out.println("vicBank=" + vicBank);
        System.out.println("screenMatrix=0x" + hex16(matrix));
        if (expectedScreen != null) {
            System.out.println("expectScreen=" + printable(expectedScreen));
            System.out.println("expectScreenFound=" + visibleText.contains(expectedScreen));
        }
        System.out.println("visibleChars=" + countVisibleCharacters(visibleLines));
        System.out.println("visibleCrc32=0x" + ProbeOutput.crc32Hex(visibleText.getBytes(StandardCharsets.US_ASCII)));
        System.out.println("screen:");
        for (int row = 0; row < visibleLines.length; row++) {
            System.out.println("%02d|%s".formatted(row, visibleLines[row]));
        }

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

    private static FrameProbeResult renderFrame(
            C64Machine machine,
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
        private int stopPc = -1;
        private int lastExecutedPc = -1;
        private int lastExecutedOpcode = -1;
        private final long[] pcHits;
        private int keysTyped;
        private boolean typeAfterScreenFound;
        private boolean readyFound;
        private boolean expectationMet;

        private ProbeState(int profilePcTop) {
            pcHits = profilePcTop > 0 ? new long[0x10000] : null;
        }
    }

    private static final class TraceCollector {
        private final C64Machine machine;
        private final TraceOptions options;
        private final List<String> events = new ArrayList<>();
        private long dropped;
        private int nextTailIndex;
        private boolean recording = true;

        private TraceCollector(C64Machine machine, TraceOptions options) {
            this.machine = machine;
            this.options = options;
        }

        void install() {
            if (options.traceIo()) {
                machine.board().setIoTraceSink(this::recordIo);
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
            if (!options.traceIo()) {
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
            for (int i = 0; i < events.size(); i++) {
                int eventIndex = (start + i) % events.size();
                System.out.println("%04d|%s".formatted(i + 1, events.get(eventIndex)));
            }
        }
    }

    private record ProbeConfig(
            Path romPath,
            long maxInstructions,
            String keyScript,
            String typeAfterScreen,
            Path prgPath,
            Integer prgSysAddress,
            String expectedScreen,
            Path dumpFramePath,
            Long expectedFrameCrc32,
            int[] stopPcs,
            int[] watchAddrs,
            int profilePcTop,
            TraceOptions traceOptions
    ) {
    }

    private record TraceOptions(boolean traceIo, int limit, boolean tail) {
        private static final int DEFAULT_LIMIT = 256;
    }

}
