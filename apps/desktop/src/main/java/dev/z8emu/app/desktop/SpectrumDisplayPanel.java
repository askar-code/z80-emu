package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.platform.video.FrameBuffer;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.swing.JPanel;

final class SpectrumDisplayPanel extends JPanel {
    private static final int SCALE = 2;

    private final BufferedImage image = new BufferedImage(
            SpectrumUlaDevice.FRAME_WIDTH,
            SpectrumUlaDevice.FRAME_HEIGHT,
            BufferedImage.TYPE_INT_ARGB
    );

    SpectrumDisplayPanel() {
        setPreferredSize(new Dimension(
                SpectrumUlaDevice.FRAME_WIDTH * SCALE,
                SpectrumUlaDevice.FRAME_HEIGHT * SCALE
        ));
    }

    void present(FrameBuffer frameBuffer) {
        int[] target = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(frameBuffer.pixels(), 0, target, 0, target.length);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        } finally {
            g2.dispose();
        }
    }
}
