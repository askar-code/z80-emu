package dev.z8emu.machine.apple2.disk;

public record Apple2Disk2TraceEvent(
        String switchName,
        boolean read,
        int address,
        int offset,
        long tState,
        int value,
        int track,
        int halfTrack,
        int trackPosition,
        boolean motorOn,
        boolean spinning,
        boolean drive1Selected,
        boolean q6,
        boolean q7
) {
}
