package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.Apple2Memory;
import dev.z8emu.machine.apple2.disk.Apple2DosDiskImage;
import dev.z8emu.machine.apple2.disk.Apple2ProDosBlockImage;
import dev.z8emu.machine.cpc.memory.CpcMemory;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopMachineDefinitionsTest {
    @Test
    void parsesMachineAliasesThroughDefinitions() {
        assertEquals(DesktopMachineKind.SPECTRUM48, DesktopMachineDefinitions.parse("48k").kind());
        assertEquals(DesktopMachineKind.SPECTRUM128, DesktopMachineDefinitions.parse("spectrum128").kind());
        assertEquals(DesktopMachineKind.RADIO86RK, DesktopMachineDefinitions.parse("rk86").kind());
        assertEquals(DesktopMachineKind.CPC6128, DesktopMachineDefinitions.parse("amstradcpc6128").kind());
        assertEquals(DesktopMachineKind.APPLE2, DesktopMachineDefinitions.parse("appleii").kind());
        assertEquals(DesktopMachineKind.APPLE2, DesktopMachineDefinitions.parse("apple2plus").kind());
        assertEquals(DesktopMachineKind.APPLE2E, DesktopMachineDefinitions.parse("apple2e").kind());
        assertEquals(DesktopMachineKind.APPLE2E, DesktopMachineDefinitions.parse("appleiie").kind());
        assertEquals(DesktopMachineKind.APPLE2E, DesktopMachineDefinitions.parse("apple2e-128k").kind());
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.parse("apple2cplus"));
    }

    @Test
    void demoConfigKeepsTheSpectrum48Fallback() {
        DesktopLaunchConfig config = DesktopMachineDefinitions.demoConfig();

        assertEquals(DesktopMachineKind.SPECTRUM48, config.machineKind());
        assertTrue(config.demoMode());
        assertEquals(Spectrum48kMemoryMap.ROM_SIZE, config.romImage().length);
        assertFalse(config.loadedMedia(DesktopLaunchConfig.LoadedSpectrumTape.class).isPresent());
    }

    @Test
    void validatesMachineSpecificRomSizes() {
        Path label = Path.of("rom.bin");

        assertDoesNotThrow(() -> DesktopMachineDefinitions.forKind(DesktopMachineKind.SPECTRUM48)
                .validateRom(new byte[Spectrum48kMemoryMap.ROM_SIZE], label));
        assertDoesNotThrow(() -> DesktopMachineDefinitions.forKind(DesktopMachineKind.SPECTRUM128)
                .validateRom(new byte[Spectrum128Machine.ROM_IMAGE_SIZE], label));
        assertDoesNotThrow(() -> DesktopMachineDefinitions.forKind(DesktopMachineKind.RADIO86RK)
                .validateRom(new byte[Radio86Memory.ROM_SIZE_4K], label));
        assertDoesNotThrow(() -> DesktopMachineDefinitions.forKind(DesktopMachineKind.CPC6128)
                .validateRom(new byte[CpcMemory.ROM_IMAGE_SIZE_OS_BASIC_AMSDOS], label));
        assertDoesNotThrow(() -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2)
                .validateRom(new byte[Apple2Memory.SYSTEM_ROM_SIZE_12K], label));
        assertDoesNotThrow(() -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2)
                .validateRom(new byte[Apple2Memory.ADDRESS_SPACE_SIZE], label));
        assertDoesNotThrow(() -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2E)
                .validateRom(new byte[Apple2Memory.SYSTEM_ROM_SIZE_12K], label));
        assertDoesNotThrow(() -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2E)
                .validateRom(new byte[Apple2Memory.SYSTEM_ROM_SIZE_16K], label));
        assertDoesNotThrow(() -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2E)
                .validateRom(new byte[Apple2Memory.ADDRESS_SPACE_SIZE], label));

        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.SPECTRUM48)
                .validateRom(new byte[1], label));
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.CPC6128)
                .validateRom(new byte[1], label));
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2)
                .validateRom(new byte[1], label));
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2)
                .validateRom(new byte[16 * 1024], label));
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2E)
                .validateRom(new byte[32 * 1024], label));
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2)
                .validateRom(new byte[Apple2Memory.ADDRESS_SPACE_SIZE + 1], label));
    }

    @Test
    void loadsEnhancedAppleIIeRomBundleFromStandardLocalParts(@TempDir Path tempDir) throws Exception {
        Path cdRom = tempDir.resolve("342-0304-a.e10");
        Path efRom = tempDir.resolve("342-0303-a.e8");
        byte[] cdBytes = filledBytes(Apple2Memory.SYSTEM_ROM_SIZE_8K, 0xC0);
        byte[] efBytes = filledBytes(Apple2Memory.SYSTEM_ROM_SIZE_8K, 0xE0);
        cdBytes[0] = 0x12;
        efBytes[efBytes.length - 1] = 0x34;
        Files.write(cdRom, cdBytes);
        Files.write(efRom, efBytes);

        byte[] bundleFromPart = DesktopMachineDefinitions.loadRom(
                efRom,
                DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2E)
        );
        byte[] bundleFromDirectory = DesktopMachineDefinitions.loadRom(
                tempDir,
                DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2E)
        );

        assertEquals(Apple2Memory.SYSTEM_ROM_SIZE_16K, bundleFromPart.length);
        assertEquals(0x12, Byte.toUnsignedInt(bundleFromPart[0]));
        assertEquals(0x34, Byte.toUnsignedInt(bundleFromPart[bundleFromPart.length - 1]));
        assertEquals(0x12, Byte.toUnsignedInt(bundleFromDirectory[0]));
        assertEquals(0x34, Byte.toUnsignedInt(bundleFromDirectory[bundleFromDirectory.length - 1]));
    }

    @Test
    void loadsApple2RawProgramMediaWithDefaultAndExplicitAddresses(@TempDir Path tempDir) throws Exception {
        Path programPath = tempDir.resolve("tiny.bin");
        Files.write(programPath, new byte[]{(byte) 0xA9, (byte) 0xC1, (byte) 0x8D, 0x00, 0x04});
        DesktopMachineDefinition apple2 = DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2);

        DesktopLaunchConfig.LoadedMedia defaultMedia = apple2.loadMedia(programPath.toString(), DesktopLaunchOptions.empty());
        assertTrue(defaultMedia instanceof DesktopLaunchConfig.LoadedApple2Program);
        DesktopLaunchConfig.LoadedApple2Program defaultProgram = (DesktopLaunchConfig.LoadedApple2Program) defaultMedia;
        assertEquals(programPath.toAbsolutePath().normalize().toString(), defaultProgram.sourceLabel());
        assertEquals(0x0800, defaultProgram.loadAddress());
        assertEquals(5, defaultProgram.programImage().length);

        DesktopLaunchConfig.LoadedMedia explicitMedia = apple2.loadMedia(
                programPath.toString(),
                new DesktopLaunchOptions(0x2000, 0x2000)
        );
        DesktopLaunchConfig.LoadedApple2Program explicitProgram = (DesktopLaunchConfig.LoadedApple2Program) explicitMedia;
        assertEquals(0x2000, explicitProgram.loadAddress());
    }

    @Test
    void loadsApple2DosOrderedDiskMediaByImageSize(@TempDir Path tempDir) throws Exception {
        Path diskPath = tempDir.resolve("oregon.do");
        Files.write(diskPath, new byte[Apple2DosDiskImage.IMAGE_SIZE]);
        DesktopMachineDefinition apple2 = DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2);

        DesktopLaunchConfig.LoadedMedia media = apple2.loadMedia(diskPath.toString(), DesktopLaunchOptions.empty());

        assertTrue(media instanceof DesktopLaunchConfig.LoadedApple2Disk);
        assertEquals(diskPath.toAbsolutePath().normalize().toString(), media.sourceLabel());
    }

    @Test
    void loadsApple2WozDiskMediaByHeader(@TempDir Path tempDir) throws Exception {
        Path diskPath = tempDir.resolve("prince-side-a.woz");
        Files.write(diskPath, wozImage());
        DesktopMachineDefinition apple2 = DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2E);

        DesktopLaunchConfig.LoadedMedia media = apple2.loadMedia(diskPath.toString(), DesktopLaunchOptions.empty());

        assertTrue(media instanceof DesktopLaunchConfig.LoadedApple2WozDisk);
        DesktopLaunchConfig.LoadedApple2WozDisk wozMedia = (DesktopLaunchConfig.LoadedApple2WozDisk) media;
        assertEquals(diskPath.toAbsolutePath().normalize().toString(), wozMedia.sourceLabel());
        assertTrue(wozMedia.diskImage().writeProtected());
    }

    @Test
    void loadsApple2ProDosBlockImagesAsRecognizedBlockDevices(@TempDir Path tempDir) throws Exception {
        Path diskPath = tempDir.resolve("prince-of-persia.po");
        byte[] image = new byte[Apple2ProDosBlockImage.IMAGE_SIZE_800K];
        int root = Apple2ProDosBlockImage.BLOCK_SIZE * 2;
        image[root + 4] = (byte) 0xF3;
        image[root + 5] = 'P';
        image[root + 6] = 'O';
        image[root + 7] = 'P';
        int entry = root + 43;
        image[entry] = (byte) 0x26;
        image[entry + 1] = 'P';
        image[entry + 2] = 'R';
        image[entry + 3] = 'O';
        image[entry + 4] = 'D';
        image[entry + 5] = 'O';
        image[entry + 6] = 'S';
        Files.write(diskPath, image);
        DesktopMachineDefinition apple2 = DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2);

        DesktopLaunchConfig.LoadedMedia media = apple2.loadMedia(diskPath.toString(), DesktopLaunchOptions.empty());

        assertTrue(media instanceof DesktopLaunchConfig.LoadedApple2BlockDevice);
        DesktopLaunchConfig.LoadedApple2BlockDevice blockMedia = (DesktopLaunchConfig.LoadedApple2BlockDevice) media;
        assertEquals(diskPath.toAbsolutePath().normalize().toString(), blockMedia.sourceLabel());
        assertEquals(Apple2ProDosBlockImage.BLOCK_SIZE, blockMedia.blockDevice().blockSize());
        assertEquals(Apple2ProDosBlockImage.BLOCK_COUNT_800K, blockMedia.blockDevice().blockCount());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> apple2.open(new DesktopLaunchConfig(
                "apple2plus-12k.rom",
                new byte[Apple2Memory.SYSTEM_ROM_SIZE_12K],
                false,
                blockMedia,
                DesktopLaunchOptions.empty(),
                DesktopMachineKind.APPLE2
        )));
        assertTrue(failure.getMessage().contains("not desktop-bootable"));
    }

    @Test
    void apple2LaunchOptionsPermitStartOnlyButRequireMediaForLoadAddress() {
        DesktopMachineDefinition apple2 = DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2);
        DesktopMachineDefinition spectrum = DesktopMachineDefinitions.forKind(DesktopMachineKind.SPECTRUM48);
        DesktopMachineDefinition apple2e = DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2E);

        assertDoesNotThrow(() -> apple2.validateLaunchOptions(new DesktopLaunchOptions(null, 0x0800), 1));
        assertDoesNotThrow(() -> apple2.validateLaunchOptions(new DesktopLaunchOptions(null, null, Path.of("disk2.rom")), 1));
        assertDoesNotThrow(() -> apple2e.validateLaunchOptions(new DesktopLaunchOptions(null, 0x0800), 1));
        assertDoesNotThrow(() -> apple2e.validateLaunchOptions(new DesktopLaunchOptions(null, null, Path.of("disk2.rom")), 1));
        assertThrows(IllegalArgumentException.class,
                () -> apple2.validateLaunchOptions(new DesktopLaunchOptions(0x0800, null), 1));
        assertThrows(IllegalArgumentException.class,
                () -> spectrum.validateLaunchOptions(new DesktopLaunchOptions(null, 0x0800), 1));
        assertThrows(IllegalArgumentException.class,
                () -> spectrum.validateLaunchOptions(new DesktopLaunchOptions(null, null, Path.of("disk2.rom")), 1));
    }

    @Test
    void launchOptionAddressesAreHexadecimal() {
        assertEquals(0x0800, DesktopLaunchOptions.parseAddress("0800"));
        assertEquals(0x0800, DesktopLaunchOptions.parseAddress("0x0800"));
        assertEquals(0x0800, DesktopLaunchOptions.parseAddress("$0800"));
        assertThrows(IllegalArgumentException.class, () -> DesktopLaunchOptions.parseAddress("10000"));
    }

    private static byte[] filledBytes(int length, int value) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) value;
        }
        return bytes;
    }

    private static byte[] wozImage() {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        output.writeBytes(new byte[]{'W', 'O', 'Z', '1', (byte) 0xFF, 0x0A, 0x0D, 0x0A, 0, 0, 0, 0});

        byte[] info = new byte[60];
        info[0] = 1;
        info[1] = 1;
        info[2] = 1;
        byte[] creator = "Synthetic WOZ".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(creator, 0, info, 5, creator.length);
        writeChunk(output, "INFO", info);

        byte[] tmap = new byte[160];
        java.util.Arrays.fill(tmap, (byte) 0xFF);
        tmap[0] = 0;
        writeChunk(output, "TMAP", tmap);

        byte[] trks = new byte[6656];
        byte[] trackBits = new byte[]{(byte) 0xD5, (byte) 0xAA, (byte) 0x96};
        System.arraycopy(trackBits, 0, trks, 0, trackBits.length);
        putWord(trks, 6646, trackBits.length);
        putWord(trks, 6648, trackBits.length * 8);
        writeChunk(output, "TRKS", trks);
        return output.toByteArray();
    }

    private static void writeChunk(java.io.ByteArrayOutputStream output, String id, byte[] data) {
        output.writeBytes(id.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        putInt(output, data.length);
        output.writeBytes(data);
    }

    private static void putWord(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void putInt(java.io.ByteArrayOutputStream output, int value) {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 24) & 0xFF);
    }
}
