package dev.z8emu.app.desktop;

import java.util.ArrayList;
import java.util.List;

final class Apple2BinaryScanner {
    private Apple2BinaryScanner() {
    }

    static List<AsciiString> printableStrings(byte[] data, int minLength) {
        if (minLength < 1) {
            throw new IllegalArgumentException("minLength must be positive");
        }
        List<AsciiString> strings = new ArrayList<>();
        int start = -1;
        StringBuilder value = new StringBuilder();
        boolean hasNonSpace = false;
        for (int offset = 0; offset <= data.length; offset++) {
            int ch = offset < data.length ? Byte.toUnsignedInt(data[offset]) & 0x7F : -1;
            boolean printable = ch >= 0x20 && ch <= 0x7E;
            if (printable) {
                if (start < 0) {
                    start = offset;
                }
                value.append((char) ch);
                if (ch != 0x20) {
                    hasNonSpace = true;
                }
            } else if (start >= 0) {
                if (value.length() >= minLength && hasNonSpace) {
                    strings.add(new AsciiString(start, value.toString()));
                }
                start = -1;
                value.setLength(0);
                hasNonSpace = false;
            }
        }
        return strings;
    }

    static List<AbsoluteReference> absoluteReferences(byte[] data, int loadAddress) {
        List<AbsoluteReference> references = new ArrayList<>();
        for (int offset = 0; offset < data.length; ) {
            AbsoluteOpcode opcode = absoluteOpcode(Byte.toUnsignedInt(data[offset]));
            if (opcode != null && offset <= data.length - 3) {
                int target = Byte.toUnsignedInt(data[offset + 1])
                        | (Byte.toUnsignedInt(data[offset + 2]) << 8);
                references.add(new AbsoluteReference(
                        offset,
                        (loadAddress + offset) & 0xFFFF,
                        Byte.toUnsignedInt(data[offset]),
                        opcode.mnemonic(),
                        opcode.addressingMode(),
                        target
                ));
            }
            offset += instructionLength(Byte.toUnsignedInt(data[offset]));
        }
        return references;
    }

    static List<AbsoluteReference> ioReferences(byte[] data, int loadAddress) {
        return absoluteReferences(data, loadAddress).stream()
                .filter(ref -> ref.target() >= 0xC000 && ref.target() <= 0xCFFF)
                .toList();
    }

    static String apple2IoArea(int address) {
        int normalized = address & 0xFFFF;
        if (normalized >= 0xC000 && normalized <= 0xC00F) {
            return "system-switches";
        }
        if (normalized >= 0xC010 && normalized <= 0xC07F) {
            return "system-io";
        }
        if (normalized >= 0xC080 && normalized <= 0xC08F) {
            return "language-card";
        }
        if (normalized >= 0xC090 && normalized <= 0xC0FF) {
            int slot = (((normalized & 0xFF) >>> 4) - 8);
            return "slot%d-io".formatted(slot);
        }
        if (normalized >= 0xC100 && normalized <= 0xC7FF) {
            return "slot%d-rom".formatted((normalized >>> 8) & 0x07);
        }
        if (normalized >= 0xC800 && normalized <= 0xCFFF) {
            return "slot-expansion-rom";
        }
        return "other";
    }

    private static AbsoluteOpcode absoluteOpcode(int opcode) {
        return switch (opcode & 0xFF) {
            case 0x0D -> new AbsoluteOpcode("ORA", "abs");
            case 0x0E -> new AbsoluteOpcode("ASL", "abs");
            case 0x19 -> new AbsoluteOpcode("ORA", "abs,Y");
            case 0x1D -> new AbsoluteOpcode("ORA", "abs,X");
            case 0x1E -> new AbsoluteOpcode("ASL", "abs,X");
            case 0x20 -> new AbsoluteOpcode("JSR", "abs");
            case 0x2C -> new AbsoluteOpcode("BIT", "abs");
            case 0x2D -> new AbsoluteOpcode("AND", "abs");
            case 0x2E -> new AbsoluteOpcode("ROL", "abs");
            case 0x39 -> new AbsoluteOpcode("AND", "abs,Y");
            case 0x3D -> new AbsoluteOpcode("AND", "abs,X");
            case 0x3E -> new AbsoluteOpcode("ROL", "abs,X");
            case 0x4C -> new AbsoluteOpcode("JMP", "abs");
            case 0x4D -> new AbsoluteOpcode("EOR", "abs");
            case 0x4E -> new AbsoluteOpcode("LSR", "abs");
            case 0x59 -> new AbsoluteOpcode("EOR", "abs,Y");
            case 0x5D -> new AbsoluteOpcode("EOR", "abs,X");
            case 0x5E -> new AbsoluteOpcode("LSR", "abs,X");
            case 0x6C -> new AbsoluteOpcode("JMP", "(abs)");
            case 0x6D -> new AbsoluteOpcode("ADC", "abs");
            case 0x6E -> new AbsoluteOpcode("ROR", "abs");
            case 0x79 -> new AbsoluteOpcode("ADC", "abs,Y");
            case 0x7D -> new AbsoluteOpcode("ADC", "abs,X");
            case 0x7E -> new AbsoluteOpcode("ROR", "abs,X");
            case 0x8C -> new AbsoluteOpcode("STY", "abs");
            case 0x8D -> new AbsoluteOpcode("STA", "abs");
            case 0x8E -> new AbsoluteOpcode("STX", "abs");
            case 0x99 -> new AbsoluteOpcode("STA", "abs,Y");
            case 0x9D -> new AbsoluteOpcode("STA", "abs,X");
            case 0xAC -> new AbsoluteOpcode("LDY", "abs");
            case 0xAD -> new AbsoluteOpcode("LDA", "abs");
            case 0xAE -> new AbsoluteOpcode("LDX", "abs");
            case 0xB9 -> new AbsoluteOpcode("LDA", "abs,Y");
            case 0xBC -> new AbsoluteOpcode("LDY", "abs,X");
            case 0xBD -> new AbsoluteOpcode("LDA", "abs,X");
            case 0xBE -> new AbsoluteOpcode("LDX", "abs,Y");
            case 0xCC -> new AbsoluteOpcode("CPY", "abs");
            case 0xCD -> new AbsoluteOpcode("CMP", "abs");
            case 0xCE -> new AbsoluteOpcode("DEC", "abs");
            case 0xD9 -> new AbsoluteOpcode("CMP", "abs,Y");
            case 0xDD -> new AbsoluteOpcode("CMP", "abs,X");
            case 0xDE -> new AbsoluteOpcode("DEC", "abs,X");
            case 0xEC -> new AbsoluteOpcode("CPX", "abs");
            case 0xED -> new AbsoluteOpcode("SBC", "abs");
            case 0xEE -> new AbsoluteOpcode("INC", "abs");
            case 0xF9 -> new AbsoluteOpcode("SBC", "abs,Y");
            case 0xFD -> new AbsoluteOpcode("SBC", "abs,X");
            case 0xFE -> new AbsoluteOpcode("INC", "abs,X");
            default -> null;
        };
    }

    private static int instructionLength(int opcode) {
        if (absoluteOpcode(opcode) != null) {
            return 3;
        }
        return switch (opcode & 0xFF) {
            case 0x00, 0x08, 0x0A, 0x18, 0x28, 0x2A, 0x38, 0x40,
                    0x48, 0x4A, 0x58, 0x60, 0x68, 0x6A, 0x78,
                    0x88, 0x8A, 0x98, 0x9A, 0xA8, 0xAA, 0xB8,
                    0xBA, 0xC8, 0xCA, 0xD8, 0xE8, 0xEA, 0xF8 -> 1;
            case 0x01, 0x05, 0x06, 0x09, 0x10, 0x11, 0x15, 0x16,
                    0x21, 0x24, 0x25, 0x26, 0x29, 0x30, 0x31,
                    0x35, 0x36, 0x41, 0x45, 0x46, 0x49, 0x50,
                    0x51, 0x55, 0x56, 0x61, 0x65, 0x66, 0x69,
                    0x70, 0x71, 0x75, 0x76, 0x81, 0x84, 0x85,
                    0x86, 0x90, 0x91, 0x94, 0x95, 0x96, 0xA0,
                    0xA1, 0xA2, 0xA4, 0xA5, 0xA6, 0xA9, 0xB0,
                    0xB1, 0xB4, 0xB5, 0xB6, 0xC0, 0xC1, 0xC4,
                    0xC5, 0xC6, 0xC9, 0xD0, 0xD1, 0xD5, 0xD6,
                    0xE0, 0xE1, 0xE4, 0xE5, 0xE6, 0xE9, 0xF0,
                    0xF1, 0xF5, 0xF6 -> 2;
            default -> 1;
        };
    }

    record AsciiString(int offset, String value) {
    }

    record AbsoluteReference(
            int offset,
            int pc,
            int opcode,
            String mnemonic,
            String addressingMode,
            int target
    ) {
    }

    private record AbsoluteOpcode(String mnemonic, String addressingMode) {
    }
}
