package dev.z8emu.machine.apple2;

import dev.z8emu.machine.apple2.device.Apple2GamePortDevice;
import dev.z8emu.machine.apple2.device.Apple2KeyboardDevice;
import dev.z8emu.machine.apple2.device.Apple2SpeakerDevice;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.bus.io.IoAddressSpace;
import dev.z8emu.platform.bus.io.IoSelector;
import dev.z8emu.platform.bus.io.IoTraceSink;
import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public final class Apple2Bus extends ClockedCpuBus {
    private static final int KEYBOARD_DATA = 0xC000;
    private static final int KEYBOARD_STROBE_CLEAR = 0xC010;
    private static final int VBLANK_STATUS = 0xC019;
    private static final int SPEAKER_TOGGLE = 0xC030;
    private static final int PUSH_BUTTON_0 = 0xC061;
    private static final int PUSH_BUTTON_2 = 0xC063;
    private static final int PADDLE_0 = 0xC064;
    private static final int PADDLE_3 = 0xC067;
    private static final int PADDLE_TRIGGER = 0xC070;
    private static final int SLOT_3_ROM_START = 0xC300;
    private static final int SLOT_3_ROM_END_EXCLUSIVE = 0xC400;
    private static final int PERIPHERAL_ROM_END_EXCLUSIVE = 0xC800;
    private static final int C800_EXPANSION_ROM_START = 0xC800;
    private static final int C800_EXPANSION_ROM_END_EXCLUSIVE = 0xD000;
    private static final int VBLANK_TSTATES = 4_550;

    private final Apple2Memory memory;
    private final Apple2KeyboardDevice keyboard;
    private final Apple2GamePortDevice gamePort;
    private final Apple2SpeakerDevice speaker;
    private final Apple2SoftSwitches softSwitches;
    private final Apple2AuxMemory auxMemory;
    private final Apple2LanguageCard languageCard;
    private final Apple2SlotBus slotBus;
    private final int frameTStates;
    private final IoAddressSpace ioPorts;

    public Apple2Bus(
            TStateCounter clock,
            Apple2Memory memory,
            Apple2KeyboardDevice keyboard,
            Apple2GamePortDevice gamePort,
            Apple2SpeakerDevice speaker,
            Apple2SoftSwitches softSwitches,
            Apple2AuxMemory auxMemory,
            Apple2LanguageCard languageCard,
            Apple2SlotBus slotBus,
            int frameTStates
    ) {
        super(clock);
        this.memory = Objects.requireNonNull(memory, "memory");
        this.keyboard = Objects.requireNonNull(keyboard, "keyboard");
        this.gamePort = Objects.requireNonNull(gamePort, "gamePort");
        this.speaker = Objects.requireNonNull(speaker, "speaker");
        this.softSwitches = Objects.requireNonNull(softSwitches, "softSwitches");
        this.auxMemory = Objects.requireNonNull(auxMemory, "auxMemory");
        this.languageCard = Objects.requireNonNull(languageCard, "languageCard");
        this.slotBus = Objects.requireNonNull(slotBus, "slotBus");
        if (frameTStates <= 0) {
            throw new IllegalArgumentException("frameTStates must be positive");
        }
        this.frameTStates = frameTStates;
        this.ioPorts = buildIoMap();
    }

    @Override
    public int readMemory(int address) {
        int normalized = address & 0xFFFF;
        if (isIoAddress(normalized)) {
            return readIo(normalized);
        }
        if (isPeripheralRomAddress(normalized)) {
            if (usesInternalCxRom(normalized)) {
                return readInternalSystemRom(normalized);
            }
            if (slotBus.hasCnxxRom(normalized)) {
                return slotBus.readCnxx(normalized);
            }
            return 0xFF;
        }
        if (isC800ExpansionRomAddress(normalized)) {
            if (!usesInternalC800Rom(normalized) && slotBus.hasC800ExpansionRom()) {
                return slotBus.readC800(normalized);
            }
            return usesInternalC800Rom(normalized) ? readInternalSystemRom(normalized) : memory.read(normalized);
        }
        if (languageCard.handlesHighMemory(normalized) && languageCard.readsRam()) {
            return languageCard.readHighMemory(normalized, auxMemory.altZeroPage());
        }
        if (auxMemory.readsAux(normalized, softSwitches)) {
            return auxMemory.read(normalized);
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
        if (isPeripheralRomAddress(normalized)) {
            if (!usesInternalCxRom(normalized) && slotBus.hasCnxxRom(normalized)) {
                slotBus.writeCnxx(normalized, value);
            }
            return;
        }
        if (isC800ExpansionRomAddress(normalized)) {
            if (!usesInternalC800Rom(normalized) && slotBus.hasC800ExpansionRom()) {
                slotBus.writeC800(normalized, value);
            }
            return;
        }
        if (languageCard.handlesHighMemory(normalized) && languageCard.writesRam()) {
            languageCard.writeHighMemory(normalized, value, auxMemory.altZeroPage());
            return;
        }
        if (auxMemory.writesAux(normalized, softSwitches)) {
            auxMemory.write(normalized, value);
            return;
        }
        memory.write(normalized, value);
    }

    private static boolean isIoAddress(int address) {
        return address >= Apple2Memory.IO_START && address < Apple2Memory.IO_END_EXCLUSIVE;
    }

    private static boolean isPeripheralRomAddress(int address) {
        return address >= Apple2Memory.SLOT_ROM_START && address < PERIPHERAL_ROM_END_EXCLUSIVE;
    }

    private static boolean isC800ExpansionRomAddress(int address) {
        return address >= C800_EXPANSION_ROM_START && address < C800_EXPANSION_ROM_END_EXCLUSIVE;
    }

    private boolean usesInternalCxRom(int address) {
        if (!memory.hasSystemRomAt(address)) {
            return false;
        }
        if (!auxMemory.installed()) {
            return false;
        }
        if (address >= SLOT_3_ROM_START && address < SLOT_3_ROM_END_EXCLUSIVE) {
            return auxMemory.intCxRom() || !auxMemory.slotC3Rom();
        }
        return auxMemory.intCxRom();
    }

    private boolean usesInternalC800Rom(int address) {
        if (!memory.hasSystemRomAt(address)) {
            return false;
        }
        return auxMemory.installed() && auxMemory.intCxRom();
    }

    private int readInternalSystemRom(int address) {
        int offset = (address & 0xFFFF) - Apple2Memory.FIRMWARE_ROM_START_16K;
        return memory.readSystemRomOffset(offset);
    }

    private int readIo(int address) {
        return ioPorts.read(address, clockValue(), 0);
    }

    private void writeIo(int address, int value) {
        ioPorts.write(address, value, clockValue(), 0);
    }

    public void setIoTraceSink(IoTraceSink traceSink) {
        ioPorts.setTraceSink(traceSink);
    }

    private IoAddressSpace buildIoMap() {
        IoAddressSpace ioMap = IoAddressSpace.withUnmappedValue(0x00);
        ioMap.mapRead("apple2.keyboard-data", IoSelector.exact(KEYBOARD_DATA),
                access -> keyboard.readData()
        );
        if (auxMemory.installed()) {
            ioMap.mapWrite("apple2e.aux-soft-switches", IoSelector.range(0xC000, 0xC00F),
                    (access, value) -> auxMemory.writeSoftSwitch(access.address())
            );
            ioMap.mapRead("apple2e.status-switches", IoSelector.range(0xC013, 0xC01F),
                    access -> auxMemory.readStatus(access.address(), softSwitches)
            );
            ioMap.mapRead("apple2e.vblank-status", IoSelector.exact(VBLANK_STATUS),
                    access -> verticalBlankStatus(access.effectiveTState()),
                    1
            );
        }
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
        ioMap.mapRead("apple2.game-port-push-buttons", IoSelector.range(PUSH_BUTTON_0, PUSH_BUTTON_2),
                access -> gamePort.readPushButton(access.address())
        );
        ioMap.mapRead("apple2.game-port-paddles", IoSelector.range(PADDLE_0, PADDLE_3),
                access -> gamePort.readPaddle(access.address(), access.effectiveTState())
        );
        ioMap.mapReadWrite("apple2.game-port-paddle-trigger", IoSelector.exact(PADDLE_TRIGGER),
                access -> {
                    gamePort.triggerPaddles(access.effectiveTState());
                    return 0x00;
                },
                (access, value) -> gamePort.triggerPaddles(access.effectiveTState())
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

    private int verticalBlankStatus(long tState) {
        int frameOffset = Math.floorMod(tState, frameTStates);
        int activeTStates = Math.min(VBLANK_TSTATES, frameTStates);
        return frameOffset >= frameTStates - activeTStates ? 0x80 : 0x00;
    }
}
