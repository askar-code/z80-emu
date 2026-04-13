package dev.z8emu.machine.spectrum48k;

import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.machine.spectrum48k.tape.TapLoader;
import dev.z8emu.platform.video.FrameBuffer;
import java.io.ByteArrayInputStream;
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
        machine.board().ula().writePortFe(0x02, machine.board().beeper());

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
    void keyboardMatrixUsesActiveLowBits() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();

        machine.board().keyboard().setKeyPressed(1, 0, true);
        int read = machine.board().ula().readPortFe(0xFD_FE, machine.board().keyboard(), machine.board().tape());
        assertEquals(0xFE, read, "Pressed A key should clear bit 0 while idle tape EAR bit stays high");

        machine.board().keyboard().setKeyPressed(1, 0, false);
        assertEquals(0xFF, machine.board().ula().readPortFe(0xFD_FE, machine.board().keyboard(), machine.board().tape()));
    }

    @Test
    void frameBoundaryRaisesMaskableInterruptRequest() {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();

        assertEquals(false, machine.board().consumeMaskableInterrupt());
        machine.board().onTStatesElapsed(SpectrumUlaDevice.T_STATES_PER_FRAME, SpectrumUlaDevice.T_STATES_PER_FRAME);
        assertEquals(true, machine.board().consumeMaskableInterrupt());
        assertEquals(false, machine.board().consumeMaskableInterrupt());
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
        assertEquals(8063, tapeFile.blocks().get(0).pilotTonePulses());
        assertEquals(3223, tapeFile.blocks().get(1).pilotTonePulses());
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

        machine.board().onTStatesElapsed(2168, 2168);
        int read = machine.board().ula().readPortFe(0xFFFF, machine.board().keyboard(), machine.board().tape());

        assertEquals(0xBF, read, "Tape EAR input should pull bit 6 low during the active half of a pilot pulse");
    }

    @Test
    void fastLoadTrapLoadsCurrentTapBlockViaRomEntryPoint() throws Exception {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        byte[] tap = {
                0x04, 0x00,
                (byte) 0xFF, 0x12, 0x34, (byte) (0xFF ^ 0x12 ^ 0x34)
        };

        machine.board().tape().load(TapLoader.load(new ByteArrayInputStream(tap)));
        machine.cpu().registers().setPc(0x0556);
        machine.cpu().registers().setA(0xFF);
        machine.cpu().registers().setDe(0x0002);
        machine.cpu().registers().setIx(0x8000);
        machine.cpu().registers().setSp(0x9000);
        machine.cpu().registers().setFlag(dev.z8emu.cpu.z80.Z80Registers.FLAG_C, true);
        machine.board().memory().write(0x9000, 0x78);
        machine.board().memory().write(0x9001, 0x56);

        machine.runInstruction();

        assertEquals(0x12, machine.board().memory().read(0x8000));
        assertEquals(0x34, machine.board().memory().read(0x8001));
        assertEquals(0x5678, machine.cpu().registers().pc());
        assertEquals(0x9002, machine.cpu().registers().sp());
        assertEquals(true, machine.cpu().registers().flagSet(dev.z8emu.cpu.z80.Z80Registers.FLAG_C));
        assertEquals(true, machine.cpu().registers().iff1());
        assertEquals(true, machine.cpu().registers().iff2());
    }

    private int sampleLevel(int portFeValue) {
        Spectrum48kMachine machine = Spectrum48kMachine.withBlankRom();
        byte[] pcm = new byte[8];
        machine.board().beeper().writeFromPortFe(portFeValue);
        machine.board().beeper().onTStatesElapsed(3_500);
        machine.board().beeper().drainAudio(pcm, 0, pcm.length);
        return (pcm[1] << 8) | (pcm[0] & 0xFF);
    }
}
