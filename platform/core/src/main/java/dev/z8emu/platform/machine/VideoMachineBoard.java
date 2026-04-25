package dev.z8emu.platform.machine;

import dev.z8emu.platform.video.FrameBuffer;

public interface VideoMachineBoard extends MachineBoard {
    FrameBuffer renderVideoFrame();
}
