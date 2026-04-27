package dev.z8emu.machine.apple2.disk;

@FunctionalInterface
public interface Apple2SuperDriveTraceSink {
    Apple2SuperDriveTraceSink NONE = event -> {
    };

    void traceSuperDrive(Apple2SuperDriveTraceEvent event);
}
