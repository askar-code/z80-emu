package dev.z8emu.cpu.i8080;

public final class I8080Registers {
    public static final int FLAG_S = 0x80;
    public static final int FLAG_Z = 0x40;
    public static final int FLAG_AC = 0x10;
    public static final int FLAG_P = 0x04;
    public static final int FLAG_C = 0x01;

    private static final int FIXED_FLAG_BITS = 0x02;
    private static final int WRITABLE_FLAG_MASK = FLAG_S | FLAG_Z | FLAG_AC | FLAG_P | FLAG_C;

    private int a;
    private int b;
    private int c;
    private int d;
    private int e;
    private int h;
    private int l;
    private int sp;
    private int pc;
    private int f = FIXED_FLAG_BITS;

    public void reset() {
        a = 0;
        b = 0;
        c = 0;
        d = 0;
        e = 0;
        h = 0;
        l = 0;
        sp = 0xFFFF;
        pc = 0;
        f = FIXED_FLAG_BITS;
    }

    public int pc() {
        return pc;
    }

    public void setPc(int value) {
        pc = value & 0xFFFF;
    }

    public void incrementPc(int delta) {
        setPc(pc + delta);
    }

    public int sp() {
        return sp;
    }

    public void setSp(int value) {
        sp = value & 0xFFFF;
    }

    public int af() {
        return ((a & 0xFF) << 8) | f();
    }

    public void setAf(int value) {
        setA((value >>> 8) & 0xFF);
        setF(value & 0xFF);
    }

    public int bc() {
        return ((b & 0xFF) << 8) | (c & 0xFF);
    }

    public void setBc(int value) {
        setB((value >>> 8) & 0xFF);
        setC(value & 0xFF);
    }

    public int de() {
        return ((d & 0xFF) << 8) | (e & 0xFF);
    }

    public void setDe(int value) {
        setD((value >>> 8) & 0xFF);
        setE(value & 0xFF);
    }

    public int hl() {
        return ((h & 0xFF) << 8) | (l & 0xFF);
    }

    public void setHl(int value) {
        setH((value >>> 8) & 0xFF);
        setL(value & 0xFF);
    }

    public int a() {
        return a & 0xFF;
    }

    public void setA(int value) {
        a = value & 0xFF;
    }

    public int b() {
        return b & 0xFF;
    }

    public void setB(int value) {
        b = value & 0xFF;
    }

    public int c() {
        return c & 0xFF;
    }

    public void setC(int value) {
        c = value & 0xFF;
    }

    public int d() {
        return d & 0xFF;
    }

    public void setD(int value) {
        d = value & 0xFF;
    }

    public int e() {
        return e & 0xFF;
    }

    public void setE(int value) {
        e = value & 0xFF;
    }

    public int h() {
        return h & 0xFF;
    }

    public void setH(int value) {
        h = value & 0xFF;
    }

    public int l() {
        return l & 0xFF;
    }

    public void setL(int value) {
        l = value & 0xFF;
    }

    public int f() {
        return normalizeFlags(f);
    }

    public void setF(int value) {
        f = normalizeFlags(value);
    }

    public boolean flagSet(int flagMask) {
        return (f() & flagMask) != 0;
    }

    public void setFlag(int flagMask, boolean enabled) {
        int flags = f();
        if (enabled) {
            flags |= flagMask;
        } else {
            flags &= ~flagMask;
        }
        setF(flags);
    }

    private static int normalizeFlags(int value) {
        return (value & WRITABLE_FLAG_MASK) | FIXED_FLAG_BITS;
    }
}
