package dev.z8emu.app.desktop;

import dev.z8emu.machine.cpc.disk.CpcDskImage;
import dev.z8emu.machine.apple2.disk.Apple2DosDiskImage;
import dev.z8emu.machine.radio86rk.tape.Radio86TapeFile;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import java.util.Optional;

record DesktopLaunchConfig(
        String sourceLabel,
        byte[] romImage,
        boolean demoMode,
        LoadedMedia loadedMedia,
        DesktopLaunchOptions launchOptions,
        DesktopMachineKind machineKind
) {
    <T extends LoadedMedia> Optional<T> loadedMedia(Class<T> mediaType) {
        return mediaType.isInstance(loadedMedia)
                ? Optional.of(mediaType.cast(loadedMedia))
                : Optional.empty();
    }

    sealed interface LoadedMedia permits LoadedSpectrumTape, LoadedRadioTape, LoadedCpcDisk, LoadedApple2Program, LoadedApple2Disk {
        String sourceLabel();
    }

    record LoadedSpectrumTape(String sourceLabel, TapeFile tapeFile) implements LoadedMedia {
    }

    record LoadedRadioTape(String sourceLabel, Radio86TapeFile tapeFile) implements LoadedMedia {
    }

    record LoadedCpcDisk(String sourceLabel, CpcDskImage diskImage) implements LoadedMedia {
    }

    record LoadedApple2Program(String sourceLabel, byte[] programImage, int loadAddress) implements LoadedMedia {
    }

    record LoadedApple2Disk(String sourceLabel, Apple2DosDiskImage diskImage) implements LoadedMedia {
    }
}
