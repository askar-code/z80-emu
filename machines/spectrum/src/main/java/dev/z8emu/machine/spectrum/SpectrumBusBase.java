package dev.z8emu.machine.spectrum;

import dev.z8emu.machine.spectrum.model.SpectrumContentionModel;
import dev.z8emu.machine.spectrum48k.device.BeeperDevice;
import dev.z8emu.machine.spectrum48k.device.KeyboardMatrixDevice;
import dev.z8emu.machine.spectrum48k.device.KempstonJoystickDevice;
import dev.z8emu.machine.spectrum48k.device.SpectrumUlaDevice;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.bus.ClockedCpuBus;
import dev.z8emu.platform.bus.CpuBus.InternalCycleType;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.bus.io.IoAddressSpace;
import dev.z8emu.platform.bus.io.IoTraceSink;
import dev.z8emu.platform.device.TimedDevice;
import dev.z8emu.platform.time.TStateCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class SpectrumBusBase extends ClockedCpuBus {
    private static final int KEMPSTON_PORT_PRIORITY = 60;
    private static final int ULA_PORT_PRIORITY = 90;
    private static final int ULA_KEMPSTON_OVERLAP_PRIORITY = 100;

    private final Spectrum48kMemoryMap memory;
    private final SpectrumUlaDevice ula;
    private final BeeperDevice beeper;
    private final SpectrumContentionModel contentionModel;
    private final IoAddressSpace ports;
    private final List<AudioTimeline> audioTimelines = new ArrayList<>();
    private final int beeperAudioTimeline;
    private long preparedDisplayWriteTState = Long.MIN_VALUE;
    private long preparedDisplayWriteClock = Long.MIN_VALUE;
    private int preparedDisplayWriteAddress = -1;
    private int preparedDisplayWriteValue = -1;

    protected SpectrumBusBase(
            TStateCounter clock,
            Spectrum48kMemoryMap memory,
            SpectrumUlaDevice ula,
            KeyboardMatrixDevice keyboard,
            KempstonJoystickDevice kempstonJoystick,
            BeeperDevice beeper,
            TapeDevice tape,
            int frameTStates,
            int contentionStartTState,
            int tStatesPerScanline
    ) {
        super(clock);
        this.memory = Objects.requireNonNull(memory, "memory");
        this.ula = Objects.requireNonNull(ula, "ula");
        Objects.requireNonNull(keyboard, "keyboard");
        Objects.requireNonNull(kempstonJoystick, "kempstonJoystick");
        this.beeper = Objects.requireNonNull(beeper, "beeper");
        Objects.requireNonNull(tape, "tape");
        this.contentionModel = new SpectrumContentionModel(
                frameTStates,
                contentionStartTState,
                tStatesPerScanline
        );
        this.ports = new IoAddressSpace(
                access -> ula.readFloatingBus(memory, access.effectiveTState())
        );
        this.beeperAudioTimeline = registerAudioDevice(beeper);
        ports.mapRead(
                "spectrum.ula-fe",
                SpectrumUlaDevice.portSelector(),
                access -> ula.readPortFe(access, keyboard, tape),
                ULA_PORT_PRIORITY
        );
        ports.mapRead(
                "spectrum.kempston",
                kempstonJoystick.enabledPortSelector(),
                access -> kempstonJoystick.readPort(),
                KEMPSTON_PORT_PRIORITY
        );
        ports.mapRead(
                "spectrum.ula-kempston-overlap",
                kempstonJoystick.enabledPortSelector(0x0001, 0x0000),
                access -> ula.readPortFe(access, keyboard, tape) & kempstonJoystick.readPort(),
                ULA_KEMPSTON_OVERLAP_PRIORITY
        );
    }

    @Override
    public int fetchOpcodeWaitStates(int address, int phaseTStates) {
        return contentionModel.memoryDelay(clockValue(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public int readMemory(int address) {
        return memory.read(address);
    }

    @Override
    public int readMemoryWaitStates(int address, int phaseTStates) {
        return contentionModel.memoryDelay(clockValue(), phaseTStates, memory.isContendedAddress(address));
    }

    @Override
    public void writeMemory(int address, int value) {
        int normalizedAddress = address & 0xFFFF;
        int normalizedValue = value & 0xFF;
        long mutationTState = clockValue();
        if (preparedDisplayWriteClock == mutationTState
                && preparedDisplayWriteAddress == normalizedAddress
                && preparedDisplayWriteValue == normalizedValue) {
            mutationTState = preparedDisplayWriteTState;
        }
        clearPreparedDisplayWrite();
        if (memory.isActiveDisplayAddress(normalizedAddress)) {
            // Capture through the mutation edge. A ULA fetch at the same
            // t-state therefore observes the value which was present before
            // this write, matching the ordering of the physical RAM cycle.
            ula.syncToTState(mutationTState, memory);
        }
        memory.write(address, value);
    }

    @Override
    public int writeMemoryWaitStates(int address, int value, int phaseTStates) {
        int waitStates = contentionModel.memoryDelay(
                clockValue(),
                phaseTStates,
                memory.isContendedAddress(address)
        );
        if (memory.isActiveDisplayAddress(address)) {
            preparedDisplayWriteClock = clockValue();
            preparedDisplayWriteTState = preparedDisplayWriteClock
                    + Math.max(0, phaseTStates)
                    + 3L
                    + waitStates;
            preparedDisplayWriteAddress = address & 0xFFFF;
            preparedDisplayWriteValue = value & 0xFF;
        } else {
            clearPreparedDisplayWrite();
        }
        return waitStates;
    }

    @Override
    public int internalCycleWaitStates(
            int address,
            int phaseTStates,
            int tStates,
            InternalCycleType type
    ) {
        if (type != InternalCycleType.READ_NO_MREQ
                && type != InternalCycleType.WRITE_NO_MREQ) {
            return 0;
        }
        return contentionModel.internalMemoryDelay(
                clockValue(),
                phaseTStates,
                memory.isContendedAddress(address),
                tStates
        );
    }

    @Override
    public int readPort(int port) {
        return readPort(port, 0);
    }

    @Override
    public int readPort(int port, int phaseTStates) {
        int waitStates = readPortWaitStates(port, phaseTStates);
        return ports.read(port, clockValue() + 3L + waitStates, phaseTStates);
    }

    @Override
    public void writePort(int port, int value) {
        writePort(port, value, 0);
    }

    @Override
    public void writePort(int port, int value, int phaseTStates) {
        int waitStates = writePortWaitStates(port, value, phaseTStates);
        long eventTState = clockValue() + 3L + waitStates;
        if (SpectrumUlaDevice.portSelector().matches(port)) {
            IoAccess access = new IoAccess(
                    port,
                    SpectrumUlaDevice.portSelector().offset(port),
                    eventTState,
                    phaseTStates
            );
            syncAudioDevice(beeperAudioTimeline, access.effectiveTState());
            ula.writePortFe(access, value, beeper, memory);
        }
        // Spectrum devices are only partially decoded and can therefore see
        // the same OUT cycle. Keep dispatching after the ULA write so an even
        // paging or AY mirror updates both chips, as the physical bus does.
        ports.write(port, value, eventTState, phaseTStates);
    }

    @Override
    public int readPortWaitStates(int port, int phaseTStates) {
        return contentionModel.ioPortDelay(
                clockValue(),
                phaseTStates,
                port,
                memory.isContendedAddress(port)
        );
    }

    @Override
    public int writePortWaitStates(int port, int value, int phaseTStates) {
        return contentionModel.ioPortDelay(
                clockValue(),
                phaseTStates,
                port,
                memory.isContendedAddress(port)
        );
    }

    protected final IoAddressSpace ports() {
        return ports;
    }

    /** Synchronizes every Spectrum audio source through an absolute machine t-state. */
    public final void syncAudioTo(long targetTState) {
        for (AudioTimeline timeline : audioTimelines) {
            timeline.syncTo(targetTState);
        }
    }

    /** Re-anchors audio timelines after the devices and machine clock have been reset. */
    public final void resetAudioTiming() {
        long currentTState = clockValue();
        for (AudioTimeline timeline : audioTimelines) {
            timeline.resetTo(currentTState);
        }
    }

    public final void setIoTraceSink(IoTraceSink traceSink) {
        ports.setTraceSink(traceSink);
    }

    protected final int registerAudioDevice(TimedDevice device) {
        AudioTimeline timeline = new AudioTimeline(device, clockValue());
        audioTimelines.add(timeline);
        return audioTimelines.size() - 1;
    }

    protected final void syncAudioDevice(int timelineIndex, long targetTState) {
        audioTimelines.get(timelineIndex).syncTo(targetTState);
    }

    private void clearPreparedDisplayWrite() {
        preparedDisplayWriteTState = Long.MIN_VALUE;
        preparedDisplayWriteClock = Long.MIN_VALUE;
        preparedDisplayWriteAddress = -1;
        preparedDisplayWriteValue = -1;
    }

    private static final class AudioTimeline {
        private final TimedDevice device;
        private long elapsedTStates;

        private AudioTimeline(TimedDevice device, long elapsedTStates) {
            this.device = Objects.requireNonNull(device, "device");
            this.elapsedTStates = elapsedTStates;
        }

        private void syncTo(long targetTState) {
            if (targetTState < elapsedTStates) {
                throw new IllegalArgumentException(
                        "Spectrum audio timestamp moved backwards from " + elapsedTStates + " to " + targetTState
                );
            }
            while (elapsedTStates < targetTState) {
                int delta = (int) Math.min(Integer.MAX_VALUE, targetTState - elapsedTStates);
                device.onTStatesElapsed(delta);
                elapsedTStates += delta;
            }
        }

        private void resetTo(long targetTState) {
            elapsedTStates = targetTState;
        }
    }
}
