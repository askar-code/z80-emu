package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.Apple2Machine;
import dev.z8emu.machine.apple2.Apple2Memory;
import dev.z8emu.machine.apple2.Apple2VideoDevice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

public final class Apple2RomProbeLauncher {
    private static final long DEFAULT_MAX_INSTRUCTIONS = 2_000_000L;
    private static final int DEFAULT_KEY_POLL_PC = 0xFD21;

    private Apple2RomProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        ProbeConfig config = parseArgs(args);
        if (config == null) {
            System.err.println("Usage: Apple2RomProbeLauncher <system-rom|memory-image> [max-instructions] [--keys=<script>] [--expect-screen=<text>] [--key-poll-pc=<hex>]");
            System.exit(2);
            return;
        }

        Path imagePath = config.imagePath();
        byte[] image = Files.readAllBytes(imagePath);
        if (!Apple2Memory.isSupportedLaunchImageSize(image.length)) {
            throw new IllegalArgumentException("Apple II image must be exactly 4 KB, 8 KB, 12 KB, or 64 KB: " + imagePath);
        }

        Apple2Machine machine = Apple2Machine.fromLaunchImage(image);
        String keyScript = decodeScript(config.keyScript());
        String expectedScreen = config.expectedScreen() == null ? null : decodeScript(config.expectedScreen());
        int injectedKeys = 0;
        boolean expectationMet = false;

        long steps = 0;
        try {
            while (steps < config.maxInstructions()) {
                if (shouldInjectKey(machine, keyScript, injectedKeys, config.keyPollPc())) {
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
                System.out.println("status=expectation-not-met");
                printState(machine, steps, imagePath, image.length, keyScript.length(), injectedKeys, expectedScreen);
                System.exit(1);
                return;
            }

            System.out.println("status=" + (expectationMet ? "expectation-met" : "max-instructions-reached"));
            printState(machine, steps, imagePath, image.length, keyScript.length(), injectedKeys, expectedScreen);
        } catch (Throwable failure) {
            System.out.println("status=failure");
            printState(machine, steps, imagePath, image.length, keyScript.length(), injectedKeys, expectedScreen);
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
        int keyPollPc = DEFAULT_KEY_POLL_PC;
        for (String arg : args) {
            if (arg.startsWith("--keys=")) {
                keyScript = arg.substring("--keys=".length());
            } else if (arg.startsWith("--expect-screen=")) {
                expectedScreen = arg.substring("--expect-screen=".length());
            } else if (arg.startsWith("--key-poll-pc=")) {
                keyPollPc = parseAddress(arg.substring("--key-poll-pc=".length()));
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
                keyPollPc
        );
    }

    private static int parseAddress(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        return Integer.parseInt(normalized, 16) & 0xFFFF;
    }

    private static boolean shouldInjectKey(Apple2Machine machine, String keyScript, int injectedKeys, int keyPollPc) {
        return injectedKeys < keyScript.length()
                && !machine.board().keyboard().strobe()
                && machine.cpu().registers().pc() == keyPollPc;
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
            String expectedScreen
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
            int keyPollPc
    ) {
    }
}
