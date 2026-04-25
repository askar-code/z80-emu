package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.Apple2Machine;
import dev.z8emu.machine.apple2.Apple2Memory;
import dev.z8emu.machine.apple2.Apple2VideoDevice;
import dev.z8emu.machine.apple2.disk.Apple2Disk2Controller;
import dev.z8emu.machine.apple2.disk.Apple2DosDiskImageLoader;
import dev.z8emu.platform.video.FrameBuffer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

public final class Apple2RomProbeLauncher {
    private static final long DEFAULT_MAX_INSTRUCTIONS = 2_000_000L;
    private static final int DEFAULT_KEY_POLL_PC = 0xFD21;

    private Apple2RomProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        ProbeConfig config = parseArgs(args);
        if (config == null) {
            System.err.println("Usage: Apple2RomProbeLauncher <system-rom|memory-image> [max-instructions] [--disk=<disk.do|disk.dsk>] [--disk2-rom=<disk2.rom>] [--keys=<script>] [--expect-screen=<text>] [--key-poll-pc=<hex[,hex...]>] [--stop-pc=<hex[,hex...]>] [--watch-addr=<hex[,hex...]>] [--poke-on-pc=<pc>:<addr>=<value>[,<addr>=<value>][;...]] [--profile-pc-callers=<pc[,pc...]>] [--profile-pc-top=<count>] [--dump-frame=<png>]");
            System.exit(2);
            return;
        }

        Path imagePath = config.imagePath();
        byte[] image = Files.readAllBytes(imagePath);
        if (!Apple2Memory.isSupportedLaunchImageSize(image.length)) {
            throw new IllegalArgumentException("Apple II image must be exactly 4 KB, 8 KB, 12 KB, or 64 KB: " + imagePath);
        }

        Apple2Machine machine = Apple2Machine.fromLaunchImage(image);
        if (config.disk2RomPath() != null) {
            machine.loadDisk2SlotRom(readDisk2Rom(config.disk2RomPath()));
        }
        if (config.diskPath() != null) {
            machine.insertDisk(Apple2DosDiskImageLoader.load(config.diskPath()));
            if (config.disk2RomPath() != null) {
                machine.bootDiskFromSlot6();
            }
        }
        String keyScript = decodeScript(config.keyScript());
        String expectedScreen = config.expectedScreen() == null ? null : decodeScript(config.expectedScreen());
        int injectedKeys = 0;
        boolean expectationMet = false;
        int stopPc = -1;
        long pokesApplied = 0;
        long[] pcHits = config.profilePcTop() > 0 ? new long[0x10000] : null;
        long[][] pcCallerHits = config.profilePcCallers().length > 0
                ? new long[config.profilePcCallers().length][0x10000]
                : null;

        long steps = 0;
        try {
            while (steps < config.maxInstructions()) {
                int pc = machine.cpu().registers().pc();
                if (pcHits != null) {
                    pcHits[pc]++;
                }
                profilePcCaller(machine, pc, config.profilePcCallers(), pcCallerHits);
                pokesApplied += applyPcPokes(machine, pc, config.pcPokes());
                if (isPcMatch(pc, config.stopPcs())) {
                    stopPc = pc;
                    break;
                }
                if (shouldInjectKey(machine, keyScript, injectedKeys, config.keyPollPcs())) {
                    machine.board().keyboard().pressKey(normalizeKey(keyScript.charAt(injectedKeys)));
                    injectedKeys++;
                }

                machine.runInstruction();
                steps++;

                if (expectedScreen != null && (steps % 100) == 0 && screenContains(machine, expectedScreen)) {
                    expectationMet = true;
                    break;
                }
            }

            if (expectedScreen != null && !expectationMet) {
                System.out.println("status=" + (stopPc >= 0 ? "stop-pc-reached-expectation-not-met" : "expectation-not-met"));
                printState(machine, steps, imagePath, image.length, keyScript.length(), injectedKeys, expectedScreen, config.watchAddrs());
                printStopPc(stopPc);
                printPokesApplied(pokesApplied);
                printPcProfile(pcHits, config.profilePcTop());
                printPcCallerProfile(pcCallerHits, config.profilePcCallers(), config.profilePcTop());
                dumpFrameIfRequested(machine, config.dumpFramePath());
                System.exit(1);
                return;
            }

            System.out.println("status=" + status(expectationMet, stopPc));
            printState(machine, steps, imagePath, image.length, keyScript.length(), injectedKeys, expectedScreen, config.watchAddrs());
            printStopPc(stopPc);
            printPokesApplied(pokesApplied);
            printPcProfile(pcHits, config.profilePcTop());
            printPcCallerProfile(pcCallerHits, config.profilePcCallers(), config.profilePcTop());
            dumpFrameIfRequested(machine, config.dumpFramePath());
        } catch (Throwable failure) {
            System.out.println("status=failure");
            printState(machine, steps, imagePath, image.length, keyScript.length(), injectedKeys, expectedScreen, config.watchAddrs());
            printStopPc(stopPc);
            printPokesApplied(pokesApplied);
            printPcProfile(pcHits, config.profilePcTop());
            printPcCallerProfile(pcCallerHits, config.profilePcCallers(), config.profilePcTop());
            dumpFrameIfRequested(machine, config.dumpFramePath());
            System.out.println("failure=" + failure.getClass().getName() + ": " + failure.getMessage());
            throw failure;
        }
    }

    private static ProbeConfig parseArgs(String[] args) {
        if (args.length == 0) {
            return null;
        }

        List<String> positional = new ArrayList<>(2);
        String keyScript = "";
        String expectedScreen = null;
        int[] keyPollPcs = new int[]{DEFAULT_KEY_POLL_PC};
        int[] stopPcs = new int[0];
        int[] watchAddrs = new int[0];
        List<PcPoke> pcPokes = List.of();
        int[] profilePcCallers = new int[0];
        int profilePcTop = 0;
        Path dumpFramePath = null;
        Path diskPath = null;
        Path disk2RomPath = null;
        for (String arg : args) {
            if (arg.startsWith("--keys=")) {
                keyScript = arg.substring("--keys=".length());
            } else if (arg.startsWith("--expect-screen=")) {
                expectedScreen = arg.substring("--expect-screen=".length());
            } else if (arg.startsWith("--key-poll-pc=")) {
                keyPollPcs = parseAddresses(arg.substring("--key-poll-pc=".length()));
            } else if (arg.startsWith("--stop-pc=")) {
                stopPcs = parseAddresses(arg.substring("--stop-pc=".length()));
            } else if (arg.startsWith("--watch-addr=")) {
                watchAddrs = parseAddresses(arg.substring("--watch-addr=".length()));
            } else if (arg.startsWith("--poke-on-pc=")) {
                pcPokes = parsePcPokes(arg.substring("--poke-on-pc=".length()));
            } else if (arg.startsWith("--profile-pc-callers=")) {
                profilePcCallers = parseAddresses(arg.substring("--profile-pc-callers=".length()));
            } else if (arg.startsWith("--profile-pc-top=")) {
                profilePcTop = Integer.parseInt(arg.substring("--profile-pc-top=".length()));
                if (profilePcTop < 0) {
                    return null;
                }
            } else if (arg.startsWith("--dump-frame=")) {
                dumpFramePath = Path.of(arg.substring("--dump-frame=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--disk=")) {
                diskPath = Path.of(arg.substring("--disk=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--disk2-rom=")) {
                disk2RomPath = Path.of(arg.substring("--disk2-rom=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--")) {
                return null;
            } else {
                positional.add(arg);
            }
        }

        if (positional.isEmpty() || positional.size() > 2) {
            return null;
        }

        long maxInstructions = positional.size() == 2
                ? Long.parseLong(positional.get(1))
                : DEFAULT_MAX_INSTRUCTIONS;
        return new ProbeConfig(
                Path.of(positional.get(0)).toAbsolutePath().normalize(),
                maxInstructions,
                keyScript,
                expectedScreen,
                keyPollPcs,
                stopPcs,
                watchAddrs,
                pcPokes,
                profilePcCallers,
                profilePcTop,
                dumpFramePath,
                diskPath,
                disk2RomPath
        );
    }

    private static byte[] readDisk2Rom(Path romPath) throws IOException {
        byte[] rom = Files.readAllBytes(romPath);
        if (rom.length != Apple2Disk2Controller.SLOT_ROM_SIZE) {
            throw new IllegalArgumentException("Disk II slot ROM must be exactly 256 bytes: " + romPath);
        }
        return rom;
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

    private static List<PcPoke> parsePcPokes(String value) {
        List<PcPoke> pokes = new ArrayList<>();
        for (String entry : value.split(";")) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid --poke-on-pc entry: " + entry);
            }
            int pc = parseAddress(parts[0]);
            String[] assignments = parts[1].split(",");
            int[] addresses = new int[assignments.length];
            int[] values = new int[assignments.length];
            for (int i = 0; i < assignments.length; i++) {
                String[] assignment = assignments[i].split("=", 2);
                if (assignment.length != 2) {
                    throw new IllegalArgumentException("Invalid --poke-on-pc assignment: " + assignments[i]);
                }
                addresses[i] = parseAddress(assignment[0]);
                values[i] = parseAddress(assignment[1]) & 0xFF;
            }
            pokes.add(new PcPoke(pc, addresses, values));
        }
        return pokes;
    }

    private static long applyPcPokes(Apple2Machine machine, int pc, List<PcPoke> pcPokes) {
        long applied = 0;
        for (PcPoke pcPoke : pcPokes) {
            if (pcPoke.pc() == pc) {
                for (int i = 0; i < pcPoke.addresses().length; i++) {
                    machine.board().cpuBus().writeMemory(pcPoke.addresses()[i], pcPoke.values()[i]);
                    applied++;
                }
            }
        }
        return applied;
    }

    private static void profilePcCaller(Apple2Machine machine, int pc, int[] targetPcs, long[][] callerHits) {
        if (callerHits == null) {
            return;
        }
        for (int i = 0; i < targetPcs.length; i++) {
            if (pc == targetPcs[i]) {
                callerHits[i][stackReturnAddress(machine)]++;
            }
        }
    }

    private static int stackReturnAddress(Apple2Machine machine) {
        int sp = machine.cpu().registers().sp();
        int low = machine.board().cpuBus().readMemory(0x0100 | ((sp + 1) & 0xFF));
        int high = machine.board().cpuBus().readMemory(0x0100 | ((sp + 2) & 0xFF));
        return ((high << 8) | low) & 0xFFFF;
    }

    private static boolean shouldInjectKey(Apple2Machine machine, String keyScript, int injectedKeys, int[] keyPollPcs) {
        return injectedKeys < keyScript.length()
                && !machine.board().keyboard().strobe()
                && isPcMatch(machine.cpu().registers().pc(), keyPollPcs);
    }

    private static boolean isPcMatch(int pc, int[] pcs) {
        for (int expectedPc : pcs) {
            if (pc == expectedPc) {
                return true;
            }
        }
        return false;
    }

    private static String status(boolean expectationMet, int stopPc) {
        if (expectationMet) {
            return "expectation-met";
        }
        if (stopPc >= 0) {
            return "stop-pc-reached";
        }
        return "max-instructions-reached";
    }

    private static int normalizeKey(char key) {
        if (key >= 'a' && key <= 'z') {
            return Character.toUpperCase(key) & 0x7F;
        }
        return key & 0x7F;
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

    private static void printState(
            Apple2Machine machine,
            long steps,
            Path imagePath,
            int imageLength,
            int keyCount,
            int injectedKeys,
            String expectedScreen,
            int[] watchAddrs
    ) {
        int pc = machine.cpu().registers().pc();
        int opcode = machine.board().cpuBus().readMemory(pc);
        String[] visibleLines = visibleLines(machine);
        String visibleText = String.join("\n", visibleLines);

        System.out.println("source=" + imagePath);
        System.out.println("imageBytes=" + imageLength);
        System.out.println("steps=" + steps);
        System.out.println("pc=0x" + hex16(pc));
        System.out.println("opcode=0x" + hex8(opcode));
        System.out.println("a=0x" + hex8(machine.cpu().registers().a()));
        System.out.println("x=0x" + hex8(machine.cpu().registers().x()));
        System.out.println("y=0x" + hex8(machine.cpu().registers().y()));
        System.out.println("sp=0x" + hex8(machine.cpu().registers().sp()));
        System.out.println("p=0x" + hex8(machine.cpu().registers().p()));
        System.out.println("t=" + machine.currentTState());
        System.out.println("textPage=" + (machine.board().softSwitches().page2() ? 2 : 1));
        System.out.println("textMode=" + machine.board().softSwitches().textMode());
        System.out.println("key=0x" + hex8(machine.board().keyboard().readData()));
        System.out.println("keysInjected=" + injectedKeys + "/" + keyCount);
        if (expectedScreen != null) {
            System.out.println("expectScreen=" + printable(expectedScreen));
            System.out.println("expectScreenFound=" + visibleText.contains(expectedScreen));
        }
        System.out.println("speaker=" + (machine.board().speaker().high() ? 1 : 0));
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

        if (watchAddrs.length > 0) {
            System.out.println("watchedBytes:");
            for (int address : watchAddrs) {
                System.out.println(hex16(address) + ": " + hex8(machine.board().cpuBus().readMemory(address)));
            }
        }
    }

    private static void printStopPc(int stopPc) {
        if (stopPc >= 0) {
            System.out.println("stopPc=0x" + hex16(stopPc));
        }
    }

    private static void printPokesApplied(long pokesApplied) {
        if (pokesApplied > 0) {
            System.out.println("pokesApplied=" + pokesApplied);
        }
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

    private static void printPcCallerProfile(long[][] callerHits, int[] targetPcs, int topCount) {
        if (callerHits == null) {
            return;
        }
        int callersToPrint = topCount > 0 ? topCount : 12;
        for (int targetIndex = 0; targetIndex < targetPcs.length; targetIndex++) {
            System.out.println("pcCallerProfile target=0x" + hex16(targetPcs[targetIndex]) + ":");
            long[] hits = callerHits[targetIndex];
            for (int printed = 0; printed < callersToPrint; printed++) {
                int bestReturn = -1;
                long bestHits = 0;
                for (int returnAddress = 0; returnAddress < hits.length; returnAddress++) {
                    if (hits[returnAddress] > bestHits) {
                        bestHits = hits[returnAddress];
                        bestReturn = returnAddress;
                    }
                }
                if (bestReturn < 0) {
                    break;
                }
                int callerResume = (bestReturn + 1) & 0xFFFF;
                System.out.println("%02d|return=0x%s resume=0x%s hits=%d"
                        .formatted(printed + 1, hex16(bestReturn), hex16(callerResume), bestHits));
                hits[bestReturn] = 0;
            }
        }
    }

    private static void dumpFrameIfRequested(Apple2Machine machine, Path dumpFramePath) throws IOException {
        if (dumpFramePath == null) {
            return;
        }
        Path parent = dumpFramePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        FrameBuffer frame = machine.board().renderVideoFrame();
        writePng(frame, dumpFramePath);
        System.out.println("frameDump=" + dumpFramePath);
        System.out.println("frameSize=" + frame.width() + "x" + frame.height());
        System.out.println("frameCrc32=0x" + frameCrc32Hex(frame));
    }

    private static void writePng(FrameBuffer frame, Path target) throws IOException {
        BufferedImage image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, frame.width(), frame.height(), frame.pixels(), 0, frame.width());
        ImageIO.write(image, "png", target.toFile());
    }

    private static boolean screenContains(Apple2Machine machine, String expectedScreen) {
        return String.join("\n", visibleLines(machine)).contains(expectedScreen);
    }

    private static String[] visibleLines(Apple2Machine machine) {
        String[] lines = new String[Apple2VideoDevice.TEXT_ROWS];
        int textPageBase = machine.board().softSwitches().page2()
                ? Apple2Memory.TEXT_PAGE_2_START
                : Apple2Memory.TEXT_PAGE_1_START;
        for (int row = 0; row < lines.length; row++) {
            StringBuilder line = new StringBuilder(Apple2VideoDevice.TEXT_COLUMNS);
            for (int column = 0; column < Apple2VideoDevice.TEXT_COLUMNS; column++) {
                int address = Apple2Memory.textPageAddress(textPageBase, row, column);
                line.append(renderCharacter(machine.board().memory().read(address)));
            }
            lines[row] = line.toString();
        }
        return lines;
    }

    private static char renderCharacter(int screenCode) {
        int normalized = screenCode & 0x3F;
        if (normalized < 0x20) {
            normalized += 0x40;
        }
        if (normalized >= 0x20 && normalized <= 0x7E) {
            return (char) normalized;
        }
        return '.';
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

    private static String crc32Hex(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, data.length);
        return "%08X".formatted(crc32.getValue());
    }

    private static String frameCrc32Hex(FrameBuffer frame) {
        CRC32 crc32 = new CRC32();
        for (int pixel : frame.pixels()) {
            crc32.update((pixel >>> 24) & 0xFF);
            crc32.update((pixel >>> 16) & 0xFF);
            crc32.update((pixel >>> 8) & 0xFF);
            crc32.update(pixel & 0xFF);
        }
        return "%08X".formatted(crc32.getValue());
    }

    private static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }

    private static String printable(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private record ProbeConfig(
            Path imagePath,
            long maxInstructions,
            String keyScript,
            String expectedScreen,
            int[] keyPollPcs,
            int[] stopPcs,
            int[] watchAddrs,
            List<PcPoke> pcPokes,
            int[] profilePcCallers,
            int profilePcTop,
            Path dumpFramePath,
            Path diskPath,
            Path disk2RomPath
    ) {
    }

    private record PcPoke(
            int pc,
            int[] addresses,
            int[] values
    ) {
    }
}
