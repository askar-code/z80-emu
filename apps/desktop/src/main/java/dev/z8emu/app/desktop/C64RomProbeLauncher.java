package dev.z8emu.app.desktop;

import dev.z8emu.machine.c64.C64KeyboardTyper;
import dev.z8emu.machine.c64.C64Machine;
import dev.z8emu.machine.c64.device.C64VideoDevice;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.video.FrameBuffer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

import static dev.z8emu.app.desktop.ProbeOutput.countVisibleCharacters;
import static dev.z8emu.app.desktop.ProbeOutput.hex16;
import static dev.z8emu.app.desktop.ProbeOutput.hex8;
import static dev.z8emu.app.desktop.ProbeOutput.writePng;

public final class C64RomProbeLauncher {
    private static final long DEFAULT_MAX_INSTRUCTIONS = 10_000_000L;

    private C64RomProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        ProbeConfig config = parseArgs(args);
        if (config == null) {
            System.err.println("Usage: C64RomProbeLauncher <rom-dir-or-rom-file> [max-instructions] [--keys=<script>] [--type-after-screen=<text>] [--expect-screen=<text>] [--dump-frame=<png>] [--expect-frame-crc=<crc32>] [--stop-pc=<hex[,hex...]>] [--watch-addr=<hex[,hex...]>] [--profile-pc-top=<count>] [--trace-io] [--trace-limit=<count>] [--trace-tail]");
            System.exit(2);
            return;
        }

        C64RomImageLoader.C64RomSet roms = C64RomImageLoader.load(config.romPath());
        C64Machine machine = new C64Machine(roms.basic(), roms.kernal(), roms.chargen());
        TraceCollector traceCollector = new TraceCollector(machine, config.traceOptions());
        traceCollector.install();
        String keyScript = config.keyScript() == null ? null : decodeScript(config.keyScript());
        String typeAfterScreen = config.typeAfterScreen() == null ? null : decodeScript(config.typeAfterScreen());
        String expectedScreen = config.expectedScreen() == null ? null : decodeScript(config.expectedScreen());
        int keysTyped = 0;
        boolean typeAfterScreenFound = false;
        boolean expectationMet = false;
        int stopPc = -1;
        long[] pcHits = config.profilePcTop() > 0 ? new long[0x10000] : null;
        int lastExecutedPc = -1;
        int lastExecutedOpcode = -1;
        long steps = 0;

        try {
            if (keyScript != null && typeAfterScreen != null) {
                while (steps < config.maxInstructions()) {
                    int pc = machine.cpu().registers().pc();
                    if (isPcMatch(pc, config.stopPcs())) {
                        stopPc = pc;
                        break;
                    }
                    if (pcHits != null) {
                        pcHits[pc]++;
                    }
                    lastExecutedPc = pc;
                    lastExecutedOpcode = machine.board().cpuBus().readMemory(pc);
                    machine.runInstruction();
                    steps++;

                    if ((steps % 100) == 0 && screenContains(machine, typeAfterScreen)) {
                        typeAfterScreenFound = true;
                        break;
                    }
                }

                if (!typeAfterScreenFound && steps >= config.maxInstructions()) {
                    traceCollector.pause();
                    FrameProbeResult frameResult = renderFrame(
                            machine,
                            config.dumpFramePath(),
                            config.expectedFrameCrc32()
                    );
                    System.out.println("status=type-screen-not-found");
                    printProbeReport(
                            machine,
                            config,
                            roms,
                            steps,
                            keyScript,
                            keysTyped,
                            typeAfterScreen,
                            typeAfterScreenFound,
                            expectedScreen,
                            lastExecutedPc,
                            lastExecutedOpcode,
                            stopPc,
                            pcHits,
                            traceCollector,
                            frameResult
                    );
                    System.exit(1);
                    return;
                }
            }

            if (keyScript != null && (typeAfterScreen == null || typeAfterScreenFound) && stopPc < 0) {
                for (int keyIndex = 0; keyIndex < keyScript.length(); keyIndex++) {
                    steps += C64KeyboardTyper.typeCharacter(machine, keyScript.charAt(keyIndex));
                    keysTyped++;
                }
            }

            while (steps < config.maxInstructions() && stopPc < 0) {
                int pc = machine.cpu().registers().pc();
                if (isPcMatch(pc, config.stopPcs())) {
                    stopPc = pc;
                    break;
                }
                if (pcHits != null) {
                    pcHits[pc]++;
                }
                lastExecutedPc = pc;
                lastExecutedOpcode = machine.board().cpuBus().readMemory(pc);
                machine.runInstruction();
                steps++;

                if (expectedScreen != null && (steps % 100) == 0 && screenContains(machine, expectedScreen)) {
                    expectationMet = true;
                    break;
                }
            }

            traceCollector.pause();
            FrameProbeResult frameResult = renderFrame(
                    machine,
                    config.dumpFramePath(),
                    config.expectedFrameCrc32()
            );
            boolean screenExpectationFailed = expectedScreen != null && !expectationMet;
            boolean frameExpectationFailed = config.expectedFrameCrc32() != null
                    && frameResult.crc32() != config.expectedFrameCrc32();
            System.out.println("status=" + status(
                    expectationMet,
                    stopPc,
                    config.expectedFrameCrc32(),
                    screenExpectationFailed,
                    frameExpectationFailed
            ));
            printProbeReport(
                    machine,
                    config,
                    roms,
                    steps,
                    keyScript,
                    keysTyped,
                    typeAfterScreen,
                    typeAfterScreenFound,
                    expectedScreen,
                    lastExecutedPc,
                    lastExecutedOpcode,
                    stopPc,
                    pcHits,
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
                        steps,
                        keyScript,
                        keysTyped,
                        typeAfterScreen,
                        typeAfterScreenFound,
                        expectedScreen,
                        lastExecutedPc,
                        lastExecutedOpcode,
                        stopPc,
                        pcHits,
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
        if (positional.isEmpty() || positional.size() > 2 || (typeAfterScreen != null && keyScript == null)) {
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
                expectedScreen,
                dumpFramePath,
                expectedFrameCrc32,
                stopPcs,
                watchAddrs,
                profilePcTop,
                new TraceOptions(traceIo, traceLimit, traceTail)
        );
    }

    private static int parseAddress(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        return Integer.parseInt(normalized, 16) & 0xFFFF;
    }

    private static int[] parseAddresses(String value) {
        String[] parts = value.split(",");
        int[] addresses = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            addresses[i] = parseAddress(parts[i]);
        }
        return addresses;
    }

    private static long parseCrc32(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        long parsed = Long.parseUnsignedLong(normalized, 16);
        if ((parsed & ~0xFFFF_FFFFL) != 0) {
            throw new IllegalArgumentException("CRC32 value out of range: " + value);
        }
        return parsed;
    }

    private static boolean isPcMatch(int pc, int[] pcs) {
        for (int expectedPc : pcs) {
            if (pc == expectedPc) {
                return true;
            }
        }
        return false;
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

    private static String decodeScript(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '<') {
                int end = value.indexOf('>', i + 1);
                if (end > i) {
                    String token = value.substring(i + 1, end).toUpperCase(Locale.ROOT);
                    Character tokenCharacter = decodeAngleToken(token);
                    if (tokenCharacter != null) {
                        decoded.append(tokenCharacter);
                        i = end;
                        continue;
                    }
                }
            }
            if (character == '\\' && i + 1 < value.length()) {
                char escaped = value.charAt(++i);
                switch (escaped) {
                    case 'r' -> decoded.append('\r');
                    case 'n' -> decoded.append('\n');
                    case 't' -> decoded.append('\t');
                    case '\\' -> decoded.append('\\');
                    case 'x' -> {
                        if (i + 2 >= value.length()) {
                            throw new IllegalArgumentException("Incomplete hex escape in key script");
                        }
                        String hex = value.substring(i + 1, i + 3);
                        decoded.append((char) Integer.parseInt(hex, 16));
                        i += 2;
                    }
                    default -> decoded.append(escaped);
                }
            } else {
                decoded.append(character);
            }
        }
        return decoded.toString();
    }

    private static Character decodeAngleToken(String token) {
        return switch (token) {
            case "CR", "ENTER", "RETURN" -> '\r';
            case "LF", "NL", "NEWLINE" -> '\n';
            case "SP", "SPACE" -> ' ';
            case "TAB" -> '\t';
            case "ESC", "ESCAPE" -> 0x1B;
            case "BS", "BACKSPACE", "LEFT" -> 0x08;
            case "RIGHT" -> 0x15;
            default -> null;
        };
    }

    private static boolean screenContains(C64Machine machine, String expectedScreen) {
        return String.join("\n", visibleLines(machine)).contains(expectedScreen);
    }

    private static String[] visibleLines(C64Machine machine) {
        String[] lines = new String[C64VideoDevice.TEXT_ROWS];
        int d018 = machine.board().video().readRegister(0x18);
        int bank = (~machine.board().cia2().readRegister(0x00)) & 0x03;
        int matrix = bank * 0x4000 + ((d018 >> 4) & 0x0F) * 0x400;
        for (int row = 0; row < lines.length; row++) {
            StringBuilder line = new StringBuilder(C64VideoDevice.TEXT_COLUMNS);
            for (int column = 0; column < C64VideoDevice.TEXT_COLUMNS; column++) {
                int screenCode = machine.board().memory().readRam(matrix + row * C64VideoDevice.TEXT_COLUMNS + column);
                line.append(renderCharacter(screenCode));
            }
            lines[row] = line.toString();
        }
        return lines;
    }

    private static char renderCharacter(int screenCode) {
        int code = screenCode & 0x7F;
        if (code == 0x00) {
            return '@';
        }
        if (code >= 0x01 && code <= 0x1A) {
            return (char) ('A' + code - 1);
        }
        if (code >= 0x20 && code <= 0x3F) {
            return (char) code;
        }
        return '.';
    }

    private static void printProbeReport(
            C64Machine machine,
            ProbeConfig config,
            C64RomImageLoader.C64RomSet roms,
            long steps,
            String keyScript,
            int keysTyped,
            String typeAfterScreen,
            boolean typeAfterScreenFound,
            String expectedScreen,
            int lastExecutedPc,
            int lastExecutedOpcode,
            int stopPc,
            long[] pcHits,
            TraceCollector traceCollector,
            FrameProbeResult frameResult
    ) {
        int pc = machine.cpu().registers().pc();
        int opcode = machine.board().cpuBus().readMemory(pc);
        String[] visibleLines = visibleLines(machine);
        String visibleText = String.join("\n", visibleLines);
        int d018 = machine.board().video().readRegister(0x18);
        int vicBank = (~machine.board().cia2().readRegister(0x00)) & 0x03;
        int matrix = vicBank * 0x4000 + ((d018 >> 4) & 0x0F) * 0x400;

        System.out.println("source=" + config.romPath());
        System.out.println("basicBytes=" + roms.basic().length);
        System.out.println("kernalBytes=" + roms.kernal().length);
        System.out.println("chargenBytes=" + roms.chargen().length);
        System.out.println("steps=" + steps);
        System.out.println("keysTyped=" + keysTyped + "/" + (keyScript == null ? 0 : keyScript.length()));
        if (typeAfterScreen != null) {
            System.out.println("typeAfterScreen=" + printable(typeAfterScreen));
        }
        System.out.println("typeAfterScreenFound=" + typeAfterScreenFound);
        System.out.println("pc=0x" + hex16(pc));
        System.out.println("opcode=0x" + hex8(opcode));
        if (lastExecutedPc >= 0) {
            System.out.println("lastPc=0x" + hex16(lastExecutedPc));
            System.out.println("lastOpcode=0x" + hex8(lastExecutedOpcode));
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
        if (stopPc >= 0) {
            System.out.println("stopPc=0x" + hex16(stopPc));
        }
        printPcProfile(pcHits, config.profilePcTop());
        traceCollector.print();
        printFrameResult(frameResult);
    }

    private static void printPcProfile(long[] pcHits, int topCount) {
        if (pcHits == null || topCount <= 0) {
            return;
        }
        int printed = 0;
        System.out.println("pcProfileTop:");
        while (printed < topCount) {
            int bestPc = -1;
            long bestHits = 0;
            for (int pc = 0; pc < pcHits.length; pc++) {
                long hits = pcHits[pc];
                if (hits > bestHits) {
                    bestHits = hits;
                    bestPc = pc;
                }
            }
            if (bestPc < 0) {
                break;
            }
            System.out.println("%02d|pc=0x%s hits=%d".formatted(printed + 1, hex16(bestPc), bestHits));
            pcHits[bestPc] = 0;
            printed++;
        }
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

    private static void printFrameResult(FrameProbeResult frameResult) {
        if (frameResult.dumpPath() != null) {
            System.out.println("frameDump=" + frameResult.dumpPath());
        }
        System.out.println("frameSize=" + frameResult.width() + "x" + frameResult.height());
        System.out.println("frameCrc32=0x" + crc32Hex(frameResult.crc32()));
        if (frameResult.expectedCrc32() != null) {
            System.out.println("expectFrameCrc32=0x" + crc32Hex(frameResult.expectedCrc32()));
            System.out.println("expectFrameCrc32Found=" + (frameResult.crc32() == frameResult.expectedCrc32()));
        }
    }

    private static String crc32Hex(long value) {
        return "%08X".formatted(value & 0xFFFF_FFFFL);
    }

    private static long frameCrc32(FrameBuffer frame) {
        CRC32 crc32 = new CRC32();
        for (int pixel : frame.pixels()) {
            crc32.update((pixel >>> 24) & 0xFF);
            crc32.update((pixel >>> 16) & 0xFF);
            crc32.update((pixel >>> 8) & 0xFF);
            crc32.update(pixel & 0xFF);
        }
        return crc32.getValue();
    }

    private static String printable(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
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

    private record FrameProbeResult(
            Path dumpPath,
            int width,
            int height,
            long crc32,
            Long expectedCrc32
    ) {
    }
}
