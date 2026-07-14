package dev.z8emu.machine.apple2.disk;

public interface Apple2BlockDevice {
    int blockSize();

    int blockCount();

    byte[] readBlock(int block);

    default byte[] readBootProgram() {
        byte[] block0 = readBlock(0);
        byte[] block1 = readBlock(1);
        byte[] program = new byte[block0.length + block1.length];
        System.arraycopy(block0, 0, program, 0, block0.length);
        System.arraycopy(block1, 0, program, block0.length, block1.length);
        return program;
    }
}
