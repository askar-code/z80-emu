package dev.z8emu.machine.spectrum.snapshot;

import dev.z8emu.cpu.z80.Z80Registers;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import java.util.Objects;

/**
 * Machine-facing 48K snapshot API. Decoding completes before a machine is
 * reset, so malformed and unsupported images leave the running machine alone.
 */
public final class Spectrum48Snapshots {
    private Spectrum48Snapshots() {
    }

    public static Spectrum48Snapshot capture(Spectrum48kMachine machine) {
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
        byte[] ram = new byte[Spectrum48Snapshot.RAM_SIZE];
        for (int offset = 0; offset < ram.length; offset++) {
            ram[offset] = (byte) machine.board().memory().read(0x4000 + offset);
        }
        return new Spectrum48Snapshot(cpu, machine.board().ula().borderColor(), ram);
    }

    public static void restore(Spectrum48kMachine machine, Spectrum48Snapshot snapshot) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(snapshot, "snapshot");

        machine.reset();
        byte[] ram = snapshot.ram();
        for (int offset = 0; offset < ram.length; offset++) {
            machine.board().memory().write(0x4000 + offset, Byte.toUnsignedInt(ram[offset]));
        }

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
        machine.board().ula().writePortFe(
                snapshot.borderColor(),
                0,
                machine.board().beeper(),
                machine.board().memory()
        );
    }

    public static byte[] saveSna(Spectrum48kMachine machine) throws SpectrumSnapshotException {
        return Sna48SnapshotCodec.encode(capture(machine));
    }

    public static void loadSna(Spectrum48kMachine machine, byte[] image) throws SpectrumSnapshotException {
        Spectrum48Snapshot snapshot = Sna48SnapshotCodec.decode(image);
        restore(machine, snapshot);
    }

    public static byte[] saveZ80(
            Spectrum48kMachine machine,
            Z80V1SnapshotCodec.Compression compression
    ) throws SpectrumSnapshotException {
        return Z80V1SnapshotCodec.encode(capture(machine), compression);
    }

    public static void loadZ80(Spectrum48kMachine machine, byte[] image) throws SpectrumSnapshotException {
        SpectrumSnapshot decoded = SpectrumSnapshots.decodeZ80(image);
        if (!(decoded instanceof Spectrum48Snapshot snapshot)) {
            throw new SpectrumSnapshotException(
                    SpectrumSnapshotException.Reason.UNSUPPORTED,
                    "Cannot load a 128K Z80 snapshot into a Spectrum 48K machine"
            );
        }
        restore(machine, snapshot);
    }

    public static byte[] saveZ80V3(
            Spectrum48kMachine machine,
            Z80V2V3SnapshotCodec.Compression compression
    ) {
        return Z80V2V3SnapshotCodec.encode(capture(machine), compression);
    }
}
