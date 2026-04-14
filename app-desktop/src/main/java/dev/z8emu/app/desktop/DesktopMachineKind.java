package dev.z8emu.app.desktop;

import java.util.Locale;

enum DesktopMachineKind {
    SPECTRUM48("48"),
    SPECTRUM128("128"),
    RADIO86RK("radio86rk");

    private final String cliValue;

    DesktopMachineKind(String cliValue) {
        this.cliValue = cliValue;
    }

    static DesktopMachineKind parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "48", "48k", "spectrum48", "spectrum48k" -> SPECTRUM48;
            case "128", "128k", "spectrum128", "spectrum128k" -> SPECTRUM128;
            case "radio86", "radio86rk", "rk86", "86rk" -> RADIO86RK;
            default -> throw new IllegalArgumentException("Unknown machine kind: " + value);
        };
    }
}
