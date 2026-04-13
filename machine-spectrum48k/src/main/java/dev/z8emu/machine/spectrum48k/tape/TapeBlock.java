package dev.z8emu.machine.spectrum48k.tape;

public record TapeBlock(
        int pilotPulseLengthTStates,
        int syncFirstPulseLengthTStates,
        int syncSecondPulseLengthTStates,
        int zeroBitPulseLengthTStates,
        int oneBitPulseLengthTStates,
        int pilotTonePulses,
        int pauseAfterMillis,
        byte[] data
) {
}
