package dev.z8emu.machine.radio86rk.device;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

final class Radio86CharacterGenerator {
    private static final int CHAR_COUNT = 128;
    private static final int ROWS_PER_CHAR = 8;
    private static final int FONT_IMAGE_SIZE = CHAR_COUNT * ROWS_PER_CHAR;
    private static final String FONT_OVERRIDE_PROPERTY = "radio86.fontRom";
    private static final String DEFAULT_FONT_FILE = "radio-86rk-font.bin";

    private static final FontData FONT_DATA = loadFontData();

    private Radio86CharacterGenerator() {
    }

    static int row(int charCode, int rowIndex) {
        int normalizedChar = charCode & 0x7F;
        int normalizedRow = rowIndex & 0x07;
        return Byte.toUnsignedInt(FONT_DATA.glyphs()[(normalizedChar * ROWS_PER_CHAR) + normalizedRow]);
    }

    static boolean activeLowEncoding() {
        return FONT_DATA.activeLowEncoding();
    }

    private static FontData loadFontData() {
        Path fontPath = locateFontRom();
        if (fontPath != null) {
            try {
                byte[] image = Files.readAllBytes(fontPath);
                if (image.length == FONT_IMAGE_SIZE) {
                    return new FontData(image, true);
                }
                if (image.length == FONT_IMAGE_SIZE * 2) {
                    return new FontData(Arrays.copyOf(image, FONT_IMAGE_SIZE), true);
                }
                System.err.println("Ignoring Radio-86RK font ROM with unexpected size " + image.length + ": " + fontPath);
            } catch (IOException io) {
                System.err.println("Failed to read Radio-86RK font ROM " + fontPath + ": " + io.getMessage());
            }
        }

        return createFallbackFont();
    }

    private static FontData createFallbackFont() {
        byte[] glyphs = new byte[FONT_IMAGE_SIZE];
        for (int charCode = 0x20; charCode <= 0x7E; charCode++) {
            int offset = charCode * ROWS_PER_CHAR;
            glyphs[offset + 1] = 0x7E;
            glyphs[offset + 2] = 0x42;
            glyphs[offset + 3] = 0x42;
            glyphs[offset + 4] = 0x42;
            glyphs[offset + 5] = 0x42;
            glyphs[offset + 6] = 0x7E;
        }
        return new FontData(glyphs, false);
    }

    private static Path locateFontRom() {
        String explicitPath = System.getProperty(FONT_OVERRIDE_PROPERTY);
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path path = Path.of(explicitPath).toAbsolutePath().normalize();
            return Files.exists(path) ? path : null;
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(DEFAULT_FONT_FILE);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private record FontData(byte[] glyphs, boolean activeLowEncoding) {
    }
}
