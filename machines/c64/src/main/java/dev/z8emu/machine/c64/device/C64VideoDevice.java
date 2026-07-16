package dev.z8emu.machine.c64.device;

import dev.z8emu.machine.c64.C64Memory;
import dev.z8emu.platform.device.TimedDevice;
import dev.z8emu.platform.video.FrameBuffer;
import java.util.Arrays;
import java.util.Objects;

public final class C64VideoDevice implements TimedDevice {
    public static final int TEXT_COLUMNS = 40;
    public static final int TEXT_ROWS = 25;
    public static final int CELL_SIZE = 8;
    public static final int FRAME_WIDTH = 384;
    public static final int FRAME_HEIGHT = 272;
    public static final int BORDER_LEFT = (FRAME_WIDTH - TEXT_COLUMNS * CELL_SIZE) / 2;
    public static final int BORDER_TOP = (FRAME_HEIGHT - TEXT_ROWS * CELL_SIZE) / 2;
    public static final int CYCLES_PER_LINE = 63;
    public static final int LINES_PER_FRAME = 312;

    private static final int FRAME_CYCLES = CYCLES_PER_LINE * LINES_PER_FRAME;
    private static final int[] PALETTE = {
            0xFF000000, // black
            0xFFFFFFFF, // white
            0xFF813338, // red
            0xFF75CEC8, // cyan
            0xFF8E3C97, // purple
            0xFF56AC4D, // green
            0xFF2E2C9B, // blue
            0xFFEDF171, // yellow
            0xFF8E5029, // orange
            0xFF553800, // brown
            0xFFC46C71, // light red
            0xFF4A4A4A, // dark grey
            0xFF7B7B7B, // grey
            0xFFA9FF9F, // light green
            0xFF706DEB, // light blue
            0xFFB2B2B2, // light grey
    };

    private final C64Memory memory;
    private final int[] registers = new int[0x2F];
    private final FrameBuffer frame = new FrameBuffer(FRAME_WIDTH, FRAME_HEIGHT);

    private int interruptLatch;
    private int rasterCompare;
    private int frameCycle;

    public C64VideoDevice(C64Memory memory) {
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    public int readRegister(int offset) {
        int registerIndex = offset & 0x3F;
        if (registerIndex >= registers.length) {
            return 0xFF;
        }
        return switch (registerIndex) {
            case 0x11 -> (registers[registerIndex] & 0x7F) | ((rasterLine() & 0x100) >> 1);
            case 0x12 -> rasterLine() & 0xFF;
            case 0x13, 0x14 -> 0x00; // Light pen input is not connected in Phase 2.
            case 0x16 -> registers[registerIndex] | 0xC0;
            case 0x18 -> registers[registerIndex] | 0x01;
            case 0x19 -> interruptLatch | 0x70 | (interruptLineActive() ? 0x80 : 0);
            case 0x1A -> registers[registerIndex] | 0xF0;
            case 0x1E, 0x1F -> 0x00; // Sprites and their read-clearing collision latches do not exist yet.
            default -> registerIndex >= 0x20
                    ? registers[registerIndex] | 0xF0
                    : registers[registerIndex];
        };
    }

    public void writeRegister(int offset, int value) {
        int registerIndex = offset & 0x3F;
        if (registerIndex >= registers.length) {
            return;
        }
        int byteValue = value & 0xFF;
        switch (registerIndex) {
            case 0x11 -> {
                registers[registerIndex] = byteValue & 0x7F;
                rasterCompare = (rasterCompare & 0xFF) | ((byteValue & 0x80) << 1);
            }
            case 0x12 -> {
                registers[registerIndex] = byteValue;
                rasterCompare = (rasterCompare & 0x100) | byteValue;
            }
            case 0x19 -> interruptLatch &= ~(byteValue & 0x0F);
            default -> registers[registerIndex] = byteValue;
        }
    }

    @Override
    public void reset() {
        Arrays.fill(registers, 0);
        interruptLatch = 0;
        rasterCompare = 0;
        frameCycle = 0;
    }

    @Override
    public void onTStatesElapsed(int tStates) {
        for (int cycle = 0; cycle < tStates; cycle++) {
            frameCycle++;
            if (frameCycle == FRAME_CYCLES) {
                frameCycle = 0;
            }
            if (frameCycle % CYCLES_PER_LINE == 0 && rasterLine() == rasterCompare) {
                interruptLatch |= 0x01;
            }
        }
    }

    public int rasterLine() {
        return frameCycle / CYCLES_PER_LINE;
    }

    public boolean interruptLineActive() {
        return (interruptLatch & registers[0x1A] & 0x0F) != 0;
    }

    public FrameBuffer renderFrame(int cia2PortA) {
        frame.clear(PALETTE[registers[0x20] & 0x0F]);
        if ((registers[0x11] & 0x10) == 0) {
            return frame;
        }

        // Phase 2 stores mode and scroll bits but always takes one standard-text full-frame snapshot.
        int bankBase = ((~cia2PortA) & 0x03) * 0x4000;
        int matrixBase = ((registers[0x18] >> 4) & 0x0F) * 0x400;
        int charsetBase = ((registers[0x18] >> 1) & 0x07) * 0x800;
        int background = PALETTE[registers[0x21] & 0x0F];
        for (int row = 0; row < TEXT_ROWS; row++) {
            for (int column = 0; column < TEXT_COLUMNS; column++) {
                int cellOffset = row * TEXT_COLUMNS + column;
                int screenCode = vicRead(matrixBase + cellOffset, bankBase);
                int foreground = PALETTE[memory.readColorRam(cellOffset)];
                int targetX = BORDER_LEFT + column * CELL_SIZE;
                int targetY = BORDER_TOP + row * CELL_SIZE;
                for (int y = 0; y < CELL_SIZE; y++) {
                    int glyphBits = vicRead(charsetBase + screenCode * CELL_SIZE + y, bankBase);
                    for (int x = 0; x < CELL_SIZE; x++) {
                        int color = ((glyphBits >>> (7 - x)) & 1) != 0 ? foreground : background;
                        frame.setPixel(targetX + x, targetY + y, color);
                    }
                }
            }
        }
        return frame;
    }

    public static int paletteArgb(int index) {
        return PALETTE[index & 0x0F];
    }

    private int vicRead(int address, int bankBase) {
        int fullAddress = (bankBase + (address & 0x3FFF)) & 0xFFFF;
        int bankIndependentAddress = fullAddress & 0x7FFF;
        if (bankIndependentAddress >= 0x1000 && bankIndependentAddress <= 0x1FFF) {
            return memory.readCharRom(fullAddress & 0x0FFF);
        }
        return memory.readRam(fullAddress);
    }
}
