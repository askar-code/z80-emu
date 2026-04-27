package dev.z8emu.machine.apple2.disk;

public interface Apple2BlockDevice {
    int blockSize();

    int blockCount();

    byte[] readBlock(int block);
}
