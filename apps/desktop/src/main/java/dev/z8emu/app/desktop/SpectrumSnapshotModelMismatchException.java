package dev.z8emu.app.desktop;

import java.nio.file.Path;
import java.util.Objects;

/** A decoded snapshot is valid, but targets a different Spectrum model. */
final class SpectrumSnapshotModelMismatchException extends IllegalArgumentException {
    private final SpectrumSnapshotFiles.Model expectedModel;
    private final SpectrumSnapshotFiles.Model actualModel;
    private final Path source;

    SpectrumSnapshotModelMismatchException(
            SpectrumSnapshotFiles.Model expectedModel,
            SpectrumSnapshotFiles.Model actualModel,
            Path source
    ) {
        super("Snapshot model mismatch: active " + expectedModel.displayName()
                + " cannot load " + actualModel.displayName() + " snapshot"
                + (source == null ? "" : ": " + source));
        this.expectedModel = Objects.requireNonNull(expectedModel, "expectedModel");
        this.actualModel = Objects.requireNonNull(actualModel, "actualModel");
        this.source = source;
    }

    SpectrumSnapshotFiles.Model expectedModel() {
        return expectedModel;
    }

    SpectrumSnapshotFiles.Model actualModel() {
        return actualModel;
    }

    Path source() {
        return source;
    }
}
