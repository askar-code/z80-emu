package dev.z8emu.machine.radio86rk.device;

public final class Radio86DmaDevice {
    public static final int CHANNEL_COUNT = 4;
    public static final int CHANNEL_VIDEO = 2;

    private final ChannelState[] channels = {
            new ChannelState(),
            new ChannelState(),
            new ChannelState(),
            new ChannelState()
    };

    private int modeRegister;
    private int statusRegister;

    public void reset() {
        for (ChannelState channel : channels) {
            channel.reset();
        }
        modeRegister = 0;
        statusRegister = 0;
    }

    public int readRegister(int registerIndex) {
        int normalized = registerIndex & 0x0F;
        if (normalized == 0x08) {
            int value = statusRegister & 0x1F;
            statusRegister &= 0x10;
            return value;
        }
        return 0xFF;
    }

    public void writeRegister(int registerIndex, int value) {
        int normalized = registerIndex & 0x0F;
        int byteValue = value & 0xFF;
        if (normalized < 0x08) {
            int channelIndex = normalized >>> 1;
            if ((normalized & 0x01) == 0) {
                channels[channelIndex].writeAddressByte(byteValue);
            } else {
                channels[channelIndex].writeTerminalCountByte(byteValue);
            }
            return;
        }

        if (normalized == 0x08) {
            modeRegister = byteValue;
        }
    }

    public int channelBaseAddress(int channelIndex) {
        return channels[channelIndex].baseAddress();
    }

    public int channelTransferLength(int channelIndex) {
        return channels[channelIndex].transferLength();
    }

    public boolean channelEnabled(int channelIndex) {
        return ((modeRegister >>> channelIndex) & 0x01) != 0;
    }

    public int modeRegister() {
        return modeRegister & 0xFF;
    }

    private static final class ChannelState {
        private int baseAddress;
        private int terminalCount;
        private boolean nextAddressHighByte;
        private boolean nextTerminalCountHighByte;

        private void reset() {
            baseAddress = 0;
            terminalCount = 0;
            nextAddressHighByte = false;
            nextTerminalCountHighByte = false;
        }

        private void writeAddressByte(int value) {
            if (nextAddressHighByte) {
                baseAddress = (baseAddress & 0x00FF) | ((value & 0xFF) << 8);
            } else {
                baseAddress = (baseAddress & 0xFF00) | (value & 0xFF);
            }
            nextAddressHighByte = !nextAddressHighByte;
        }

        private void writeTerminalCountByte(int value) {
            if (nextTerminalCountHighByte) {
                terminalCount = (terminalCount & 0x00FF) | ((value & 0xFF) << 8);
            } else {
                terminalCount = (terminalCount & 0xFF00) | (value & 0xFF);
            }
            nextTerminalCountHighByte = !nextTerminalCountHighByte;
        }

        private int baseAddress() {
            return baseAddress & 0xFFFF;
        }

        private int transferLength() {
            return (terminalCount & 0x3FFF) + 1;
        }
    }
}
