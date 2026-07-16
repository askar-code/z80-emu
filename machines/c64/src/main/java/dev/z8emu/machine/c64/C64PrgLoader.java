package dev.z8emu.machine.c64;

import dev.z8emu.machine.c64.media.C64PrgImage;
import java.util.Objects;

public final class C64PrgLoader {
    private static final int BASIC_START = 0x0801;

    private C64PrgLoader() {
    }

    public static void inject(C64Machine machine, C64PrgImage image) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(image, "image");
        C64Memory memory = machine.board().memory();
        byte[] payload = image.payload();
        for (int offset = 0; offset < payload.length; offset++) {
            memory.writeRam(image.loadAddress() + offset, Byte.toUnsignedInt(payload[offset]));
        }
        if (image.loadAddress() == BASIC_START) {
            int endAddress = image.endAddress();
            for (int pointer = 0x2D; pointer <= 0x31; pointer += 2) {
                memory.writeRam(pointer, endAddress & 0xFF);
                memory.writeRam(pointer + 1, endAddress >>> 8);
            }
        }
    }

    public static String startCommand(C64PrgImage image, Integer sysAddress) {
        Objects.requireNonNull(image, "image");
        if (sysAddress != null) {
            return "SYS " + sysAddress + "\r";
        }
        if (image.loadAddress() == BASIC_START) {
            return "RUN\r";
        }
        return "SYS " + image.loadAddress() + "\r";
    }
}
