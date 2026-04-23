package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.machine.spectrum48k.tape.TapeBlock;
import dev.z8emu.machine.spectrum48k.tape.TapeFile;
import dev.z8emu.machine.spectrum48k.tape.TapLoader;
import dev.z8emu.platform.video.FrameBuffer;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Spectrum48kMachineTest {
    @Test
    void outToPortFeUpdatesBorderAndBeeper() {
        byte[] rom = new byte[Spectrum48kMemoryMap.ROM_SIZE];
        rom[0] = (byte) 0x3E;
        rom[1] = 0x07;
        rom[2] = (byte) 0xD3;
        rom[3] = (byte) 0xFE;
        rom[4] = 0x76;

        Spectrum48kMachine machine = new Spectrum48kMachine(rom);

        assertEquals(7, machine.runInstruction());
        assertEquals(11, machine.runInstruction());

        assertEquals(7, machine.board().ula().borderColor());
        assertTrue(machine.board().beeper().micHigh());
    }

    @Test
    void rendersPixelsAttributesAndBorderIntoFrameBuffer() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        machine.board().ula().writePortFe(0x02, 0, machine.board().beeper());

        machine.board().memory().write(0x4000, 0x80);
        machine.board().memory().write(0x5800, 0x47);

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(
                0xFFCD0000,
                frame.pixels()[0],
                "Border color should fill non-display area"
        );

        int displayX = SpectrumUlaDevice.BORDER_LEFT;
        int displayY = SpectrumUlaDevice.BORDER_TOP;
        int pixelIndex = (displayY * frame.width()) + displayX;
        int nextPixelIndex = (displayY * frame.width()) + displayX + 1;

        assertEquals(0xFFFFFFFF, frame.pixels()[pixelIndex], "Left-most set pixel should use bright ink color");
        assertEquals(0xFF000000, frame.pixels()[nextPixelIndex], "Unset pixel should use paper color");
    }

    @Test
    void completedFrameUsesDisplayBytesCapturedWhenUlaFetchedThem() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();

        machine.board().memory().write(0x4000, 0x80);
        machine.board().memory().write(0x5800, 0x47);

        machine.board().onTStatesElapsed(15_000, 15_000);
        machine.board().memory().write(0x4000, 0x00);
        machine.board().onTStatesElapsed(
                SpectrumUlaDevice.T_STATES_PER_FRAME - 15_000,
                SpectrumUlaDevice.T_STATES_PER_FRAME
        );

        FrameBuffer frame = machine.board().renderVideoFrame();
        int displayX = SpectrumUlaDevice.BORDER_LEFT;
        int displayY = SpectrumUlaDevice.BORDER_TOP;
        int pixelIndex = (displayY * frame.width()) + displayX;

        assertEquals(0xFFFFFFFF, frame.pixels()[pixelIndex]);
    }

    @Test
    void borderColorHistoryProducesHorizontalBandsAcrossFrame() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        machine.board().ula().writePortFe(0x02, 0, machine.board().beeper());
        machine.board().ula().onTStatesElapsed(SpectrumUlaDevice.T_STATES_PER_FRAME / 2);
        machine.board().ula().writePortFe(0x04, SpectrumUlaDevice.T_STATES_PER_FRAME / 2, machine.board().beeper());
        machine.board().ula().onTStatesElapsed(SpectrumUlaDevice.T_STATES_PER_FRAME / 2);

        FrameBuffer frame = machine.board().renderVideoFrame();

        assertEquals(0xFFCD0000, frame.pixels()[0], "Top border should keep the first border color");
        int bottomRowIndex = (frame.height() - 1) * frame.width();
        assertEquals(0xFF00CD00, frame.pixels()[bottomRowIndex], "Bottom border should reflect the later border color");
    }

    @Test
    void keyboardMatrixUsesActiveLowBits() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();

        machine.board().keyboard().setKeyPressed(1, 0, true);
        int read = machine.board().ula().readPortFe(0xFD_FE, machine.board().keyboard(), machine.board().tape());
        assertEquals(0xFE, read, "Pressed A key should clear bit 0 while idle tape EAR bit stays high");

        machine.board().keyboard().setKeyPressed(1, 0, false);
        assertEquals(0xFF, machine.board().ula().readPortFe(0xFD_FE, machine.board().keyboard(), machine.board().tape()));
    }

    @Test
    void portFeReadUsesIssue3StyleDefaultBitsFromLastOutput() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();

        machine.board().ula().writePortFe(0x00, 0, machine.board().beeper());
        assertEquals(0xBF, machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape()));

        machine.board().ula().writePortFe(0x10, 0, machine.board().beeper());
        assertEquals(0xFF, machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape()));
    }

    @Test
    void ulaMaskableInterruptLineIsAShortFrameStartPulse() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();

        assertEquals(true, machine.board().maskableInterruptLineActive(SpectrumUlaDevice.T_STATES_PER_FRAME));
        assertEquals(true, machine.board().maskableInterruptLineActive(SpectrumUlaDevice.T_STATES_PER_FRAME + 31));
        assertEquals(false, machine.board().maskableInterruptLineActive(SpectrumUlaDevice.T_STATES_PER_FRAME + 32));
    }

    @Test
    void beeperProducesAudioSamplesWhenLevelChangesOverTime() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        byte[] pcm = new byte[256];

        machine.board().beeper().writeFromPortFe(0x10);
        machine.board().beeper().onTStatesElapsed(3_500);

        int copied = machine.board().beeper().drainAudio(pcm, 0, pcm.length);

        assertEquals(true, copied > 0);
    }

    @Test
    void beeperDistinguishesEarAndMicOutputLevels() {
        int highBoth = sampleLevel(0x18);
        int highEarOnly = sampleLevel(0x10);
        int lowMicOnly = sampleLevel(0x08);

        assertEquals(true, highBoth > highEarOnly);
        assertEquals(true, highEarOnly > lowMicOnly);
    }

    @Test
    void tapLoaderBuildsStandardBlocks() throws Exception {
        byte[] tap = {
                0x03, 0x00,
                0x00, 0x11, 0x22,
                0x02, 0x00,
                (byte) 0xFF, 0x33
        };

        var tapeFile = TapLoader.load(new ByteArrayInputStream(tap));

        assertEquals(2, tapeFile.blocks().size());
        assertEquals(8065, tapeFile.blocks().get(0).prefixPulseLengthsTStates().length);
        assertEquals(3225, tapeFile.blocks().get(1).prefixPulseLengthsTStates().length);
        assertEquals(2168, tapeFile.blocks().get(0).prefixPulseLengthsTStates()[0]);
        assertEquals(667, tapeFile.blocks().get(0).prefixPulseLengthsTStates()[8063]);
        assertEquals(735, tapeFile.blocks().get(0).prefixPulseLengthsTStates()[8064]);
        assertEquals(3, tapeFile.blocks().get(0).data().length);
    }

    @Test
    void tapeRaisesEarBitOnPortFeWhenPlaying() throws Exception {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        byte[] tap = {
                0x03, 0x00,
                0x00, 0x00, 0x00
        };

        machine.board().tape().load(TapLoader.load(new ByteArrayInputStream(tap)));
        machine.board().tape().play();
        machine.board().ula().writePortFe(0x00, 0, machine.board().beeper());

        machine.board().onTStatesElapsed(1000, 1000);
        int read = machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape());

        assertEquals(0xFF, read, "Tape EAR input should XOR bit 6 against the current port FE default value");
    }

    @Test
    void loadedButStoppedTapeDoesNotDriveEarBit() throws Exception {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        byte[] tap = {
                0x03, 0x00,
                0x00, 0x00, 0x00
        };

        machine.board().tape().load(TapLoader.load(new ByteArrayInputStream(tap)));

        int read = machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape());

        assertEquals(0xFF, read);
    }

    @Test
    void stopTapeBlockPausesPlaybackBeforeFollowingBlock() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        machine.board().tape().load(new TapeFile(List.of(
                TapeBlock.pauseBlock(0, true),
                TapeBlock.pauseBlock(100, false)
        )));

        machine.board().tape().play();
        machine.board().onTStatesElapsed(1, 1);

        assertEquals(false, machine.board().tape().isPlaying());
        assertEquals(2, machine.board().tape().currentBlockIndex());
        assertEquals(false, machine.board().tape().isAtEnd());
    }

    @Test
    void consecutivePulseBlocksPreserveEarLevelAcrossBoundary() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        machine.board().tape().load(new TapeFile(List.of(
                TapeBlock.dataBlock(new int[]{5}, 0, 0, 0, 0, new byte[0]),
                TapeBlock.dataBlock(new int[]{5}, 0, 0, 0, 0, new byte[0])
        )));

        machine.board().tape().play();
        machine.board().onTStatesElapsed(5, 5);

        assertEquals(0xBF, machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape()));
    }

    @Test
    void standardHeaderPauseKeepsCurrentLevelForPauseLeadIn() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        machine.board().tape().load(new TapeFile(List.of(
                TapeBlock.dataBlock(standardHeaderPulses(), 855, 1_710, 8, 100, new byte[19])
        )));

        machine.board().tape().play();
        machine.board().onTStatesElapsed(18_000_000, 18_000_000);

        int pauseLeadIn = machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape());
        machine.board().onTStatesElapsed(3_500, 3_500);
        int pauseSettled = machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape());

        assertEquals(pauseLeadIn, pauseSettled);
    }

    @Test
    void standardDataPauseDoesNotInsertCustomLeadInEdge() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        byte[] data = new byte[64];
        data[0] = (byte) 0xFF;
        machine.board().tape().load(new TapeFile(List.of(
                TapeBlock.dataBlock(standardDataPulses(), 855, 1_710, 8, 100, data)
        )));

        machine.board().tape().play();
        machine.board().onTStatesElapsed(18_000_000, 18_000_000);

        int pauseLeadIn = machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape());
        machine.board().onTStatesElapsed(3_500, 3_500);
        int pauseSettled = machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape());

        assertEquals(pauseLeadIn, pauseSettled);
    }

    @Test
    void pauseBlockKeepsLowLevelWhenThereWasNoPriorPulse() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        machine.board().tape().load(new TapeFile(List.of(
                TapeBlock.pauseBlock(2, false)
        )));

        machine.board().tape().play();

        assertEquals(0xFF, machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape()));

        machine.board().onTStatesElapsed(3_500, 3_500);

        assertEquals(0xFF, machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape()));
    }

    @Test
    void customTimedDataPauseKeepsCurrentLevelUntilBlockBoundary() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        machine.board().tape().load(new TapeFile(List.of(
                TapeBlock.dataBlock(new int[0], 4, 8, 1, 100, new byte[]{(byte) 0x80})
        )));

        machine.board().tape().play();
        machine.board().onTStatesElapsed(16, 16);

        int pauseLeadIn = machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape());

        machine.board().onTStatesElapsed(3_500, 3_500);

        int pauseSettled = machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape());

        assertEquals(pauseLeadIn, pauseSettled);
    }

    @Test
    void explicitPlayStartsTapeWithoutEarSamplingBurst() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        machine.board().tape().load(new TapeFile(List.of(
                TapeBlock.dataBlock(new int[]{5, 5, 5}, 5, 5, 1, 0, new byte[]{(byte) 0x80})
        )));

        machine.board().tape().play();

        assertEquals(true, machine.board().tape().isPlaying());
    }

    @Test
    void explicitPlayDoesNotDependOnKeyboardReleaseState() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        machine.board().tape().load(new TapeFile(List.of(
                TapeBlock.dataBlock(new int[]{5, 5, 5}, 5, 5, 1, 0, new byte[]{(byte) 0x80})
        )));
        machine.board().keyboard().setKeyPressed(6, 0, true);

        machine.board().tape().play();

        assertEquals(true, machine.board().tape().isPlaying());
    }

    private int sampleLevel(int portFeValue) {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        byte[] pcm = new byte[8];
        machine.board().beeper().writeFromPortFe(portFeValue);
        machine.board().beeper().onTStatesElapsed(3_500);
        machine.board().beeper().drainAudio(pcm, 0, pcm.length);
        return (pcm[1] << 8) | (pcm[0] & 0xFF);
    }

    private int[] standardHeaderPulses() {
        int[] pulses = new int[8_065];
        for (int i = 0; i < 8_063; i++) {
            pulses[i] = 2_168;
        }
        pulses[8_063] = 667;
        pulses[8_064] = 735;
        return pulses;
    }

    private int[] standardDataPulses() {
        int[] pulses = new int[3_225];
        for (int i = 0; i < 3_223; i++) {
            pulses[i] = 2_168;
        }
        pulses[3_223] = 667;
        pulses[3_224] = 735;
        return pulses;
    }
}
