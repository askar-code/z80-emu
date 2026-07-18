package dev.z8emu.machine.spectrum.snapshot;

/** Common state carried by the supported Spectrum snapshot variants. */
public sealed interface SpectrumSnapshot permits Spectrum48Snapshot, Spectrum128Snapshot {
    Z80SnapshotState cpu();

    int borderColor();
}
