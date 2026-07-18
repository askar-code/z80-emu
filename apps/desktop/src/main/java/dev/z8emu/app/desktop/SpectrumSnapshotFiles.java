package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum.snapshot.Sna128SnapshotCodec;
import dev.z8emu.machine.spectrum.snapshot.Sna48SnapshotCodec;
import dev.z8emu.machine.spectrum.snapshot.Spectrum128Snapshot;
import dev.z8emu.machine.spectrum.snapshot.Spectrum128Snapshots;
import dev.z8emu.machine.spectrum.snapshot.Spectrum48Snapshot;
import dev.z8emu.machine.spectrum.snapshot.Spectrum48Snapshots;
import dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshot;
import dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException;
import dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshots;
import dev.z8emu.machine.spectrum.snapshot.Z80V2V3SnapshotCodec;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;

/** File-system and model-safety boundary for desktop Spectrum snapshots. */
final class SpectrumSnapshotFiles {
    static final String SNA_128_AY_WARNING =
            "Spectrum 128K .sna does not store AY register state; use .z80 to preserve sound state.";

    private SpectrumSnapshotFiles() {
    }

    static boolean hasSnapshotExtension(Path path) {
        return format(path) != null;
    }

    static LoadedSnapshot load(Path source) throws IOException, SpectrumSnapshotException {
        Path normalized = normalize(source);
        Format format = requireFormat(normalized);
        byte[] image = Files.readAllBytes(normalized);
        SpectrumSnapshot snapshot = switch (format) {
            case SNA -> decodeSna(image);
            case Z80 -> SpectrumSnapshots.decodeZ80(image);
        };
        return new LoadedSnapshot(normalized, format, snapshot);
    }

    static LoadedSnapshot load(Path source, Model expectedModel)
            throws IOException, SpectrumSnapshotException {
        LoadedSnapshot loaded = load(source);
        requireCompatible(expectedModel, loaded.snapshot(), loaded.source());
        return loaded;
    }

    static SaveResult save(Path target, SpectrumSnapshot snapshot) throws IOException, SpectrumSnapshotException {
        Objects.requireNonNull(snapshot, "snapshot");
        Path normalized = normalize(target);
        Format format = requireFormat(normalized);
        byte[] image = switch (snapshot) {
            case Spectrum48Snapshot spectrum48 -> switch (format) {
                case SNA -> Sna48SnapshotCodec.encode(spectrum48);
                case Z80 -> Z80V2V3SnapshotCodec.encode(
                        spectrum48,
                        Z80V2V3SnapshotCodec.Compression.COMPRESSED
                );
            };
            case Spectrum128Snapshot spectrum128 -> switch (format) {
                case SNA -> Sna128SnapshotCodec.encode(spectrum128);
                case Z80 -> Z80V2V3SnapshotCodec.encode(
                        spectrum128,
                        Z80V2V3SnapshotCodec.Compression.COMPRESSED
                );
            };
        };
        writeAtomically(normalized, image);
        return new SaveResult(normalized, format, modelOf(snapshot), warning(format, snapshot));
    }

    static SpectrumSnapshot capture(SpectrumMachine machine) {
        Objects.requireNonNull(machine, "machine");
        if (machine instanceof Spectrum48kMachine spectrum48) {
            return Spectrum48Snapshots.capture(spectrum48);
        }
        if (machine instanceof Spectrum128Machine spectrum128) {
            return Spectrum128Snapshots.capture(spectrum128);
        }
        throw new IllegalArgumentException("Unsupported Spectrum machine implementation: " + machine.getClass());
    }

    static void restore(SpectrumMachine machine, SpectrumSnapshot snapshot) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(snapshot, "snapshot");
        Model expectedModel = modelOf(machine);
        requireCompatible(expectedModel, snapshot, null);
        if (machine instanceof Spectrum48kMachine spectrum48) {
            Spectrum48Snapshots.restore(spectrum48, (Spectrum48Snapshot) snapshot);
            return;
        }
        if (machine instanceof Spectrum128Machine spectrum128) {
            Spectrum128Snapshots.restore(spectrum128, (Spectrum128Snapshot) snapshot);
            return;
        }
        throw new IllegalArgumentException("Unsupported Spectrum machine implementation: " + machine.getClass());
    }

    static void requireCompatible(Model expectedModel, SpectrumSnapshot snapshot, Path source) {
        Objects.requireNonNull(expectedModel, "expectedModel");
        Objects.requireNonNull(snapshot, "snapshot");
        Model actualModel = modelOf(snapshot);
        if (expectedModel != actualModel) {
            throw new SpectrumSnapshotModelMismatchException(expectedModel, actualModel, source);
        }
    }

    static Model modelOf(SpectrumMachine machine) {
        if (machine instanceof Spectrum48kMachine) {
            return Model.SPECTRUM_48K;
        }
        if (machine instanceof Spectrum128Machine) {
            return Model.SPECTRUM_128K;
        }
        throw new IllegalArgumentException("Unsupported Spectrum machine implementation: " + machine.getClass());
    }

    static Model modelOf(DesktopMachineKind kind) {
        return switch (kind) {
            case SPECTRUM48 -> Model.SPECTRUM_48K;
            case SPECTRUM128 -> Model.SPECTRUM_128K;
            case RADIO86RK, CPC6128, C64, APPLE2, APPLE2E -> throw new IllegalArgumentException(
                    "Expected Spectrum machine kind: " + kind
            );
        };
    }

    static String warning(Format format, SpectrumSnapshot snapshot) {
        return format == Format.SNA && snapshot instanceof Spectrum128Snapshot
                ? SNA_128_AY_WARNING
                : null;
    }

    static String warning(Path target, Model model) {
        return requireFormat(normalize(target)) == Format.SNA && model == Model.SPECTRUM_128K
                ? SNA_128_AY_WARNING
                : null;
    }

    private static SpectrumSnapshot decodeSna(byte[] image) throws SpectrumSnapshotException {
        if (image.length != Sna48SnapshotCodec.IMAGE_SIZE
                && image.length != Sna128SnapshotCodec.IMAGE_SIZE
                && image.length != Sna128SnapshotCodec.DUPLICATE_FIXED_BANK_IMAGE_SIZE) {
            throw new SpectrumSnapshotException(
                    SpectrumSnapshotException.Reason.MALFORMED,
                    "Spectrum SNA image must be exactly 49179, 131103 or 147487 bytes, got " + image.length
            );
        }
        return SpectrumSnapshots.decodeSna(image);
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static Format requireFormat(Path path) {
        Format format = format(path);
        if (format == null) {
            throw new IllegalArgumentException("Spectrum snapshot filename must end in .sna or .z80: " + path);
        }
        return format;
    }

    private static Format format(Path path) {
        String filename = path.getFileName() == null
                ? ""
                : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".sna")) {
            return Format.SNA;
        }
        if (filename.endsWith(".z80")) {
            return Format.Z80;
        }
        return null;
    }

    private static void writeAtomically(Path target, byte[] image) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Snapshot target has no parent directory: " + target);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".z8emu-snapshot-", ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, image);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    enum Format {
        SNA,
        Z80
    }

    enum Model {
        SPECTRUM_48K("Spectrum 48K"),
        SPECTRUM_128K("Spectrum 128K");

        private final String displayName;

        Model(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    record LoadedSnapshot(Path source, Format format, SpectrumSnapshot snapshot) {
        LoadedSnapshot {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    record SaveResult(Path target, Format format, Model model, String warning) {
        SaveResult {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(model, "model");
        }
    }

    private static Model modelOf(SpectrumSnapshot snapshot) {
        return switch (snapshot) {
            case Spectrum48Snapshot ignored -> Model.SPECTRUM_48K;
            case Spectrum128Snapshot ignored -> Model.SPECTRUM_128K;
        };
    }
}
