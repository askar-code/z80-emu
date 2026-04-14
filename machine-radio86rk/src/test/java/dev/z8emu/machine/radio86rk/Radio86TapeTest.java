package dev.z8emu.machine.radio86rk;

import dev.z8emu.machine.radio86rk.tape.Radio86RkTapeLoader;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Radio86TapeTest {
    @Test
    void rkLoaderPrependsHeaderAndSyncByte() throws Exception {
        byte[] payload = {(byte) 0xA5};
        var tapeFile = Radio86RkTapeLoader.load(new ByteArrayInputStream(payload));

        assertEquals((256 + 1 + 1) * 16, tapeFile.halfWaveCount());
        assertTrue(tapeFile.lowLevels()[0], "Header zero bit should start with low half-wave");
        assertFalse(tapeFile.lowLevels()[1], "Header zero bit should then flip high");

        int syncOffset = 256 * 16;
        assertFalse(tapeFile.lowLevels()[syncOffset], "Sync 0xE6 starts with a one bit");
        assertTrue(tapeFile.lowLevels()[syncOffset + 1]);
    }

    @Test
    void tapeDeviceFeedsKeyboardCassetteBitDuringPlayback() throws Exception {
        byte[] payload = {(byte) 0x00};
        var tapeFile = Radio86RkTapeLoader.load(new ByteArrayInputStream(payload));
        Radio86Machine machine = new Radio86Machine(new byte[0x800]);

        machine.board().tape().load(tapeFile);
        machine.board().tape().play();

        machine.board().onTStatesElapsed(1, 1);
        assertEquals(0xEF, machine.board().cpuBus().readMemory(0x8002));

        machine.board().onTStatesElapsed(808, 809);
        assertEquals(0xFF, machine.board().cpuBus().readMemory(0x8002));
    }
}
