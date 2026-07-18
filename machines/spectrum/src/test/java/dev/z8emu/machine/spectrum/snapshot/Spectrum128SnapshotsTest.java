package dev.z8emu.machine.spectrum.snapshot;

import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Spectrum128SnapshotsTest {
    @Test
    void restoreAndCapturePreserveAllBanksCpuPagingBorderAndAy() {
        Spectrum128Machine machine = blankMachine();
        Spectrum128Snapshot expected = sampleSnapshot(false);

        Spectrum128Snapshots.restore(machine, expected);
        Spectrum128Snapshot actual = Spectrum128Snapshots.capture(machine);

        assertEquals(0, machine.currentTState());
        assertSnapshotEquals(expected, actual);
        assertEquals(firstByte(5), machine.board().memory().read(0x4000));
        assertEquals(firstByte(2), machine.board().memory().read(0x8000));
        assertEquals(firstByte(7), machine.board().memory().read(0xC000));
        assertEquals(7, machine.board().machineState().topRamBankIndex());
        assertEquals(7, machine.board().machineState().activeScreenBankIndex());
        assertEquals(1, machine.board().machineState().selectedRomIndex());
        assertTrue(machine.board().machineState().pagingLocked());
    }

    @Test
    void snaAndZ80SaveDoNotMutateLiveMachine() throws Exception {
        Spectrum128Machine machine = blankMachine();
        Spectrum128Snapshots.restore(machine, sampleSnapshot(true));
        Spectrum128Snapshot before = Spectrum128Snapshots.capture(machine);

        byte[] sna = Spectrum128Snapshots.saveSna(machine);
        byte[] z80 = Spectrum128Snapshots.saveZ80(
                machine,
                Z80V2V3SnapshotCodec.Compression.COMPRESSED
        );

        assertSnapshotEquals(before, Spectrum128Snapshots.capture(machine));
        assertEquals(Sna128SnapshotCodec.IMAGE_SIZE, sna.length);
        assertEquals(4, Byte.toUnsignedInt(z80[34]));
    }

    @Test
    void z80LoadRestoresAllMachineVisibleAndPhysicalState() throws Exception {
        Spectrum128Snapshot expected = sampleSnapshot(false);
        byte[] image = Z80V2V3SnapshotCodec.encode(
                expected,
                Z80V2V3SnapshotCodec.Compression.COMPRESSED
        );
        Spectrum128Machine machine = blankMachine();
        machine.runInstruction();

        Spectrum128Snapshots.loadZ80(machine, image);

        assertEquals(0, machine.currentTState());
        assertSnapshotEquals(expected, Spectrum128Snapshots.capture(machine));
    }

    @Test
    void snaLoadUsesRealPcFieldAndResetsAyStateAbsentFromFormat() throws Exception {
        Spectrum128Snapshot source = sampleSnapshot(true);
        byte[] image = Sna128SnapshotCodec.encode(source);
        Spectrum128Machine machine = blankMachine();

        Spectrum128Snapshots.loadSna(machine, image);
        Spectrum128Snapshot actual = Spectrum128Snapshots.capture(machine);

        assertEquals(source.cpu(), actual.cpu());
        assertEquals(source.pagingPort7ffd(), actual.pagingPort7ffd());
        assertArrayEquals(new byte[16], actual.ayRegisters());
        assertEquals(0, actual.selectedAyRegister());
        for (int bank = 0; bank < 8; bank++) {
            assertArrayEquals(source.ramBank(bank), actual.ramBank(bank));
        }
    }

    @Test
    void malformedAndWrongModelLoadsLeaveRunningMachineUntouched() throws Exception {
        Spectrum128Machine machine = blankMachine();
        Spectrum128Snapshots.restore(machine, sampleSnapshot(false));
        machine.runInstruction();
        long beforeTState = machine.currentTState();
        Spectrum128Snapshot before = Spectrum128Snapshots.capture(machine);

        byte[] malformed = Z80V2V3SnapshotCodec.encode(
                sampleSnapshot(false),
                Z80V2V3SnapshotCodec.Compression.UNCOMPRESSED
        );
        malformed = Arrays.copyOf(malformed, malformed.length - 1);
        byte[] finalMalformed = malformed;
        assertThrows(
                SpectrumSnapshotException.class,
                () -> Spectrum128Snapshots.loadZ80(machine, finalMalformed)
        );
        assertEquals(beforeTState, machine.currentTState());
        assertSnapshotEquals(before, Spectrum128Snapshots.capture(machine));

        Spectrum48Snapshot snapshot48 = new Spectrum48Snapshot(
                before.cpu(),
                before.borderColor(),
                new byte[Spectrum48Snapshot.RAM_SIZE]
        );
        byte[] image48 = Z80V2V3SnapshotCodec.encode(
                snapshot48,
                Z80V2V3SnapshotCodec.Compression.UNCOMPRESSED
        );
        assertThrows(
                SpectrumSnapshotException.class,
                () -> Spectrum128Snapshots.loadZ80(machine, image48)
        );
        assertEquals(beforeTState, machine.currentTState());
        assertSnapshotEquals(before, Spectrum128Snapshots.capture(machine));
    }

    private static Spectrum128Machine blankMachine() {
        return new Spectrum128Machine(new byte[Spectrum128Machine.ROM_IMAGE_SIZE]);
    }

    private static Spectrum128Snapshot sampleSnapshot(boolean ordinaryPage) {
        byte[][] banks = new byte[8][];
        for (int bank = 0; bank < banks.length; bank++) {
            banks[bank] = new byte[Spectrum128Snapshot.RAM_BANK_SIZE];
            for (int offset = 0; offset < banks[bank].length; offset++) {
                banks[bank][offset] = (byte) (bank * 23 + offset * 7 + (offset >>> 9));
            }
        }
        byte[] ay = {
                0x11, 0x02, 0x33, 0x04, 0x55, 0x06, 0x17, (byte) 0xB8,
                0x09, 0x0A, 0x0B, 0x1C, 0x2D, 0x0E, (byte) 0xEF, 0x70
        };
        return new Spectrum128Snapshot(
                new Z80SnapshotState(
                        0xABCD, 0xDDEE, 0xBBCC, 0x99AA,
                        0x7788, 0x5566, 0x3344, 0x1122,
                        0x2468, 0x1357, 0x9002, 0x4567,
                        0x3C, 0x91, true, true, 2
                ),
                6,
                ordinaryPage ? 0x1B : 0x3F,
                11,
                ay,
                banks
        );
    }

    private static int firstByte(int bank) {
        return bank * 23;
    }

    private static void assertSnapshotEquals(Spectrum128Snapshot expected, Spectrum128Snapshot actual) {
        assertEquals(expected.cpu(), actual.cpu());
        assertEquals(expected.borderColor(), actual.borderColor());
        assertEquals(expected.pagingPort7ffd(), actual.pagingPort7ffd());
        assertEquals(expected.selectedAyRegister(), actual.selectedAyRegister());
        assertArrayEquals(expected.ayRegisters(), actual.ayRegisters());
        for (int bank = 0; bank < 8; bank++) {
            assertArrayEquals(expected.ramBank(bank), actual.ramBank(bank), "physical bank " + bank);
        }
    }
}
