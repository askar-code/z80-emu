package dev.z8emu.machine.apple2;

import dev.z8emu.machine.apple2.device.Apple2KeyboardDevice;
import dev.z8emu.machine.apple2.device.Apple2SpeakerDevice;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.bus.io.IoAddressSpace;
import dev.z8emu.platform.bus.io.IoSelector;
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
    private final Apple2LanguageCard languageCard;
    private final Apple2SlotBus slotBus;
    private final IoAddressSpace ioPorts;

    public Apple2Bus(
            TStateCounter clock,
            Apple2Memory memory,
            Apple2KeyboardDevice keyboard,
            Apple2SpeakerDevice speaker,
            Apple2SoftSwitches softSwitches,
            Apple2LanguageCard languageCard,
            Apple2SlotBus slotBus
    ) {
        super(clock);
        this.memory = Objects.requireNonNull(memory, "memory");
        this.keyboard = Objects.requireNonNull(keyboard, "keyboard");
        this.speaker = Objects.requireNonNull(speaker, "speaker");
        this.softSwitches = Objects.requireNonNull(softSwitches, "softSwitches");
        this.languageCard = Objects.requireNonNull(languageCard, "languageCard");
        this.slotBus = Objects.requireNonNull(slotBus, "slotBus");
        this.ioPorts = buildIoMap();
    }

    @Override
    public int readMemory(int address) {
        int normalized = address & 0xFFFF;
        if (isIoAddress(normalized)) {
            return readIo(normalized);
        }
        if (isSlotRomAddress(normalized)) {
            return slotBus.readCnxx(normalized);
        }
        if (languageCard.handlesHighMemory(normalized) && languageCard.readsRam()) {
            return languageCard.readHighMemory(normalized);
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
            slotBus.writeCnxx(normalized, value);
            return;
        }
        if (languageCard.handlesHighMemory(normalized) && languageCard.writesRam()) {
            languageCard.writeHighMemory(normalized, value);
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
        return ioPorts.read(address, clockValue(), 0);
    }

    private void writeIo(int address, int value) {
        ioPorts.write(address, value, clockValue(), 0);
    }

    private IoAddressSpace buildIoMap() {
        IoAddressSpace ioMap = IoAddressSpace.withUnmappedValue(0x00);
        ioMap.mapRead("apple2.keyboard-data", IoSelector.exact(KEYBOARD_DATA),
                access -> keyboard.readData()
        );
        ioMap.mapReadWrite("apple2.keyboard-strobe-clear", IoSelector.exact(KEYBOARD_STROBE_CLEAR),
                access -> {
                    keyboard.clearStrobe();
                    return 0x00;
                },
                (access, value) -> keyboard.clearStrobe()
        );
        ioMap.mapReadWrite("apple2.speaker-toggle", IoSelector.exact(SPEAKER_TOGGLE),
                access -> {
                    speaker.toggle();
                    return 0x00;
                },
                (access, value) -> speaker.toggle()
        );
        ioMap.mapReadWrite("apple2.video-soft-switches", IoSelector.range(0xC050, 0xC057),
                access -> softSwitches.read(access.address()),
                (access, value) -> softSwitches.write(access.address(), value)
        );
        ioMap.mapReadWrite("apple2.slots-c0x", IoSelector.range(0xC080, 0xC0FF),
                slotBus::readC0x,
                slotBus::writeC0x
        );
        return ioMap;
    }
}
