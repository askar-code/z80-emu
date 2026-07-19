package dev.z8emu.machine.cpc;

import dev.z8emu.machine.cpc.disk.CpcDskLoader;
import dev.z8emu.platform.video.FrameBuffer;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpcPrinceTransitionTest {
    private static final long LOCKED_TRANSITION_CRC = 0x21A46C76L;
    private static final long LOCKED_RED_FLASH_CRC = 0x9CCD7C0FL;

    @Test
    void roomTransitionKeepsDisplayPhaseStableWhilePreservingRedFlash() throws Exception {
        Path romPath = CpcRomLocator.locate(
                CpcRomLocator.CPC6128_ROM_PROPERTY,
                CpcRomLocator.CPC6128_ROM
        );
        Path diskPath = CpcRomLocator.locate("cpc.prince", "prinpere.dsk");
        Assumptions.assumeTrue(romPath != null, "CPC 6128 ROM is unavailable");
        Assumptions.assumeTrue(diskPath != null, "Prince of Persia DSK is unavailable");

        CpcMachine machine = new CpcMachine(
                Files.readAllBytes(romPath),
                CpcDskLoader.load(diskPath)
        );
        CpcKeyboardTyper.runFrames(machine, 300);
        String command = "RUN\"PRINCE\r";
        for (int index = 0; index < command.length(); index++) {
            CpcKeyboardTyper.typeCharacter(machine, command.charAt(index));
        }
        CpcKeyboardTyper.runFrames(machine, 856);

        CpcKeyboardTyper.typeCharacter(machine, ' ');
        CpcKeyboardTyper.runFrames(machine, 1_700);
        holdKey(machine, 0, 1, 500);
        CpcKeyboardTyper.runFrames(machine, 10);
        holdKey(machine, 0, 2, 120);
        CpcKeyboardTyper.runFrames(machine, 10);

        machine.board().keyboard().setKeyPressed(1, 0, true);
        int transitionRedSamples = -1;
        int redFlashSamples = -1;
        int settledRedSamples = -1;
        int settledRowRedSamples = -1;
        long transitionCrc = -1;
        long redFlashCrc = -1;
        for (int frameIndex = 0; frameIndex <= 160; frameIndex++) {
            CpcKeyboardTyper.runFrames(machine, 1);
            FrameBuffer frame = machine.board().renderVideoFrame();
            if (frameIndex == 125 || frameIndex == 148 || frameIndex == 160) {
                int samples = redSamples(frame);
                if (frameIndex == 125) {
                    transitionRedSamples = samples;
                    transitionCrc = frameCrc32(frame);
                } else if (frameIndex == 148) {
                    redFlashSamples = samples;
                    redFlashCrc = frameCrc32(frame);
                } else {
                    settledRedSamples = samples;
                    settledRowRedSamples = rowRedSamples(frame, 227);
                    dumpTransitionPng(frame);
                }
            }
        }

        String metrics = "f125 redSamples=" + transitionRedSamples
                + " crc=0x" + Long.toHexString(transitionCrc).toUpperCase()
                + ", f148 redSamples=" + redFlashSamples
                + " crc=0x" + Long.toHexString(redFlashCrc).toUpperCase()
                + ", f160 redSamples=" + settledRedSamples
                + " row227RedSamples=" + settledRowRedSamples;
        assertTrue(transitionRedSamples < 300, metrics);
        assertEquals(LOCKED_TRANSITION_CRC, transitionCrc, metrics);
        assertEquals(LOCKED_RED_FLASH_CRC, redFlashCrc, metrics);
        assertTrue(settledRedSamples < 300, metrics);
        assertTrue(settledRowRedSamples < 50, metrics);
    }

    private static void holdKey(CpcMachine machine, int line, int bit, int frames) {
        machine.board().keyboard().setKeyPressed(line, bit, true);
        try {
            CpcKeyboardTyper.runFrames(machine, frames);
        } finally {
            machine.board().keyboard().setKeyPressed(line, bit, false);
        }
    }

    private static int redSamples(FrameBuffer frame) {
        int count = 0;
        int[] pixels = frame.pixels();
        for (int y = 0; y < frame.height(); y += 2) {
            int rowOffset = y * frame.width();
            for (int x = 0; x < frame.width(); x += 2) {
                int pixel = pixels[rowOffset + x];
                int red = (pixel >>> 16) & 0xFF;
                int green = (pixel >>> 8) & 0xFF;
                int blue = pixel & 0xFF;
                if (red > 180 && green < 80 && blue < 80) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int rowRedSamples(FrameBuffer frame, int row) {
        int count = 0;
        int rowOffset = row * frame.width();
        for (int x = 0; x < frame.width(); x++) {
            int pixel = frame.pixels()[rowOffset + x];
            int red = (pixel >>> 16) & 0xFF;
            int green = (pixel >>> 8) & 0xFF;
            int blue = pixel & 0xFF;
            if (red > 180 && green < 80 && blue < 80) {
                count++;
            }
        }
        return count;
    }

    private static void dumpTransitionPng(FrameBuffer frame) throws Exception {
        if (!Boolean.getBoolean("cpc.dumpTransitionPng")) {
            return;
        }

        Path output = Path.of("..", "..", "build", "cpc-baseline-10e-a", "prince-transition-f160.png")
                .normalize();
        Files.createDirectories(output.getParent());
        BufferedImage image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, frame.width(), frame.height(), frame.pixels(), 0, frame.width());
        ImageIO.write(image, "png", output.toFile());
    }

    private static long frameCrc32(FrameBuffer frame) {
        CRC32 crc = new CRC32();
        for (int pixel : frame.pixels()) {
            crc.update(pixel >>> 24);
            crc.update(pixel >>> 16);
            crc.update(pixel >>> 8);
            crc.update(pixel);
        }
        return crc.getValue();
    }
}
