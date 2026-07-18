package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.snapshot.Spectrum128Snapshot;
import dev.z8emu.machine.spectrum.snapshot.Spectrum48Snapshot;
import dev.z8emu.machine.spectrum.snapshot.Z80SnapshotState;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopLauncherSpectrumSnapshotTest {
    @Test
    void acceptsSnapshotAsSecond48kPositionalArgument(@TempDir Path tempDir) throws Exception {
        Path rom = tempDir.resolve("48.rom");
        Path snapshot = tempDir.resolve("game.z80");
        Files.write(rom, new byte[Spectrum48kMemoryMap.ROM_SIZE]);
        SpectrumSnapshotFiles.save(snapshot, snapshotState());

        DesktopLaunchConfig config = DesktopLauncher.createLaunchConfig(new String[]{
                "--machine=48",
                rom.toString(),
                snapshot.toString()
        });

        assertEquals(DesktopMachineKind.SPECTRUM48, config.machineKind());
        DesktopLaunchConfig.LoadedSpectrumSnapshot loaded = config
                .loadedMedia(DesktopLaunchConfig.LoadedSpectrumSnapshot.class)
                .orElseThrow();
        assertEquals(0x4567, loaded.snapshot().cpu().pc());
    }

    @Test
    void acceptsMatching128kSnapshotAsSecondArgument(@TempDir Path tempDir) throws Exception {
        Path rom = tempDir.resolve("128.rom");
        Path snapshot = tempDir.resolve("game.z80");
        Files.write(rom, new byte[Spectrum128Machine.ROM_IMAGE_SIZE]);
        SpectrumSnapshotFiles.save(snapshot, snapshot128State());

        DesktopLaunchConfig config = DesktopLauncher.createLaunchConfig(new String[]{
                "--machine=128",
                rom.toString(),
                snapshot.toString()
        });

        assertEquals(DesktopMachineKind.SPECTRUM128, config.machineKind());
        DesktopLaunchConfig.LoadedSpectrumSnapshot loaded = config
                .loadedMedia(DesktopLaunchConfig.LoadedSpectrumSnapshot.class)
                .orElseThrow();
        assertEquals(0x5678, loaded.snapshot().cpu().pc());
    }

    @Test
    void rejectsDecodedWrongModelSnapshotWithTypedMismatch(@TempDir Path tempDir) throws Exception {
        Path rom = tempDir.resolve("128.rom");
        Path snapshot = tempDir.resolve("48.z80");
        Files.write(rom, new byte[Spectrum128Machine.ROM_IMAGE_SIZE]);
        SpectrumSnapshotFiles.save(snapshot, snapshotState());

        SpectrumSnapshotModelMismatchException failure = assertThrows(
                SpectrumSnapshotModelMismatchException.class,
                () -> DesktopLauncher.createLaunchConfig(new String[]{
                        "--machine=128",
                        rom.toString(),
                        snapshot.toString()
                })
        );

        assertEquals(SpectrumSnapshotFiles.Model.SPECTRUM_128K, failure.expectedModel());
        assertEquals(SpectrumSnapshotFiles.Model.SPECTRUM_48K, failure.actualModel());
        assertTrue(failure.getMessage().contains(snapshot.toAbsolutePath().normalize().toString()));
    }

    private static Spectrum48Snapshot snapshotState() {
        return new Spectrum48Snapshot(
                new Z80SnapshotState(
                        0x1234,
                        0x2345,
                        0x3456,
                        0x4567,
                        0x5678,
                        0x6789,
                        0x789A,
                        0x89AB,
                        0x9ABC,
                        0xABCD,
                        0x8002,
                        0x4567,
                        0xBC,
                        0xCD,
                        true,
                        true,
                        2
                ),
                5,
                new byte[Spectrum48Snapshot.RAM_SIZE]
        );
    }

    private static Spectrum128Snapshot snapshot128State() {
        byte[][] banks = new byte[8][Spectrum128Snapshot.RAM_BANK_SIZE];
        return new Spectrum128Snapshot(
                new Z80SnapshotState(
                        0x1234, 0x2345, 0x3456, 0x4567,
                        0x5678, 0x6789, 0x789A, 0x89AB,
                        0x9ABC, 0xABCD, 0x8002, 0x5678,
                        0xBC, 0xCD, true, true, 2
                ),
                6,
                0x1B,
                3,
                new byte[16],
                banks
        );
    }
}
