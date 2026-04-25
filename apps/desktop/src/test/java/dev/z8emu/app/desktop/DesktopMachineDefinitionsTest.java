package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.Apple2Memory;
import dev.z8emu.machine.cpc.memory.CpcMemory;
import dev.z8emu.machine.radio86rk.memory.Radio86Memory;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

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
}
