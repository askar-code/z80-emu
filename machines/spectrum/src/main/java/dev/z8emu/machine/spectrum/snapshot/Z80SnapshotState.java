package dev.z8emu.machine.spectrum.snapshot;

/** CPU state shared by the supported Spectrum snapshot formats. */
public record Z80SnapshotState(
        int af,
        int bc,
        int de,
        int hl,
        int afAlt,
        int bcAlt,
        int deAlt,
        int hlAlt,
        int ix,
        int iy,
        int sp,
        int pc,
        int i,
        int r,
        boolean iff1,
        boolean iff2,
        int interruptMode
) {
    public Z80SnapshotState {
        requireUnsigned16("af", af);
        requireUnsigned16("bc", bc);
        requireUnsigned16("de", de);
        requireUnsigned16("hl", hl);
        requireUnsigned16("afAlt", afAlt);
        requireUnsigned16("bcAlt", bcAlt);
        requireUnsigned16("deAlt", deAlt);
        requireUnsigned16("hlAlt", hlAlt);
        requireUnsigned16("ix", ix);
        requireUnsigned16("iy", iy);
        requireUnsigned16("sp", sp);
        requireUnsigned16("pc", pc);
        requireUnsigned8("i", i);
        requireUnsigned8("r", r);
        if (interruptMode < 0 || interruptMode > 2) {
            throw new IllegalArgumentException("interruptMode must be between 0 and 2: " + interruptMode);
        }
    }

    private static void requireUnsigned8(String name, int value) {
        if ((value & ~0xFF) != 0) {
            throw new IllegalArgumentException(name + " must be an unsigned 8-bit value: " + value);
        }
    }

    private static void requireUnsigned16(String name, int value) {
        if ((value & ~0xFFFF) != 0) {
            throw new IllegalArgumentException(name + " must be an unsigned 16-bit value: " + value);
        }
    }
}
