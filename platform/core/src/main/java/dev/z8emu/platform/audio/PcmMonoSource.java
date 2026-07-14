package dev.z8emu.platform.audio;

public interface PcmMonoSource {
    int DEFAULT_SAMPLE_RATE = 44_100;

    int sampleRate();

    int drainAudio(byte[] target, int offset, int length);
}
