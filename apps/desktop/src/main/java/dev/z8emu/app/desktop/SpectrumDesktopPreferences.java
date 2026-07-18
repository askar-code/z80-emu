package dev.z8emu.app.desktop;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;

/** Small, Spectrum-scoped desktop preferences with an injectable test backend. */
final class SpectrumDesktopPreferences {
    private static final String NODE_NAME = "spectrum";
    private static final String JOYSTICK_PROFILE = "joystick-profile";
    private static final String TAPE_DIRECTORY = "tape-directory";
    private static final String SNAPSHOT_DIRECTORY = "snapshot-directory";

    private final Backend backend;

    SpectrumDesktopPreferences(Backend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    static SpectrumDesktopPreferences userPreferences() {
        Preferences preferences = Preferences.userNodeForPackage(SpectrumDesktopRunner.class).node(NODE_NAME);
        return new SpectrumDesktopPreferences(new Backend() {
            @Override
            public String get(String key, String fallback) {
                return preferences.get(key, fallback);
            }

            @Override
            public void put(String key, String value) {
                preferences.put(key, value);
            }
        });
    }

    SpectrumJoystickProfile initialJoystickProfile() {
        String commandLineOverride = System.getProperty(SpectrumJoystickProfile.PROPERTY_NAME);
        if (commandLineOverride != null) {
            return SpectrumJoystickProfile.fromSetting(commandLineOverride);
        }
        return SpectrumJoystickProfile.fromSetting(backend.get(JOYSTICK_PROFILE, null));
    }

    void rememberJoystickProfile(SpectrumJoystickProfile profile) {
        backend.put(JOYSTICK_PROFILE, Objects.requireNonNull(profile, "profile").settingValue());
    }

    Optional<Path> tapeDirectory() {
        return existingDirectory(TAPE_DIRECTORY);
    }

    Optional<Path> snapshotDirectory() {
        return existingDirectory(SNAPSHOT_DIRECTORY);
    }

    void rememberTape(Path source) {
        rememberParentDirectory(TAPE_DIRECTORY, source);
    }

    void rememberSnapshot(Path sourceOrTarget) {
        rememberParentDirectory(SNAPSHOT_DIRECTORY, sourceOrTarget);
    }

    private Optional<Path> existingDirectory(String key) {
        String value = backend.get(key, null);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            Path directory = Path.of(value).toAbsolutePath().normalize();
            return Files.isDirectory(directory) ? Optional.of(directory) : Optional.empty();
        } catch (InvalidPathException ignored) {
            return Optional.empty();
        }
    }

    private void rememberParentDirectory(String key, Path sourceOrTarget) {
        Path normalized = Objects.requireNonNull(sourceOrTarget, "sourceOrTarget")
                .toAbsolutePath()
                .normalize();
        Path directory = Files.isDirectory(normalized) ? normalized : normalized.getParent();
        if (directory != null && Files.isDirectory(directory)) {
            backend.put(key, directory.toString());
        }
    }

    interface Backend {
        String get(String key, String fallback);

        void put(String key, String value);
    }
}
