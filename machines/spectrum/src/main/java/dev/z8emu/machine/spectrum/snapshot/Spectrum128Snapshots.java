package dev.z8emu.machine.spectrum.snapshot;

import dev.z8emu.chip.ay.Ay38912Device;
import dev.z8emu.cpu.z80.Z80Registers;
import dev.z8emu.machine.spectrum.model.SpectrumMachineState;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.memory.RamMemoryBank;
import java.util.Objects;

/**
 * Machine-facing original Spectrum 128K snapshot API. Decoding and model
 * validation complete before reset, so a rejected image cannot partially
 * mutate the running machine.
 */
public final class Spectrum128Snapshots {
    private Spectrum128Snapshots() {
    }

    public static Spectrum128Snapshot capture(Spectrum128Machine machine) {
        Objects.requireNonNull(machine, "machine");
        Z80Registers registers = machine.cpu().registers();
        Z80SnapshotState cpu = new Z80SnapshotState(
                registers.af(),
                registers.bc(),
                registers.de(),
                registers.hl(),
                registers.afAlt(),
                registers.bcAlt(),
                registers.deAlt(),
                registers.hlAlt(),
                registers.ix(),
                registers.iy(),
                registers.sp(),
                registers.pc(),
                registers.i(),
                registers.r(),
                registers.iff1(),
                registers.iff2(),
                registers.interruptMode()
        );

        byte[][] ramBanks = new byte[Spectrum128Snapshot.RAM_BANK_COUNT][Spectrum128Snapshot.RAM_BANK_SIZE];
        Spectrum48kMemoryMap memory = machine.board().memory();
        for (int bank = 0; bank < ramBanks.length; bank++) {
            RamMemoryBank source = memory.ramBank(bank);
            for (int offset = 0; offset < ramBanks[bank].length; offset++) {
                ramBanks[bank][offset] = (byte) source.read(offset);
            }
        }
        Ay38912Device ay = machine.board().ay();
        byte[] ayRegisters = new byte[Spectrum128Snapshot.AY_REGISTER_COUNT];
        for (int register = 0; register < ayRegisters.length; register++) {
            ayRegisters[register] = (byte) ay.registerValue(register);
        }
        return new Spectrum128Snapshot(
                cpu,
                machine.board().ula().borderColor(),
                machine.board().machineState().pagingPort7ffd(),
                ay.selectedRegister(),
                ayRegisters,
                ramBanks
        );
    }

    public static void restore(Spectrum128Machine machine, Spectrum128Snapshot snapshot) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(snapshot, "snapshot");

        machine.reset();
        Spectrum48kMemoryMap memory = machine.board().memory();
        for (int bank = 0; bank < Spectrum128Snapshot.RAM_BANK_COUNT; bank++) {
            byte[] source = snapshot.ramBank(bank);
            RamMemoryBank target = memory.ramBank(bank);
            for (int offset = 0; offset < source.length; offset++) {
                target.write(offset, Byte.toUnsignedInt(source[offset]));
            }
        }

        int paging = snapshot.pagingPort7ffd();
        SpectrumMachineState state = machine.board().machineState();
        state.setPagingPort7ffd(paging);
        state.setTopRamBankIndex(paging & 0x07);
        state.setActiveScreenBankOption((paging >>> 3) & 0x01);
        state.setSelectedRomIndex((paging >>> 4) & 0x01);
        state.setPagingLocked((paging & 0x20) != 0);
        memory.applyState();

        Z80SnapshotState cpu = snapshot.cpu();
        Z80Registers registers = machine.cpu().registers();
        registers.setAf(cpu.af());
        registers.setBc(cpu.bc());
        registers.setDe(cpu.de());
        registers.setHl(cpu.hl());
        registers.setAfAlt(cpu.afAlt());
        registers.setBcAlt(cpu.bcAlt());
        registers.setDeAlt(cpu.deAlt());
        registers.setHlAlt(cpu.hlAlt());
        registers.setIx(cpu.ix());
        registers.setIy(cpu.iy());
        registers.setSp(cpu.sp());
        registers.setPc(cpu.pc());
        registers.setI(cpu.i());
        registers.setR(cpu.r());
        registers.setIff1(cpu.iff1());
        registers.setIff2(cpu.iff2());
        registers.setInterruptMode(cpu.interruptMode());

        Ay38912Device ay = machine.board().ay();
        byte[] ayRegisters = snapshot.ayRegisters();
        for (int register = 0; register < ayRegisters.length; register++) {
            ay.selectRegister(register);
            ay.writeSelectedRegister(Byte.toUnsignedInt(ayRegisters[register]));
        }
        ay.selectRegister(snapshot.selectedAyRegister());
        machine.board().ula().writePortFe(
                snapshot.borderColor(),
                0,
                machine.board().beeper(),
                memory
        );
    }

    public static byte[] saveSna(Spectrum128Machine machine) throws SpectrumSnapshotException {
        return Sna128SnapshotCodec.encode(capture(machine));
    }

    public static void loadSna(Spectrum128Machine machine, byte[] image) throws SpectrumSnapshotException {
        Spectrum128Snapshot snapshot = Sna128SnapshotCodec.decode(image);
        restore(machine, snapshot);
    }

    public static byte[] saveZ80(
            Spectrum128Machine machine,
            Z80V2V3SnapshotCodec.Compression compression
    ) {
        return Z80V2V3SnapshotCodec.encode(capture(machine), compression);
    }

    public static void loadZ80(Spectrum128Machine machine, byte[] image) throws SpectrumSnapshotException {
        SpectrumSnapshot decoded = Z80V2V3SnapshotCodec.decode(image);
        if (!(decoded instanceof Spectrum128Snapshot snapshot)) {
            throw new SpectrumSnapshotException(
                    SpectrumSnapshotException.Reason.UNSUPPORTED,
                    "Cannot load a 48K Z80 snapshot into a Spectrum 128K machine"
            );
        }
        restore(machine, snapshot);
    }
}
