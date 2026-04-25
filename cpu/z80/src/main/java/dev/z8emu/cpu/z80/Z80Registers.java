package dev.z8emu.cpu.z80;

public final class Z80Registers {
    public static final int FLAG_S = 0x80;
    public static final int FLAG_Z = 0x40;
    public static final int FLAG_5 = 0x20;
    public static final int FLAG_H = 0x10;
    public static final int FLAG_3 = 0x08;
    public static final int FLAG_PV = 0x04;
    public static final int FLAG_N = 0x02;
    public static final int FLAG_C = 0x01;

    private int af;
    private int bc;
    private int de;
    private int hl;

    private int afAlt;
    private int bcAlt;
    private int deAlt;
    private int hlAlt;

    private int ix;
    private int iy;
    private int sp;
    private int pc;

    private int i;
    private int r;

    private boolean iff1;
    private boolean iff2;
    private int interruptMode;

    public void reset() {
        af = 0;
        bc = 0;
        de = 0;
        hl = 0;
        afAlt = 0;
        bcAlt = 0;
        deAlt = 0;
        hlAlt = 0;
        ix = 0;
        iy = 0;
        sp = 0xFFFF;
        pc = 0;
        i = 0;
        r = 0;
        iff1 = false;
        iff2 = false;
        interruptMode = 0;
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
        return af;
    }

    public void setAf(int value) {
        af = value & 0xFFFF;
    }

    public int afAlt() {
        return afAlt;
    }

    public void setAfAlt(int value) {
        afAlt = value & 0xFFFF;
    }

    public int bc() {
        return bc;
    }

    public void setBc(int value) {
        bc = value & 0xFFFF;
    }

    public int bcAlt() {
        return bcAlt;
    }

    public void setBcAlt(int value) {
        bcAlt = value & 0xFFFF;
    }

    public int de() {
        return de;
    }

    public void setDe(int value) {
        de = value & 0xFFFF;
    }

    public int deAlt() {
        return deAlt;
    }

    public void setDeAlt(int value) {
        deAlt = value & 0xFFFF;
    }

    public int hl() {
        return hl;
    }

    public void setHl(int value) {
        hl = value & 0xFFFF;
    }

    public int hlAlt() {
        return hlAlt;
    }

    public void setHlAlt(int value) {
        hlAlt = value & 0xFFFF;
    }

    public int ix() {
        return ix;
    }

    public void setIx(int value) {
        ix = value & 0xFFFF;
    }

    public int iy() {
        return iy;
    }

    public void setIy(int value) {
        iy = value & 0xFFFF;
    }

    public int a() {
        return (af >>> 8) & 0xFF;
    }

    public void setA(int value) {
        af = ((value & 0xFF) << 8) | (af & 0x00FF);
    }

    public int f() {
        return af & 0xFF;
    }

    public void setF(int value) {
        af = (af & 0xFF00) | (value & 0xFF);
    }

    public int b() {
        return (bc >>> 8) & 0xFF;
    }

    public void setB(int value) {
        bc = ((value & 0xFF) << 8) | (bc & 0x00FF);
    }

    public int c() {
        return bc & 0xFF;
    }

    public void setC(int value) {
        bc = (bc & 0xFF00) | (value & 0xFF);
    }

    public int d() {
        return (de >>> 8) & 0xFF;
    }

    public void setD(int value) {
        de = ((value & 0xFF) << 8) | (de & 0x00FF);
    }

    public int e() {
        return de & 0xFF;
    }

    public void setE(int value) {
        de = (de & 0xFF00) | (value & 0xFF);
    }

    public int h() {
        return (hl >>> 8) & 0xFF;
    }

    public void setH(int value) {
        hl = ((value & 0xFF) << 8) | (hl & 0x00FF);
    }

    public int l() {
        return hl & 0xFF;
    }

    public void setL(int value) {
        hl = (hl & 0xFF00) | (value & 0xFF);
    }

    public int i() {
        return i & 0xFF;
    }

    public void setI(int value) {
        i = value & 0xFF;
    }

    public int r() {
        return r & 0xFF;
    }

    public void setR(int value) {
        r = value & 0xFF;
    }

    public int ir() {
        return ((i & 0xFF) << 8) | (r & 0xFF);
    }

    public void onInstructionFetch() {
        r = ((r + 1) & 0x7F) | (r & 0x80);
    }

    public boolean iff1() {
        return iff1;
    }

    public void setIff1(boolean value) {
        iff1 = value;
    }

    public boolean iff2() {
        return iff2;
    }

    public void setIff2(boolean value) {
        iff2 = value;
    }

    public int interruptMode() {
        return interruptMode;
    }

    public void setInterruptMode(int value) {
        if (value < 0 || value > 2) {
            throw new IllegalArgumentException("interruptMode must be between 0 and 2");
        }

        interruptMode = value;
    }

    public boolean flagSet(int flagMask) {
        return (f() & flagMask) != 0;
    }

    public void setFlag(int flagMask, boolean enabled) {
        int updated = enabled ? (f() | flagMask) : (f() & ~flagMask);
        setF(updated);
    }

    public void swapAfWithAlternate() {
        int current = af;
        af = afAlt;
        afAlt = current;
    }

    public void swapGeneralRegistersWithAlternate() {
        int currentBc = bc;
        int currentDe = de;
        int currentHl = hl;

        bc = bcAlt;
        de = deAlt;
        hl = hlAlt;

        bcAlt = currentBc;
        deAlt = currentDe;
        hlAlt = currentHl;
    }
}
