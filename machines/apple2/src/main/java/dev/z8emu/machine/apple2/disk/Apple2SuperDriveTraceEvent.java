package dev.z8emu.machine.apple2.disk;

public record Apple2SuperDriveTraceEvent(
        Source source,
        String region,
        boolean read,
        int address,
        int offset,
        int controllerPc,
        long controllerInstructions,
        int value,
        int bankSelect
) {
    public enum Source {
        HOST,
        CONTROLLER
    }
}
