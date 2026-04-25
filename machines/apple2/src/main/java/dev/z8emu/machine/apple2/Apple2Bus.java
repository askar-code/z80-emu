package dev.z8emu.machine.apple2;

import dev.z8emu.machine.apple2.device.Apple2KeyboardDevice;
import dev.z8emu.machine.apple2.device.Apple2SpeakerDevice;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Apple2Bus extends ClockedCpuBus {
    private static final int KEYBOARD_DATA = 0xC000;
    private static final int KEYBOARD_STROBE_CLEAR = 0xC010;
    private static final int SPEAKER_TOGGLE = 0xC030;

    private final Apple2Memory memory;
    private final Apple2KeyboardDevice keyboard;
    private final Apple2SpeakerDevice speaker;
    private final Apple2SoftSwitches softSwitches;

    public Apple2Bus(
            TStateCounter clock,
            Apple2Memory memory,
            Apple2KeyboardDevice keyboard,
            Apple2SpeakerDevice speaker,
            Apple2SoftSwitches softSwitches
    ) {
        super(clock);
        this.memory = Objects.requireNonNull(memory, "memory");
        this.keyboard = Objects.requireNonNull(keyboard, "keyboard");
        this.speaker = Objects.requireNonNull(speaker, "speaker");
        this.softSwitches = Objects.requireNonNull(softSwitches, "softSwitches");
    }

    @Override
    public int readMemory(int address) {
        int normalized = address & 0xFFFF;
        if (isIoAddress(normalized)) {
            return readIo(normalized);
        }
        if (isSlotRomAddress(normalized)) {
            return 0xFF;
        }
        return memory.read(normalized);
    }

    @Override
    public void writeMemory(int address, int value) {
        int normalized = address & 0xFFFF;
        if (isIoAddress(normalized)) {
            writeIo(normalized, value);
            return;
        }
        if (isSlotRomAddress(normalized)) {
            return;
        }
        memory.write(normalized, value);
    }

    private static boolean isIoAddress(int address) {
        return address >= Apple2Memory.IO_START && address < Apple2Memory.IO_END_EXCLUSIVE;
    }

    private static boolean isSlotRomAddress(int address) {
        return address >= Apple2Memory.SLOT_ROM_START && address < Apple2Memory.SLOT_ROM_END_EXCLUSIVE;
    }

    private int readIo(int address) {
        return switch (address) {
            case KEYBOARD_DATA -> keyboard.readData();
            case KEYBOARD_STROBE_CLEAR -> {
                keyboard.clearStrobe();
                yield 0x00;
            }
            case SPEAKER_TOGGLE -> {
                speaker.toggle();
                yield 0x00;
            }
            default -> softSwitches.read(address);
        };
    }

    private void writeIo(int address, int value) {
        switch (address) {
            case KEYBOARD_STROBE_CLEAR -> keyboard.clearStrobe();
            case SPEAKER_TOGGLE -> speaker.toggle();
            default -> softSwitches.write(address, value);
        }
    }
}
