package dev.z8emu.app.desktop;

import java.nio.file.Path;
import java.util.Locale;

record DesktopLaunchOptions(Integer loadAddress, Integer startAddress, Path disk2RomPath) {
    DesktopLaunchOptions(Integer loadAddress, Integer startAddress) {
        this(loadAddress, startAddress, null);
    }

    static DesktopLaunchOptions empty() {
        return new DesktopLaunchOptions(null, null, null);
    }

    boolean hasLoadAddress() {
        return loadAddress != null;
    }

    boolean hasStartAddress() {
        return startAddress != null;
    }

    boolean hasDisk2RomPath() {
        return disk2RomPath != null;
    }

    boolean hasAny() {
        return hasLoadAddress() || hasStartAddress() || hasDisk2RomPath();
    }

    int loadAddressOr(int fallback) {
        return hasLoadAddress() ? loadAddress : fallback;
    }

    static int parseAddress(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace("_", "");
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        int address = Integer.parseInt(normalized, 16);
        if (address < 0 || address > 0xFFFF) {
            throw new IllegalArgumentException("Address out of 16-bit range: " + value);
        }
        return address;
    }
}
