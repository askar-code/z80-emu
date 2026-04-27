package dev.z8emu.machine.apple2.disk;

@FunctionalInterface
public interface Apple2Disk2TraceSink {
    Apple2Disk2TraceSink NONE = event -> {
    };

    void traceDisk2(Apple2Disk2TraceEvent event);
}
