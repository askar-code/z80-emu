package dev.z8emu.machine.spectrum.snapshot;

import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Spectrum48SnapshotsTest {
    @Test
    void snaSaveDoesNotWritePcIntoTheLiveMachineStack() throws Exception {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        Spectrum48Snapshot state = sampleSnapshot(false);
        Spectrum48Snapshots.restore(machine, state);
        Spectrum48Snapshot before = Spectrum48Snapshots.capture(machine);

        byte[] image = Spectrum48Snapshots.saveSna(machine);

        Spectrum48Snapshot after = Spectrum48Snapshots.capture(machine);
        assertSnapshotEquals(before, after);
        assertEquals(0xA5, machine.board().memory().read(0x9000));
        assertEquals(0x5A, machine.board().memory().read(0x9001));
        int imageStackOffset = Sna48SnapshotCodec.HEADER_SIZE + 0x5000;
        assertEquals(0x67, Byte.toUnsignedInt(image[imageStackOffset]));
        assertEquals(0x45, Byte.toUnsignedInt(image[imageStackOffset + 1]));
    }

    @Test
    void z80LoadRestoresCpuRamAndBorderAndStartsAtTStateZero() throws Exception {
        Spectrum48Snapshot expected = sampleSnapshot(false);
        byte[] image = Z80V1SnapshotCodec.encode(expected, Z80V1SnapshotCodec.Compression.COMPRESSED);
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        machine.runInstruction();
        assertEquals(4, machine.currentTState());

        Spectrum48Snapshots.loadZ80(machine, image);

        assertEquals(0, machine.currentTState());
        assertSnapshotEquals(expected, Spectrum48Snapshots.capture(machine));
    }

    @Test
    void snaLoadAppliesStackPcSemanticsAndAllStoredState() throws Exception {
        Spectrum48Snapshot expected = sampleSnapshot(true);
        byte[] image = Sna48SnapshotCodec.encode(expected);
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();

        Spectrum48Snapshots.loadSna(machine, image);

        assertSnapshotEquals(expected, Spectrum48Snapshots.capture(machine));
    }

    @Test
    void v3Z80LoadUsesExtendedPcAndPageMap() throws Exception {
        Spectrum48Snapshot expected = sampleSnapshot(false);
        byte[] image = Z80V2V3SnapshotCodec.encode(
                expected,
                Z80V2V3SnapshotCodec.Compression.COMPRESSED
        );
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();

        Spectrum48Snapshots.loadZ80(machine, image);

        assertSnapshotEquals(expected, Spectrum48Snapshots.capture(machine));
    }

    @Test
    void malformedLoadLeavesMachineUntouched() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        Spectrum48Snapshot expected = sampleSnapshot(false);
        Spectrum48Snapshots.restore(machine, expected);
        Spectrum48Snapshot before = Spectrum48Snapshots.capture(machine);

        byte[] malformedPayload = new byte[31];
        malformedPayload[6] = 1;
        assertThrows(
                SpectrumSnapshotException.class,
                () -> Spectrum48Snapshots.loadZ80(machine, malformedPayload)
        );

        assertEquals(0, machine.currentTState());
        assertSnapshotEquals(before, Spectrum48Snapshots.capture(machine));
    }

    private static Spectrum48Snapshot sampleSnapshot(boolean pcAlreadyOnStack) {
        byte[] ram = new byte[Spectrum48Snapshot.RAM_SIZE];
        for (int offset = 0; offset < ram.length; offset++) {
            ram[offset] = (byte) ((offset * 31 + (offset >>> 7)) & 0xFF);
        }
        Arrays.fill(ram, 0x2000, 0x2100, (byte) 0x42);
        ram[0x5000] = pcAlreadyOnStack ? 0x67 : (byte) 0xA5;
        ram[0x5001] = pcAlreadyOnStack ? 0x45 : (byte) 0x5A;
        return new Spectrum48Snapshot(
                new Z80SnapshotState(
                        0xABCD,
                        0xDDEE,
                        0xBBCC,
                        0x99AA,
                        0x7788,
                        0x5566,
                        0x3344,
                        0x1122,
                        0x2468,
                        0x1357,
                        0x9002,
                        0x4567,
                        0x3C,
                        0x91,
                        !pcAlreadyOnStack,
                        false,
                        2
                ),
                5,
                ram
        );
    }

    private static void assertSnapshotEquals(Spectrum48Snapshot expected, Spectrum48Snapshot actual) {
        assertEquals(expected.cpu(), actual.cpu());
        assertEquals(expected.borderColor(), actual.borderColor());
        assertArrayEquals(expected.ram(), actual.ram());
    }
}
