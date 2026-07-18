package dev.z8emu.machine.spectrum.snapshot;

import java.util.Optional;

/**
 * Describes a snapshot image which cannot be decoded or represented by the
 * selected snapshot variant.
 */
public final class SpectrumSnapshotException extends Exception {
    private final Reason reason;
    private final UnsupportedFeature unsupportedFeature;

    public SpectrumSnapshotException(Reason reason, String message) {
        this(reason, null, message);
    }

    public SpectrumSnapshotException(
            Reason reason,
            UnsupportedFeature unsupportedFeature,
            String message
    ) {
        super(message);
        this.reason = reason;
        this.unsupportedFeature = unsupportedFeature;
    }

    public Reason reason() {
        return reason;
    }

    public Optional<UnsupportedFeature> unsupportedFeature() {
        return Optional.ofNullable(unsupportedFeature);
    }

    public enum Reason {
        MALFORMED,
        UNSUPPORTED,
        UNREPRESENTABLE
    }

    public enum UnsupportedFeature {
        SPECTRUM_16K,
        SAM_RAM,
        SPECTRUM_PLUS_2,
        SPECTRUM_PLUS_2A,
        SPECTRUM_PLUS_3,
        INTERFACE_1,
        MGT,
        MULTIFACE,
        PENTAGON,
        SCORPION,
        TIMEX,
        TR_DOS,
        AY_EXTENSION,
        FULLER_AUDIO,
        MEMORY_PAGE,
        FORMAT_EXTENSION,
        UNKNOWN_HARDWARE
    }
}
