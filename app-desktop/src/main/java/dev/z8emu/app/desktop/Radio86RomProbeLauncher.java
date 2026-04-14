package dev.z8emu.app.desktop;

import dev.z8emu.machine.radio86rk.Radio86Machine;
import dev.z8emu.machine.radio86rk.Radio86Bus;
import dev.z8emu.machine.radio86rk.device.Radio86KeyMap;
import dev.z8emu.machine.radio86rk.device.Radio86VideoDevice;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.machine.radio86rk.tape.Radio86TapeLoaders;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.CRC32;

public final class Radio86RomProbeLauncher {
    private static final long DEFAULT_MAX_INSTRUCTIONS = 2_000_000L;

    private Radio86RomProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args.length > 2) {
            System.err.println("Usage: Radio86RomProbeLauncher <rom-path> [max-instructions]");
            System.exit(2);
        }

        Path romPath = Path.of(args[0]).toAbsolutePath().normalize();
        byte[] romImage = Files.readAllBytes(romPath);
        if (romImage.length != Radio86Memory.ROM_SIZE_2K && romImage.length != Radio86Memory.ROM_SIZE_4K) {
            throw new IllegalArgumentException("Radio-86RK ROM must be exactly 2 KB or 4 KB: " + romPath);
        }

        long maxInstructions = args.length == 2 ? Long.parseLong(args[1]) : DEFAULT_MAX_INSTRUCTIONS;
        boolean traceBoot = Boolean.getBoolean("radio86.traceBoot");
        TraceCollector traceCollector = traceBoot ? new TraceCollector() : null;
        Radio86Machine machine = new Radio86Machine(romImage, traceCollector);
        String tapePath = System.getProperty("radio86.tape");
        String script = System.getProperty("radio86.type");
        String scriptSequence = System.getProperty("radio86.typeSequence");
        boolean typeAfterPrompt = Boolean.parseBoolean(System.getProperty("radio86.typeAfterPrompt", "true"));

        if (tapePath != null && !tapePath.isBlank()) {
            machine.board().tape().load(Radio86TapeLoaders.load(Path.of(tapePath).toAbsolutePath().normalize()));
            if (Boolean.parseBoolean(System.getProperty("radio86.tapeAutoPlay", "false"))) {
                machine.board().tape().play();
            }
        }

        long steps = 0;
        try {
            while (steps < maxInstructions && (!typeAfterPrompt || !monitorReady(machine))) {
                machine.runInstruction();
                steps++;
            }

            if (script != null && !script.isEmpty()) {
                steps = typeScript(machine, steps, script);
            }
            if (scriptSequence != null && !scriptSequence.isEmpty()) {
                String[] parts = scriptSequence.split("\\|", -1);
                for (String part : parts) {
                    while (steps < maxInstructions && !monitorReady(machine)) {
                        machine.runInstruction();
                        steps++;
                    }
                    if (steps >= maxInstructions) {
                        break;
                    }
                    if (!part.isEmpty()) {
                        steps = typeScript(machine, steps, part);
                    }
                }
            }

            while (steps < maxInstructions) {
                machine.runInstruction();
                steps++;
            }

            System.out.println("status=max-instructions-reached");
            printState(machine, steps);
            if (traceCollector != null) {
                traceCollector.printSummary();
            }
        } catch (Throwable failure) {
            System.out.println("status=failure");
            printState(machine, steps);
            if (traceCollector != null) {
                traceCollector.printSummary();
            }
            System.out.println("failure=" + failure.getClass().getName() + ": " + failure.getMessage());
            throw failure;
        }
    }

    private static void printState(Radio86Machine machine, long steps) {
        int pc = machine.cpu().registers().pc();
        int opcode = machine.board().cpuBus().readMemory(pc);

        String[] visibleLines = visibleLines(machine);
        String visibleText = String.join("\n", visibleLines);

        System.out.println("steps=" + steps);
        System.out.println("pc=0x" + hex16(pc));
        System.out.println("opcode=0x" + hex8(opcode));
        System.out.println("sp=0x" + hex16(machine.cpu().registers().sp()));
        System.out.println("af=0x" + hex16(machine.cpu().registers().af()));
        System.out.println("bc=0x" + hex16(machine.cpu().registers().bc()));
        System.out.println("de=0x" + hex16(machine.cpu().registers().de()));
        System.out.println("hl=0x" + hex16(machine.cpu().registers().hl()));
        System.out.println("inte=" + machine.board().cpuInteOutputHigh());
        System.out.println("shadow=" + machine.board().memory().bootShadowEnabled());
        System.out.println("tape=" + (machine.board().tape().isLoaded()
                ? (machine.board().tape().isPlaying() ? "play" : (machine.board().tape().isAtEnd() ? "eof" : "stop"))
                : "none"));
        System.out.println("t=" + machine.currentTState());
        System.out.println("visibleChars=" + countVisibleCharacters(visibleLines));
        System.out.println("visibleCrc32=0x" + crc32Hex(visibleText.getBytes(StandardCharsets.US_ASCII)));
        System.out.println("screen:");
        for (int row = 0; row < visibleLines.length; row++) {
            System.out.println("%02d|%s".formatted(row, visibleLines[row]));
        }

        System.out.println("bytesAroundPc:");
        for (int address = pc - 16; address <= pc + 16; address++) {
            int normalized = address & 0xFFFF;
            System.out.println(hex16(normalized) + ": " + hex8(machine.board().cpuBus().readMemory(normalized)));
        }
    }

    private static String[] visibleLines(Radio86Machine machine) {
        String[] lines = new String[Radio86VideoDevice.VISIBLE_ROWS];
        for (int row = 0; row < lines.length; row++) {
            StringBuilder line = new StringBuilder(Radio86VideoDevice.VISIBLE_COLUMNS);
            int offset = Radio86VideoDevice.VISIBLE_OFFSET + (row * Radio86VideoDevice.TOTAL_COLUMNS);
            for (int column = 0; column < Radio86VideoDevice.VISIBLE_COLUMNS; column++) {
                int code = machine.board().memory().readVideoByte(offset + column);
                line.append(renderCharacter(code));
            }
            lines[row] = line.toString();
        }
        return lines;
    }

    private static boolean monitorReady(Radio86Machine machine) {
        String[] screen = visibleLines(machine);
        if (!screen[0].contains("radio-86rk")) {
            return false;
        }
        for (String line : screen) {
            if (line.stripTrailing().endsWith("-->")) {
                return true;
            }
        }
        return false;
    }

    private static long typeScript(Radio86Machine machine, long steps, String script) {
        for (int i = 0; i < script.length(); i++) {
            char character = script.charAt(i);
            if (character == '\\' && i + 1 < script.length()) {
                char escaped = script.charAt(++i);
                character = switch (escaped) {
                    case 'r' -> '\r';
                    case 'n' -> '\n';
                    case 'b' -> '\b';
                    case 't' -> ' ';
                    default -> escaped;
                };
            }
            steps = typeCharacter(machine, steps, character);
        }
        return steps;
    }

    private static long typeCharacter(Radio86Machine machine, long steps, char character) {
        Radio86KeyMap.KeyChord chord = Radio86KeyMap.forCharacter(character);
        setChordState(machine, chord, true);
        steps = runFrames(machine, steps, 2);
        setChordState(machine, chord, false);
        return runFrames(machine, steps, 2);
    }

    private static void setChordState(Radio86Machine machine, Radio86KeyMap.KeyChord chord, boolean pressed) {
        for (Radio86KeyMap.MatrixKey key : chord.keys()) {
            machine.board().keyboard().setKeyPressed(key.row(), key.column(), pressed);
        }
    }

    private static long runFrames(Radio86Machine machine, long steps, int frames) {
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            long targetTState = machine.currentTState() + Radio86Machine.FRAME_T_STATES;
            while (machine.currentTState() < targetTState) {
                machine.runInstruction();
                steps++;
            }
        }
        return steps;
    }

    private static int countVisibleCharacters(String[] visibleLines) {
        int count = 0;
        for (String line : visibleLines) {
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) != ' ') {
                    count++;
                }
            }
        }
        return count;
    }

    private static char renderCharacter(int code) {
        int normalized = code & 0x7F;
        if (normalized == 0) {
            return ' ';
        }
        if (normalized >= 0x20 && normalized <= 0x7E) {
            return (char) normalized;
        }
        return '.';
    }

    private static String crc32Hex(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, data.length);
        return "%08X".formatted(crc32.getValue());
    }

    private static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }

    private static final class TraceCollector implements Radio86Bus.AccessTraceListener {
        private static final int MAX_EVENTS = Integer.getInteger("radio86.traceBoot.maxEvents", 256);

        private final Set<String> events = new LinkedHashSet<>();

        @Override
        public void onRead(int address, int value, long tState) {
            if (interesting(address)) {
                record("R", address, value, tState);
            }
        }

        @Override
        public void onWrite(int address, int value, long tState) {
            if (interesting(address)) {
                record("W", address, value, tState);
            }
        }

        private void record(String kind, int address, int value, long tState) {
            if (events.size() >= MAX_EVENTS) {
                return;
            }
            events.add("%s t=%d a=0x%04X v=0x%02X".formatted(kind, tState, address & 0xFFFF, value & 0xFF));
        }

        private boolean interesting(int address) {
            int normalized = address & 0xFFFF;
            return (normalized >= 0xC000 && normalized <= 0xDFFF) || normalized >= 0xE000;
        }

        private void printSummary() {
            System.out.println("trace:");
            if (events.isEmpty()) {
                System.out.println("(empty)");
                return;
            }
            for (String event : events) {
                System.out.println(event);
            }
        }
    }
}
