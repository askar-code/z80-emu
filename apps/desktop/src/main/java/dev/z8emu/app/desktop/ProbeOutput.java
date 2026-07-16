package dev.z8emu.app.desktop;

import dev.z8emu.platform.video.FrameBuffer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

final class ProbeOutput {
    private ProbeOutput() {
    }

    static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
    }

    static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }

    static String crc32Hex(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, data.length);
        return "%08X".formatted(crc32.getValue());
    }

    static String crc32Hex(long value) {
        return "%08X".formatted(value & 0xFFFF_FFFFL);
    }

    static String decodeScript(String value) {
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

    static Character decodeAngleToken(String token) {
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

    static int parseAddress(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        return Integer.parseInt(normalized, 16) & 0xFFFF;
    }

    static int[] parseAddresses(String value) {
        String[] parts = value.split(",");
        int[] addresses = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            addresses[i] = parseAddress(parts[i]);
        }
        return addresses;
    }

    static long parseCrc32(String value) {
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

    static boolean isPcMatch(int pc, int[] pcs) {
        for (int expectedPc : pcs) {
            if (pc == expectedPc) {
                return true;
            }
        }
        return false;
    }

    static void printPcProfile(long[] pcHits, int topCount) {
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

    static long frameCrc32(FrameBuffer frame) {
        CRC32 crc32 = new CRC32();
        for (int pixel : frame.pixels()) {
            crc32.update((pixel >>> 24) & 0xFF);
            crc32.update((pixel >>> 16) & 0xFF);
            crc32.update((pixel >>> 8) & 0xFF);
            crc32.update(pixel & 0xFF);
        }
        return crc32.getValue();
    }

    static String printable(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    static void printFrameResult(FrameProbeResult frameResult) {
        if (frameResult == null) {
            return;
        }
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

    static void writePng(FrameBuffer frame, Path target) throws IOException {
        BufferedImage image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, frame.width(), frame.height(), frame.pixels(), 0, frame.width());
        ImageIO.write(image, "png", target.toFile());
    }

    static int countVisibleCharacters(String[] visibleLines) {
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

    record FrameProbeResult(
            Path dumpPath,
            int width,
            int height,
            long crc32,
            Long expectedCrc32
    ) {
    }
}
