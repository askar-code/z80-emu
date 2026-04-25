package dev.z8emu.machine.apple2.device;

public final class Apple2KeyboardDevice {
    private int keyData;
    private boolean strobe;

    public void reset() {
        keyData = 0;
        strobe = false;
    }

    public void pressKey(int ascii) {
        keyData = ascii & 0x7F;
        strobe = true;
    }

    public int readData() {
        return keyData | (strobe ? 0x80 : 0x00);
    }

    public void clearStrobe() {
        strobe = false;
    }

    public void releaseAllKeys() {
        strobe = false;
    }

    public boolean strobe() {
        return strobe;
    }
}
