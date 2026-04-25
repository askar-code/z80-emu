package dev.z8emu.cpu.mos6502;

public final class Mos6502Registers {
    public static final int FLAG_C = 0x01;
    public static final int FLAG_Z = 0x02;
    public static final int FLAG_I = 0x04;
    public static final int FLAG_D = 0x08;
    public static final int FLAG_B = 0x10;
    public static final int FLAG_U = 0x20;
    public static final int FLAG_V = 0x40;
    public static final int FLAG_N = 0x80;

    private int a;
    private int x;
    private int y;
    private int sp;
    private int pc;
    private int p;

    public void reset() {
        a = 0;
        x = 0;
        y = 0;
        sp = 0xFD;
        pc = 0;
        p = FLAG_I | FLAG_U;
    }

    public int a() {
        return a & 0xFF;
    }

    public void setA(int value) {
        a = value & 0xFF;
    }

    public int x() {
        return x & 0xFF;
    }

    public void setX(int value) {
        x = value & 0xFF;
    }

    public int y() {
        return y & 0xFF;
    }

    public void setY(int value) {
        y = value & 0xFF;
    }

    public int sp() {
        return sp & 0xFF;
    }

    public void setSp(int value) {
        sp = value & 0xFF;
    }

    public int pc() {
        return pc & 0xFFFF;
    }

    public void setPc(int value) {
        pc = value & 0xFFFF;
    }

    public void incrementPc(int delta) {
        setPc(pc + delta);
    }

    public int p() {
        return normalizeStatus(p);
    }

    public void setP(int value) {
        p = normalizeStatus(value);
    }

    public boolean flagSet(int flagMask) {
        return (p() & flagMask) != 0;
    }

    public void setFlag(int flagMask, boolean enabled) {
        int flags = p();
        if (enabled) {
            flags |= flagMask;
        } else {
            flags &= ~flagMask;
        }
        setP(flags);
    }

    void updateZeroAndNegative(int value) {
        int normalized = value & 0xFF;
        setFlag(FLAG_Z, normalized == 0);
        setFlag(FLAG_N, (normalized & 0x80) != 0);
    }

    private static int normalizeStatus(int value) {
        return (value | FLAG_U) & 0xFF;
    }
}
