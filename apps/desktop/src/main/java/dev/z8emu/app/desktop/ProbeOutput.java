package dev.z8emu.app.desktop;

import dev.z8emu.platform.video.FrameBuffer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
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
}
