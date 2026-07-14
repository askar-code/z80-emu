package dev.z8emu.platform.video;

import java.util.Arrays;

public final class FrameBuffer {
    private final int width;
    private final int height;
    private final int[] pixels;

    public FrameBuffer(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }

        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int[] pixels() {
        return pixels;
    }

    public void clear(int argb) {
        Arrays.fill(pixels, argb);
    }

    public void setPixel(int x, int y, int argb) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }

        pixels[(y * width) + x] = argb;
    }
}
