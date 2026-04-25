package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.Apple2Memory;
import dev.z8emu.machine.apple2.disk.Apple2DosDiskImage;
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
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.parse("apple2e"));
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

        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.SPECTRUM48)
                .validateRom(new byte[1], label));
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.CPC6128)
                .validateRom(new byte[1], label));
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2)
                .validateRom(new byte[1], label));
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2)
                .validateRom(new byte[16 * 1024], label));
        assertThrows(IllegalArgumentException.class, () -> DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2)
                .validateRom(new byte[Apple2Memory.ADDRESS_SPACE_SIZE + 1], label));
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
    void apple2LaunchOptionsPermitStartOnlyButRequireMediaForLoadAddress() {
        DesktopMachineDefinition apple2 = DesktopMachineDefinitions.forKind(DesktopMachineKind.APPLE2);
        DesktopMachineDefinition spectrum = DesktopMachineDefinitions.forKind(DesktopMachineKind.SPECTRUM48);

        assertDoesNotThrow(() -> apple2.validateLaunchOptions(new DesktopLaunchOptions(null, 0x0800), 1));
        assertDoesNotThrow(() -> apple2.validateLaunchOptions(new DesktopLaunchOptions(null, null, Path.of("disk2.rom")), 1));
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
}
