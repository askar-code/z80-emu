package dev.z8emu.platform.audio;

public interface PcmMonoSource {
    int sampleRate();

    int drainAudio(byte[] target, int offset, int length);
}
