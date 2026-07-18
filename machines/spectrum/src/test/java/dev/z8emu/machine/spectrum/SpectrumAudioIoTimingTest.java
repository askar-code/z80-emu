package dev.z8emu.machine.spectrum;

import dev.z8emu.machine.spectrum128k.Spectrum128Board;
import dev.z8emu.machine.spectrum48k.Spectrum48kBoard;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.audio.PcmMonoSource;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.time.TStateCounter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectrumAudioIoTimingTest {
    private static final int OUT_IO_PHASE_TSTATES = 7;
    private static final int OUT_INSTRUCTION_TSTATES = 11;

    @Test
    void ioReadAndWriteTimestampsUseTheDataPhaseAfterSequentialContentionWaits() {
        TStateCounter clock = new TStateCounter();
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );
        SpectrumBusBase bus = (SpectrumBusBase) board.cpuBus();
        List<TraceEvent> events = new ArrayList<>();
        bus.setIoTraceSink((mappingName, read, access, value) ->
                events.add(new TraceEvent(mappingName, read, access))
        );
        clock.advance(board.modelConfig().contentionStartTState());

        int phaseTStates = 2;
        int readWaits = bus.readPortWaitStates(0x40FE, phaseTStates);
        int writeWaits = bus.writePortWaitStates(0x40FE, 0x10, phaseTStates);
        bus.readPort(0x40FE, phaseTStates);
        bus.writePort(0x40FE, 0x10, phaseTStates);

        assertEquals(4, readWaits);
        assertEquals(4, writeWaits);
        assertEquals(2, events.size());
        assertEquals("spectrum.ula-fe", events.get(0).mappingName());
        assertTrue(events.get(0).read());
        assertEquals("unmapped", events.get(1).mappingName());
        assertFalse(events.get(1).read());
        assertAccessTimestamp(events.get(0).access(), clock.value(), phaseTStates, readWaits);
        assertAccessTimestamp(events.get(1).access(), clock.value(), phaseTStates, writeWaits);
    }

    @Test
    void beeperWritesInSeparateInstructionsKeepTheirEffectiveTStateEdges() {
        TStateCounter clock = new TStateCounter();
        Spectrum48kBoard board = new Spectrum48kBoard(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );

        // Two OUT (n),A instructions start at t=90 and t=190. Their I/O
        // cycles start at phase 7, mutate the port at t=100/t=200 and finish
        // one t-state later. The gaps stand in for intervening instructions.
        advance(board, clock, 90);
        board.cpuBus().writePort(0x00FE, 0x10, OUT_IO_PHASE_TSTATES);
        advance(board, clock, OUT_INSTRUCTION_TSTATES);
        advance(board, clock, 89);
        board.cpuBus().writePort(0x00FE, 0x00, OUT_IO_PHASE_TSTATES);
        advance(board, clock, OUT_INSTRUCTION_TSTATES);
        advance(board, clock, 99);

        short[] samples = drainSamples(board.beeper(), 3);
        assertEquals(0, samples[0], "audio before the first write must retain the reset level");
        assertTrue(samples[1] > 0, "the interval between the two writes must retain the high beeper level");
        assertTrue(samples[2] < 0, "the second write must create the falling AC-coupled edge");
    }

    @Test
    void ayWritesAcrossInstructionBoundariesKeepTheirEffectiveTStateEdges() {
        TStateCounter clock = new TStateCounter();
        Spectrum128Board board = new Spectrum128Board(
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                new byte[Spectrum48kMemoryMap.ROM_SIZE],
                clock
        );
        board.ay().selectRegister(7);
        board.ay().writeSelectedRegister(0x3F);
        board.ay().selectRegister(8);

        advance(board, clock, 90);
        board.cpuBus().writePort(0xBFFD, 0x0F, OUT_IO_PHASE_TSTATES);
        advance(board, clock, OUT_INSTRUCTION_TSTATES);
        advance(board, clock, 89);
        board.cpuBus().writePort(0xBFFD, 0x00, OUT_IO_PHASE_TSTATES);
        advance(board, clock, OUT_INSTRUCTION_TSTATES);
        advance(board, clock, 99);

        short[] samples = drainSamples(board.ay(), 3);
        assertEquals(0, samples[0], "audio before the first AY write must remain silent");
        assertTrue(samples[1] > 0, "the first write must become audible only after t=100");
        assertTrue(samples[2] < 0, "the second write must create the falling AC-coupled edge at t=200");
    }

    private static void assertAccessTimestamp(
            IoAccess access,
            long instructionStartTState,
            int phaseTStates,
            int waitStates
    ) {
        assertEquals(instructionStartTState + 3 + waitStates, access.tState());
        assertEquals(phaseTStates, access.phaseTStates());
        assertEquals(
                instructionStartTState + phaseTStates + 3 + waitStates,
                access.effectiveTState()
        );
    }

    private static void advance(SpectrumBoard board, TStateCounter clock, int tStates) {
        clock.advance(tStates);
        board.onTStatesElapsed(tStates, clock.value());
    }

    private static short[] drainSamples(PcmMonoSource source, int sampleCount) {
        byte[] pcm = new byte[sampleCount * Short.BYTES];
        assertEquals(pcm.length, source.drainAudio(pcm, 0, pcm.length));
        short[] samples = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int byteIndex = i * Short.BYTES;
            samples[i] = (short) ((pcm[byteIndex] & 0xFF) | (pcm[byteIndex + 1] << 8));
        }
        return samples;
    }

    private record TraceEvent(String mappingName, boolean read, IoAccess access) {
    }
}
