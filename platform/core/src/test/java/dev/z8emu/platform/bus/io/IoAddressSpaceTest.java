package dev.z8emu.platform.bus.io;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IoAddressSpaceTest {
    @Test
    void routesRangeReadsAndWritesWithLocalOffsets() {
        IoAddressSpace ports = IoAddressSpace.withUnmappedValue(0xFF);
        int[] registers = {0x11, 0x22};

        ports.mapReadWrite(
                "registers",
                IoSelector.range(0x10, 0x11),
                access -> registers[access.offset()],
                (access, value) -> registers[access.offset()] = value
        );

        assertEquals(0x11, ports.read(0x10));
        assertEquals(0x22, ports.read(0x11));

        ports.write(0x11, 0x7A);

        assertEquals(0x7A, ports.read(0x11));
    }

    @Test
    void masksDecodeBitsAndDerivesOffsetsFromSelectedAddressBits() {
        IoAddressSpace ports = IoAddressSpace.withUnmappedValue(0xFF);
        int[] registers = {0x10, 0x20, 0x30, 0x40};

        ports.mapRead(
                "masked-registers",
                IoSelector.mask(0x0800, 0x0000, 0x0300, 8),
                access -> registers[access.offset()]
        );

        assertEquals(0x10, ports.read(0xF000));
        assertEquals(0x20, ports.read(0xF100));
        assertEquals(0x30, ports.read(0xF200));
        assertEquals(0x40, ports.read(0xF300));
        assertEquals(0xFF, ports.read(0xF800));
    }

    @Test
    void mirroredRangesIgnoreMirrorBitsInHandlerOffsets() {
        IoAddressSpace ports = IoAddressSpace.withUnmappedValue(0xFF);
        List<Integer> offsets = new ArrayList<>();

        ports.mapRead("mirrored", IoSelector.mirroredRange(0xC000, 0xC000, 0x000F), access -> {
            offsets.add(access.offset());
            return 0x33;
        });

        assertEquals(0x33, ports.read(0xC000));
        assertEquals(0x33, ports.read(0xC00F));
        assertEquals(List.of(0, 0), offsets);
    }

    @Test
    void exactSelectorMatchesOnlyOneAddress() {
        IoAddressSpace ports = IoAddressSpace.withUnmappedValue(0xFF);

        ports.mapRead("exact", IoSelector.exact(0x20), access -> 0x44);

        assertEquals(0x44, ports.read(0x20));
        assertEquals(0xFF, ports.read(0x21));
    }

    @Test
    void usesDefaultReadAndDropsUnmappedWrites() {
        IoAddressSpace ports = IoAddressSpace.withUnmappedValue(0xA5);

        ports.write(0x1234, 0x55);

        assertEquals(0xA5, ports.read(0x1234));
    }

    @Test
    void rejectsAmbiguousReadMappingsAtTheSamePriority() {
        IoAddressSpace ports = IoAddressSpace.withUnmappedValue(0xFF);
        ports.mapRead("first", IoSelector.range(0x10, 0x1F), access -> 0x11);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ports.mapRead("second", IoSelector.range(0x18, 0x20), access -> 0x22)
        );

        assertTrue(error.getMessage().contains("overlaps"));
    }

    @Test
    void readOnlyAndWriteOnlyMappingsMayShareAnAddressAtTheSamePriority() {
        IoAddressSpace ports = IoAddressSpace.withUnmappedValue(0xFF);
        int[] value = {0};

        ports.mapRead("read", IoSelector.exact(0x10), access -> value[0]);
        ports.mapWrite("write", IoSelector.exact(0x10), (access, written) -> value[0] = written);

        ports.write(0x10, 0x42);

        assertEquals(0x42, ports.read(0x10));
    }

    @Test
    void higherPriorityMappingWinsIntentionalOverlaps() {
        IoAddressSpace ports = IoAddressSpace.withUnmappedValue(0xFF);

        ports.mapRead("background", IoSelector.mask(0x0000, 0x0000), access -> 0x11, 0);
        ports.mapRead("foreground", IoSelector.exact(0x20), access -> 0x22, 1);

        assertEquals(0x11, ports.read(0x10));
        assertEquals(0x22, ports.read(0x20));
    }

    @Test
    void tracesMappedAndUnmappedAccessesAfterRouting() {
        IoAddressSpace ports = IoAddressSpace.withUnmappedValue(0xFF);
        List<String> events = new ArrayList<>();
        ports.setTraceSink((mappingName, read, access, value) -> events.add(
                "%s %s %04X %04X %02X".formatted(
                        mappingName,
                        read ? "R" : "W",
                        access.address(),
                        access.offset(),
                        value
                )
        ));
        ports.mapReadWrite(
                "registers",
                IoSelector.range(0x10, 0x11),
                access -> 0x20 + access.offset(),
                (access, value) -> {
                }
        );

        assertEquals(0x21, ports.read(0x11));
        ports.write(0x10, 0xA5);
        assertEquals(0xFF, ports.read(0x12));
        ports.write(0x12, 0x7B);

        assertEquals(List.of(
                "registers R 0011 0001 21",
                "registers W 0010 0000 A5",
                "unmapped R 0012 0012 FF",
                "unmapped W 0012 0012 7B"
        ), events);
    }
}
