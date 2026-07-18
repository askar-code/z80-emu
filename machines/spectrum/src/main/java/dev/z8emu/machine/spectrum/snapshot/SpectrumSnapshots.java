package dev.z8emu.machine.spectrum.snapshot;

import java.util.Objects;

/** Format-version routing that decodes completely before callers choose a machine model. */
public final class SpectrumSnapshots {
    private SpectrumSnapshots() {
    }

    public static SpectrumSnapshot decodeSna(byte[] image) throws SpectrumSnapshotException {
        Objects.requireNonNull(image, "image");
        return image.length == Sna48SnapshotCodec.IMAGE_SIZE
                ? Sna48SnapshotCodec.decode(image)
                : Sna128SnapshotCodec.decode(image);
    }

    public static SpectrumSnapshot decodeZ80(byte[] image) throws SpectrumSnapshotException {
        Objects.requireNonNull(image, "image");
        return image.length >= Z80V1SnapshotCodec.HEADER_SIZE && SnapshotBytes.le16(image, 6) == 0
                ? Z80V2V3SnapshotCodec.decode(image)
                : Z80V1SnapshotCodec.decode(image);
    }
}
