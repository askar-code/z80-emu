package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.snapshot.Spectrum128Snapshot;
import dev.z8emu.machine.spectrum.snapshot.Spectrum48Snapshot;
import dev.z8emu.machine.spectrum.snapshot.Spectrum48Snapshots;
import dev.z8emu.machine.spectrum.snapshot.SpectrumSnapshotException;
import dev.z8emu.machine.spectrum.snapshot.Z80SnapshotState;
import dev.z8emu.machine.spectrum.snapshot.Z80V2V3SnapshotCodec;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumSnapshotFilesTest {
    @Test
    void savesCompressedV3Z80AndLoads48kIncludingZeroPcBack(@TempDir Path tempDir) throws Exception {
        Spectrum48Snapshot expected = sample48(0);
        Path target = tempDir.resolve("game.Z80");

        SpectrumSnapshotFiles.SaveResult result = SpectrumSnapshotFiles.save(target, expected);
        SpectrumSnapshotFiles.LoadedSnapshot loaded = SpectrumSnapshotFiles.load(target);
        Spectrum48Snapshot actual = assertInstanceOf(Spectrum48Snapshot.class, loaded.snapshot());

        assertEquals(SpectrumSnapshotFiles.Format.Z80, loaded.format());
        assertEquals(SpectrumSnapshotFiles.Model.SPECTRUM_48K, result.model());
        assertNull(result.warning());
        assert48Equals(expected, actual);
        byte[] image = Files.readAllBytes(target);
        assertEquals(0, le16(image, 6), "zero base PC selects Z80 v2/v3");
        assertEquals(54, le16(image, 30), "desktop saves canonical v3");
        assertTrue(le16(image, 86) != 0xFFFF, "first 48K page must be compressed");
    }

    @Test
    void savesCompressedV3Z80AndLoadsAll128kStateBack(@TempDir Path tempDir) throws Exception {
        Spectrum128Snapshot expected = sample128();
        Path target = tempDir.resolve("game.z80");

        SpectrumSnapshotFiles.SaveResult result = SpectrumSnapshotFiles.save(target, expected);
        SpectrumSnapshotFiles.LoadedSnapshot loaded = SpectrumSnapshotFiles.load(target);
        Spectrum128Snapshot actual = assertInstanceOf(Spectrum128Snapshot.class, loaded.snapshot());

        assertEquals(SpectrumSnapshotFiles.Model.SPECTRUM_128K, result.model());
        assertNull(result.warning());
        assert128Equals(expected, actual, true);
        byte[] image = Files.readAllBytes(target);
        assertEquals(0, le16(image, 6), "zero base PC selects Z80 v2/v3");
        assertEquals(54, le16(image, 30), "desktop saves canonical v3");
        assertTrue(le16(image, 86) != 0xFFFF, "first 128K page must be compressed");
    }

    @Test
    void loadsLegacyV2AndCanonicalV3ThroughTheSameDesktopBoundary(@TempDir Path tempDir) throws Exception {
        Path v2Path = tempDir.resolve("legacy-128-v2.z80");
        Files.write(v2Path, legacyV2Fixture());
        Spectrum128Snapshot v2 = assertInstanceOf(
                Spectrum128Snapshot.class,
                SpectrumSnapshotFiles.load(v2Path, SpectrumSnapshotFiles.Model.SPECTRUM_128K).snapshot()
        );
        assertEquals(0x5678, v2.cpu().pc());
        assertEquals(0x1B, v2.pagingPort7ffd());
        for (int bank = 0; bank < 8; bank++) {
            assertEquals(0x30 + bank, Byte.toUnsignedInt(v2.ramBank(bank)[0]));
        }

        Spectrum48Snapshot expectedV3 = sample48(0);
        Path v3Path = tempDir.resolve("modern-48-v3.z80");
        Files.write(v3Path, Z80V2V3SnapshotCodec.encode(
                expectedV3,
                Z80V2V3SnapshotCodec.Compression.COMPRESSED
        ));
        Spectrum48Snapshot v3 = assertInstanceOf(
                Spectrum48Snapshot.class,
                SpectrumSnapshotFiles.load(v3Path, SpectrumSnapshotFiles.Model.SPECTRUM_48K).snapshot()
        );
        assert48Equals(expectedV3, v3);
    }

    @Test
    void savesBothSnaModelsAndMakes128kAyLossExplicit(@TempDir Path tempDir) throws Exception {
        Spectrum48Snapshot expected48 = sample48(0x3456);
        Path target48 = tempDir.resolve("48.sna");
        SpectrumSnapshotFiles.save(target48, expected48);
        assert48Equals(
                expected48,
                assertInstanceOf(Spectrum48Snapshot.class, SpectrumSnapshotFiles.load(target48).snapshot())
        );

        Spectrum128Snapshot expected128 = sample128();
        Path target128 = tempDir.resolve("128.sna");
        SpectrumSnapshotFiles.SaveResult result = SpectrumSnapshotFiles.save(target128, expected128);
        Spectrum128Snapshot actual128 = assertInstanceOf(
                Spectrum128Snapshot.class,
                SpectrumSnapshotFiles.load(target128).snapshot()
        );

        assertEquals(SpectrumSnapshotFiles.SNA_128_AY_WARNING, result.warning());
        assertEquals(
                SpectrumSnapshotFiles.SNA_128_AY_WARNING,
                SpectrumSnapshotFiles.warning(target128, SpectrumSnapshotFiles.Model.SPECTRUM_128K)
        );
        assert128Equals(expected128, actual128, false);
        assertArrayEquals(new byte[16], actual128.ayRegisters(), "SNA has no AY fields");
        assertEquals(0, actual128.selectedAyRegister());
    }

    @Test
    void typedModelMismatchIsRejectedBeforeMachineMutation(@TempDir Path tempDir) throws Exception {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        Spectrum48Snapshot expected = sample48(0x4567);
        Spectrum48Snapshots.restore(machine, expected);
        Spectrum48Snapshot before = Spectrum48Snapshots.capture(machine);
        Path source = tempDir.resolve("wrong-model.z80");
        SpectrumSnapshotFiles.save(source, sample128());

        SpectrumSnapshotModelMismatchException loadMismatch = assertThrows(
                SpectrumSnapshotModelMismatchException.class,
                () -> SpectrumSnapshotFiles.load(source, SpectrumSnapshotFiles.Model.SPECTRUM_48K)
        );
        assertEquals(SpectrumSnapshotFiles.Model.SPECTRUM_48K, loadMismatch.expectedModel());
        assertEquals(SpectrumSnapshotFiles.Model.SPECTRUM_128K, loadMismatch.actualModel());
        assertEquals(source.toAbsolutePath().normalize(), loadMismatch.source());

        SpectrumSnapshotModelMismatchException restoreMismatch = assertThrows(
                SpectrumSnapshotModelMismatchException.class,
                () -> SpectrumSnapshotFiles.restore(machine, sample128())
        );
        assertNull(restoreMismatch.source());
        assert48Equals(before, Spectrum48Snapshots.capture(machine));
    }

    @Test
    void encodeFailureDoesNotReplaceExistingFile(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("existing.sna");
        byte[] original = new byte[]{1, 2, 3, 4};
        Files.write(target, original);

        assertThrows(
                SpectrumSnapshotException.class,
                () -> SpectrumSnapshotFiles.save(target, sample48(0, 0x4001))
        );

        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void rejectsAmbiguousSnapshotExtension(@TempDir Path tempDir) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SpectrumSnapshotFiles.save(tempDir.resolve("state.bin"), sample48(0x1234))
        );

        assertTrue(failure.getMessage().contains(".sna or .z80"));
    }

    private static Spectrum48Snapshot sample48(int pc) {
        return sample48(pc, 0x8002);
    }

    private static Spectrum48Snapshot sample48(int pc, int sp) {
        byte[] ram = new byte[Spectrum48Snapshot.RAM_SIZE];
        for (int offset = 0; offset < ram.length; offset++) {
            ram[offset] = (byte) ((offset * 17 + 3) & 0x0F);
        }
        ram[0x4000] = (byte) pc;
        ram[0x4001] = (byte) (pc >>> 8);
        return new Spectrum48Snapshot(cpu(pc, true, sp), 5, ram);
    }

    private static Spectrum128Snapshot sample128() {
        byte[][] banks = new byte[8][Spectrum128Snapshot.RAM_BANK_SIZE];
        for (int bank = 0; bank < banks.length; bank++) {
            for (int offset = 0; offset < banks[bank].length; offset++) {
                banks[bank][offset] = (byte) (0x20 + bank);
            }
        }
        byte[] ay = {
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16
        };
        return new Spectrum128Snapshot(cpu(0x5678, true), 6, 0x1B, 7, ay, banks);
    }

    private static Z80SnapshotState cpu(int pc, boolean equalIff) {
        return cpu(pc, equalIff, 0x8002);
    }

    private static Z80SnapshotState cpu(int pc, boolean equalIff, int sp) {
        return new Z80SnapshotState(
                0x1234, 0x2345, 0x3456, 0x4567,
                0x5678, 0x6789, 0x789A, 0x89AB,
                0x9ABC, 0xABCD, sp, pc,
                0xBC, 0xCD, true, equalIff, 2
        );
    }

    private static void assert48Equals(Spectrum48Snapshot expected, Spectrum48Snapshot actual) {
        assertEquals(expected.cpu(), actual.cpu());
        assertEquals(expected.borderColor(), actual.borderColor());
        assertArrayEquals(expected.ram(), actual.ram());
    }

    private static void assert128Equals(
            Spectrum128Snapshot expected,
            Spectrum128Snapshot actual,
            boolean includeAy
    ) {
        assertEquals(expected.cpu(), actual.cpu());
        assertEquals(expected.borderColor(), actual.borderColor());
        assertEquals(expected.pagingPort7ffd(), actual.pagingPort7ffd());
        if (includeAy) {
            assertEquals(expected.selectedAyRegister(), actual.selectedAyRegister());
            assertArrayEquals(expected.ayRegisters(), actual.ayRegisters());
        }
        for (int bank = 0; bank < 8; bank++) {
            assertArrayEquals(expected.ramBank(bank), actual.ramBank(bank), "RAM bank " + bank);
        }
    }

    private static int le16(byte[] image, int offset) {
        return Byte.toUnsignedInt(image[offset]) | (Byte.toUnsignedInt(image[offset + 1]) << 8);
    }

    private static byte[] legacyV2Fixture() {
        byte[] header = new byte[55];
        header[0] = 0x12;
        header[1] = 0x34;
        putLe16(header, 8, 0x8002);
        header[10] = 0x56;
        header[11] = 0x21;
        header[12] = 6 << 1;
        putLe16(header, 30, 23);
        putLe16(header, 32, 0x5678);
        header[34] = 3;
        header[35] = 0x1B;
        header[38] = 7;
        for (int register = 0; register < 16; register++) {
            header[39 + register] = (byte) register;
        }

        ByteArrayOutputStream image = new ByteArrayOutputStream();
        image.writeBytes(header);
        for (int bank = 0; bank < 8; bank++) {
            image.write(0xFF);
            image.write(0xFF);
            image.write(bank + 3);
            image.writeBytes(filledBank(0x30 + bank));
        }
        return image.toByteArray();
    }

    private static byte[] filledBank(int value) {
        byte[] bank = new byte[Spectrum128Snapshot.RAM_BANK_SIZE];
        java.util.Arrays.fill(bank, (byte) value);
        return bank;
    }

    private static void putLe16(byte[] image, int offset, int value) {
        image[offset] = (byte) value;
        image[offset + 1] = (byte) (value >>> 8);
    }
}
