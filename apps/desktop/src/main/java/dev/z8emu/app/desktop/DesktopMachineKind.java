package dev.z8emu.app.desktop;

enum DesktopMachineKind {
    SPECTRUM48,
    SPECTRUM128,
    RADIO86RK,
    CPC6128,
    APPLE2;

    boolean isSpectrum() {
        return this == SPECTRUM48 || this == SPECTRUM128;
    }
}
