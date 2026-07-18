package dev.z8emu.machine.spectrum.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SpectrumSnapshotsTest {
    @Test
    void routesSnaByCanonicalWireSize() throws Exception {
        Spectrum48Snapshot snapshot48 = new Spectrum48Snapshot(
                cpu(0x4567),
                0,
                ram48()
        );
        Spectrum128Snapshot snapshot128 = snapshot128();

        assertInstanceOf(
                Spectrum48Snapshot.class,
                SpectrumSnapshots.decodeSna(Sna48SnapshotCodec.encode(snapshot48))
        );
        assertInstanceOf(
                Spectrum128Snapshot.class,
                SpectrumSnapshots.decodeSna(Sna128SnapshotCodec.encode(snapshot128))
        );
    }

    @Test
    void routesZ80ByBaseHeaderPcSentinel() throws Exception {
        Spectrum48Snapshot snapshot48 = new Spectrum48Snapshot(cpu(0x4567), 0, ram48());
        Spectrum128Snapshot snapshot128 = snapshot128();

        assertInstanceOf(
                Spectrum48Snapshot.class,
                SpectrumSnapshots.decodeZ80(Z80V1SnapshotCodec.encode(
                        snapshot48,
                        Z80V1SnapshotCodec.Compression.UNCOMPRESSED
                ))
        );
        assertInstanceOf(
                Spectrum128Snapshot.class,
                SpectrumSnapshots.decodeZ80(Z80V2V3SnapshotCodec.encode(
                        snapshot128,
                        Z80V2V3SnapshotCodec.Compression.UNCOMPRESSED
                ))
        );
    }

    private static Spectrum128Snapshot snapshot128() {
        byte[][] banks = new byte[8][16_384];
        return new Spectrum128Snapshot(cpu(0x4567), 0, 3, 0, new byte[16], banks);
    }

    private static byte[] ram48() {
        return new byte[Spectrum48Snapshot.RAM_SIZE];
    }

    private static Z80SnapshotState cpu(int pc) {
        return new Z80SnapshotState(
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0x9002, pc, 0, 0,
                false, false, 0
        );
    }
}
