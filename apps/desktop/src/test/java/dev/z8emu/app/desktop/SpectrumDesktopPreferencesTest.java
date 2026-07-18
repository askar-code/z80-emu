package dev.z8emu.app.desktop;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumDesktopPreferencesTest {
    private String previousJoystickProperty;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void clearJoystickOverride() {
        previousJoystickProperty = System.getProperty(SpectrumJoystickProfile.PROPERTY_NAME);
        System.clearProperty(SpectrumJoystickProfile.PROPERTY_NAME);
    }

    @AfterEach
    void restoreJoystickOverride() {
        if (previousJoystickProperty == null) {
            System.clearProperty(SpectrumJoystickProfile.PROPERTY_NAME);
        } else {
            System.setProperty(SpectrumJoystickProfile.PROPERTY_NAME, previousJoystickProperty);
        }
    }

    @Test
    void persistsProfileAndExistingMediaDirectories() {
        MapBackend backend = new MapBackend();
        SpectrumDesktopPreferences preferences = new SpectrumDesktopPreferences(backend);

        preferences.rememberJoystickProfile(SpectrumJoystickProfile.SINCLAIR_2);
        preferences.rememberTape(temporaryDirectory.resolve("side-a.tzx"));
        preferences.rememberSnapshot(temporaryDirectory.resolve("save.z80"));

        assertEquals(SpectrumJoystickProfile.SINCLAIR_2, preferences.initialJoystickProfile());
        assertEquals(temporaryDirectory, preferences.tapeDirectory().orElseThrow());
        assertEquals(temporaryDirectory, preferences.snapshotDirectory().orElseThrow());
    }

    @Test
    void explicitSystemPropertyOverridesPersistedProfile() {
        MapBackend backend = new MapBackend();
        SpectrumDesktopPreferences preferences = new SpectrumDesktopPreferences(backend);
        preferences.rememberJoystickProfile(SpectrumJoystickProfile.SINCLAIR_1);
        System.setProperty(SpectrumJoystickProfile.PROPERTY_NAME, "kempston");

        assertEquals(SpectrumJoystickProfile.KEMPSTON, preferences.initialJoystickProfile());
    }

    @Test
    void staleDirectoryIsIgnored() {
        MapBackend backend = new MapBackend();
        backend.put("tape-directory", temporaryDirectory.resolve("missing").toString());

        SpectrumDesktopPreferences preferences = new SpectrumDesktopPreferences(backend);

        assertTrue(preferences.tapeDirectory().isEmpty());
    }

    private static final class MapBackend implements SpectrumDesktopPreferences.Backend {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key, String fallback) {
            return values.getOrDefault(key, fallback);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }
    }
}
