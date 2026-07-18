package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.platform.video.FrameBuffer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic execution of Ivan Kosarev's MIT-licensed ZX screen-timing test.
 *
 * <p>The unmodified TAP and published expected PNG come from kosarev/zx commit
 * 0074f3ded032fd869baaa9b973d895273cca6abd. Copyright (c) 2017-2026 Ivan
 * Kosarev; see the fixture README and complete MIT license under
 * {@code src/test/resources/kosarev}.</p>
 */
class KosarevScreenTimingRegressionTest {
    private static final String TAP_SHA256 =
            "990d16fda1616238319e8db7044db2a55496bfb5d329764fe17882203d26b60c";
    private static final String EXPECTED_PNG_SHA256 =
            "d2b67734142859b92247cade41991ed6466af0780aafc4e0cf3ef3c3e37c8c51";
    private static final int PROGRAM_ADDRESS = 0x8000;
    private static final int SETTLE_FRAMES = 120;
    private static final int CROP_X = 18;
    private static final int CROP_Y = 24;
    private static final int CROP_SIZE = 63;
    private static final int SCALE = 6;
    private static final int PUBLISHED_VERTICAL_OFFSET = 4;

    @Test
    void earlyTimingProgramMatchesPublishedVisualOracleWithoutAProprietaryRom() throws Exception {
        byte[] tap = fixture("screen_timing_early.tap.b64");
        byte[] expectedPng = fixture("expected_output.png.b64");
        assertEquals(TAP_SHA256, sha256(tap));
        assertEquals(EXPECTED_PNG_SHA256, sha256(expectedPng));

        byte[] program = extractCodeProgram(tap);
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        // The upstream BASIC loader executes CLS immediately before USR 32768.
        // Reproduce that public RAM state directly, without embedding a ROM.
        for (int address = 0x5800; address < 0x5B00; address++) {
            machine.board().memory().write(address, 0x38);
        }
        for (int offset = 0; offset < program.length; offset++) {
            machine.board().memory().write(PROGRAM_ADDRESS + offset, program[offset] & 0xFF);
        }
        machine.cpu().registers().setPc(PROGRAM_ADDRESS);

        long targetTState = (long) SETTLE_FRAMES * SpectrumUlaDevice.T_STATES_PER_FRAME;
        while (machine.currentTState() < targetTState) {
            machine.runInstruction();
        }

        assertEquals(0x80, machine.cpu().registers().i(), "program must install its IM2 table");
        assertEquals(2, machine.cpu().registers().interruptMode(), "program must reach IM2 calibration");
        assertEquals(0x833F, machine.cpu().registers().pc(), "program must settle in its draw-loop HALT");

        BufferedImage expected = ImageIO.read(new ByteArrayInputStream(expectedPng));
        assertNotNull(expected);
        assertEquals(CROP_SIZE * SCALE, expected.getWidth());
        assertEquals(376, expected.getHeight());
        assertPublishedCrop(expected, machine.board().renderVideoFrame());
    }

    private static void assertPublishedCrop(BufferedImage expected, FrameBuffer actual) {
        int differences = 0;
        String firstDifference = null;
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int sourceX = CROP_X + (x / SCALE);
                // The published 2019 PNG uses a 376-row historical window: its
                // nearest-neighbour samples are offset by four magnified rows
                // from the y=24 origin documented by the 2026 upstream helper.
                // Keep that exact window, including its final partial row,
                // instead of rescaling the visual oracle.
                int sourceY = CROP_Y + ((y + PUBLISHED_VERTICAL_OFFSET) / SCALE);
                int actualArgb = normalizePalette(actual.pixels()[(sourceY * actual.width()) + sourceX]);
                int expectedArgb = expected.getRGB(x, y);
                if (actualArgb != expectedArgb) {
                    differences++;
                    if (firstDifference == null) {
                        firstDifference = "first mismatch at published pixel (%d,%d): expected 0x%08X, actual 0x%08X"
                                .formatted(x, y, expectedArgb, actualArgb);
                    }
                }
            }
        }

        assertEquals(0, differences, firstDifference);
    }

    private static int normalizePalette(int argb) {
        int red = normalizeNormalIntensity((argb >>> 16) & 0xFF);
        int green = normalizeNormalIntensity((argb >>> 8) & 0xFF);
        int blue = normalizeNormalIntensity(argb & 0xFF);
        return (argb & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static int normalizeNormalIntensity(int component) {
        return component == 0xCD ? 0xC0 : component;
    }

    private static byte[] extractCodeProgram(byte[] tap) {
        List<byte[]> blocks = new ArrayList<>();
        int cursor = 0;
        while (cursor < tap.length) {
            assertTrue(cursor + 2 <= tap.length, "truncated TAP block length");
            int length = (tap[cursor] & 0xFF) | ((tap[cursor + 1] & 0xFF) << 8);
            cursor += 2;
            assertTrue(cursor + length <= tap.length, "truncated TAP block data");
            blocks.add(Arrays.copyOfRange(tap, cursor, cursor + length));
            cursor += length;
        }

        assertEquals(4, blocks.size());
        byte[] header = blocks.get(2);
        byte[] data = blocks.get(3);
        assertEquals(19, header.length);
        assertEquals(0x00, header[0] & 0xFF);
        assertEquals(0x03, header[1] & 0xFF, "third block must be a CODE header");
        int codeLength = (header[12] & 0xFF) | ((header[13] & 0xFF) << 8);
        int origin = (header[14] & 0xFF) | ((header[15] & 0xFF) << 8);
        assertEquals(831, codeLength);
        assertEquals(PROGRAM_ADDRESS, origin);
        assertEquals(codeLength + 2, data.length);
        assertEquals(0xFF, data[0] & 0xFF);
        assertEquals(0, xor(data), "TAP data checksum");
        return Arrays.copyOfRange(data, 1, data.length - 1);
    }

    private static int xor(byte[] bytes) {
        int result = 0;
        for (byte value : bytes) {
            result ^= value & 0xFF;
        }
        return result;
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream input = KosarevScreenTimingRegressionTest.class.getResourceAsStream("/kosarev/" + name)) {
            assertNotNull(input, "missing fixture " + name);
            String encoded = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(encoded);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
