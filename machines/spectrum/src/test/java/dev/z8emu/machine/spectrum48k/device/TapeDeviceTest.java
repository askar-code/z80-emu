package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.machine.spectrum.model.SpectrumModelConfig;
import dev.z8emu.machine.spectrum48k.tape.TapeBlock;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TapeDeviceTest {
    private static final long CPU_CLOCK_HZ = 3_500_000L;

    @Test
    void loadingAtNonZeroMachineTimeStartsTapeAtItsBeginning() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.syncToTState(1_000);

        tape.load(singlePulseTape(10));
        tape.play();
        tape.syncToTState(1_004);

        assertTrue(tape.isPlaying());

        tape.syncToTState(1_010);

        assertTrue(tape.isAtEnd());
    }

    @Test
    void playAfterEofRestartsFromCurrentMachineTime() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(singlePulseTape(10));
        tape.play();
        tape.syncToTState(10);
        assertTrue(tape.isAtEnd());

        tape.play();
        tape.syncToTState(14);

        assertTrue(tape.isPlaying());

        tape.syncToTState(20);

        assertTrue(tape.isAtEnd());
    }

    @Test
    void rewindAtNonZeroMachineTimeDoesNotFastForwardOnNextSync() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(singlePulseTape(10));
        tape.play();
        tape.syncToTState(10);
        tape.syncToTState(1_000);

        tape.rewind();
        assertFalse(tape.isPlaying());
        tape.play();
        tape.syncToTState(1_004);

        assertTrue(tape.isPlaying());

        tape.syncToTState(1_010);

        assertTrue(tape.isAtEnd());
    }

    @Test
    void machineResetStillResetsTapeClockToZero() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(singlePulseTape(10));
        tape.syncToTState(1_000);

        tape.reset();
        tape.play();
        tape.syncToTState(4);

        assertTrue(tape.isPlaying());
    }

    @Test
    void scalesReferencePulseTimingsTo128kClockWithoutCumulativeDrift() {
        int[] oneTickPulses = new int[100];
        Arrays.fill(oneTickPulses, 1);
        TapeDevice tape = new TapeDevice(SpectrumModelConfig.spectrum128().cpuClockHz(), false);
        tape.load(new TapeFile(List.of(
                TapeBlock.dataBlock(oneTickPulses, 0, 0, 0, 0, new byte[0])
        )));

        tape.play();
        tape.syncToTState(100);

        assertTrue(tape.isPlaying());

        tape.syncToTState(101);

        assertTrue(tape.isAtEnd());
    }

    @Test
    void pauseKeepsTerminatingLevelForOneMillisecondThenSettlesLow() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(new TapeFile(List.of(
                TapeBlock.dataBlock(new int[]{4, 4}, 0, 0, 0, 2, new byte[0])
        )));

        tape.play();
        tape.syncToTState(8);

        assertTrue(tape.earHigh());

        tape.syncToTState(3_507);

        assertTrue(tape.earHigh());

        tape.syncToTState(3_508);

        assertFalse(tape.earHigh());
        assertTrue(tape.isPlaying());

        tape.syncToTState(7_008);

        assertTrue(tape.isAtEnd());
    }

    @Test
    void setSignalLevelBlockForcesLevelBeforeFollowingPause() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(new TapeFile(List.of(
                TapeBlock.dataBlock(new int[]{4}, 0, 0, 0, 0, new byte[0]),
                TapeBlock.signalLevelBlock(true),
                TapeBlock.pauseBlock(2, false)
        )));

        tape.play();
        tape.syncToTState(4);

        assertTrue(tape.earHigh());

        tape.syncToTState(3_504);

        assertFalse(tape.earHigh());
    }

    @Test
    void directRecordingHoldsExplicitSampleRunsAndHonorsUsedBits() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(new TapeFile(List.of(
                TapeBlock.directRecordingBlock(2, 5, 0, new byte[]{(byte) 0xB0})
        )));

        tape.play();
        assertTrue(tape.earHigh());

        tape.syncToTState(2);
        assertFalse(tape.earHigh());

        tape.syncToTState(4);
        assertTrue(tape.earHigh());
        tape.syncToTState(7);
        assertTrue(tape.earHigh());

        tape.syncToTState(8);
        assertFalse(tape.earHigh());
        tape.syncToTState(9);
        assertTrue(tape.isPlaying());

        tape.syncToTState(10);
        assertTrue(tape.isAtEnd());
    }

    @Test
    void directRecordingOverridesPreviousLevelFromItsFirstSample() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(new TapeFile(List.of(
                TapeBlock.signalLevelBlock(true),
                TapeBlock.directRecordingBlock(4, 1, 0, new byte[]{0})
        )));

        tape.play();
        assertTrue(tape.earHigh());

        tape.syncToTState(1);
        assertFalse(tape.earHigh());
    }

    @Test
    void directRecordingPauseStartsOppositeItsLastSampleThenSettlesLow() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(new TapeFile(List.of(
                TapeBlock.directRecordingBlock(2, 1, 2, new byte[]{0})
        )));

        tape.play();
        assertFalse(tape.earHigh());

        tape.syncToTState(2);
        assertTrue(tape.earHigh());
        tape.syncToTState(3_501);
        assertTrue(tape.earHigh());

        tape.syncToTState(3_502);
        assertFalse(tape.earHigh());
    }

    @Test
    void directRecordingUsesTheModelClockWithoutFractionalDrift() {
        byte[] alternatingSamples = new byte[13];
        Arrays.fill(alternatingSamples, (byte) 0xAA);
        TapeDevice tape = new TapeDevice(SpectrumModelConfig.spectrum128().cpuClockHz(), false);
        tape.load(new TapeFile(List.of(
                TapeBlock.directRecordingBlock(1, 4, 0, alternatingSamples)
        )));

        tape.play();
        tape.syncToTState(100);
        assertTrue(tape.isPlaying());

        tape.syncToTState(101);
        assertTrue(tape.isAtEnd());
    }

    @Test
    void cswUsesCurrentLevelAndKeepsTheLastPulseLevel() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(new TapeFile(List.of(
                TapeBlock.cswRecordingBlock(875_000, new int[]{1, 2}, 0),
                TapeBlock.cswRecordingBlock(875_000, new int[]{1}, 0)
        )));

        tape.play();
        assertFalse(tape.earHigh());

        tape.syncToTState(4);
        assertTrue(tape.earHigh());
        tape.syncToTState(11);
        assertTrue(tape.earHigh());

        tape.syncToTState(12);
        assertTrue(tape.earHigh());
    }

    @Test
    void standalonePauseAfterCswFinishesTheLastPulseBeforeSettlingLow() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.load(new TapeFile(List.of(
                TapeBlock.cswRecordingBlock(875_000, new int[]{1}, 0),
                TapeBlock.pauseBlock(2, false)
        )));

        tape.play();
        assertFalse(tape.earHigh());

        tape.syncToTState(4);
        assertTrue(tape.earHigh());
        tape.syncToTState(3_503);
        assertTrue(tape.earHigh());

        tape.syncToTState(3_504);
        assertFalse(tape.earHigh());
    }

    @Test
    void cswSamplingRateConvertsDirectlyToThe128kModelClock() {
        int[] oneSamplePulses = new int[100];
        Arrays.fill(oneSamplePulses, 1);
        TapeDevice tape = new TapeDevice(SpectrumModelConfig.spectrum128().cpuClockHz(), false);
        tape.load(new TapeFile(List.of(
                TapeBlock.cswRecordingBlock(3_500_000, oneSamplePulses, 0)
        )));

        tape.play();
        tape.syncToTState(100);
        assertTrue(tape.isPlaying());

        tape.syncToTState(101);
        assertTrue(tape.isAtEnd());
    }

    @Test
    void selectBlockStopsAtTheRequestedBoundaryWithoutResettingMachineTime() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.syncToTState(1_000);
        tape.load(new TapeFile(List.of(
                TapeBlock.dataBlock(new int[]{10}, 0, 0, 0, 0, new byte[0]),
                TapeBlock.dataBlock(new int[]{10}, 0, 0, 0, 0, new byte[0])
        )));
        tape.play();
        tape.syncToTState(1_004);

        tape.selectBlock(1);

        assertFalse(tape.isPlaying());
        assertEquals(1, tape.blockPosition());
        assertEquals(2, tape.currentBlockIndex());
        tape.play();
        tape.syncToTState(1_013);
        assertTrue(tape.isPlaying());
        tape.syncToTState(1_014);
        assertTrue(tape.isAtEnd());
    }

    @Test
    void selectBlockRejectsMissingTapeAndOutOfRangePositions() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);

        assertThrows(IllegalStateException.class, () -> tape.selectBlock(0));

        tape.load(singlePulseTape(10));
        assertThrows(IllegalArgumentException.class, () -> tape.selectBlock(-1));
        assertThrows(IllegalArgumentException.class, () -> tape.selectBlock(1));
    }

    @Test
    void ejectAtNonZeroTimeStopsPlaybackAndMakesEarInactive() {
        TapeDevice tape = new TapeDevice(CPU_CLOCK_HZ, true);
        tape.syncToTState(1_000);
        tape.load(new TapeFile(List.of(
                TapeBlock.signalLevelBlock(true),
                TapeBlock.dataBlock(new int[]{10}, 0, 0, 0, 0, new byte[0])
        )));
        tape.play();

        tape.eject();

        assertFalse(tape.isLoaded());
        assertFalse(tape.isPlaying());
        assertFalse(tape.earHigh());
        assertFalse(tape.transportStatus().loaded());
        assertEquals(0, tape.totalBlocks());
        assertEquals(0, tape.blockPosition());

        tape.load(singlePulseTape(10));
        tape.play();
        tape.syncToTState(1_004);
        assertTrue(tape.isPlaying(), "eject/reinsert must preserve the absolute tape clock");
        tape.syncToTState(1_010);
        assertTrue(tape.isAtEnd());
    }

    private static TapeFile singlePulseTape(int pulseLengthTStates) {
        return new TapeFile(List.of(
                TapeBlock.dataBlock(new int[]{pulseLengthTStates}, 0, 0, 0, 0, new byte[0])
        ));
    }
}
