package dev.z8emu.platform.bus.io;

import java.util.Objects;

public final class IoHandler {
    private final IoReadHandler readHandler;
    private final IoWriteHandler writeHandler;

    private IoHandler(IoReadHandler readHandler, IoWriteHandler writeHandler) {
        this.readHandler = readHandler;
        this.writeHandler = writeHandler;
    }

    public static IoHandler readOnly(IoReadHandler readHandler) {
        return new IoHandler(Objects.requireNonNull(readHandler, "readHandler"), null);
    }

    public static IoHandler writeOnly(IoWriteHandler writeHandler) {
        return new IoHandler(null, Objects.requireNonNull(writeHandler, "writeHandler"));
    }

    public static IoHandler readWrite(IoReadHandler readHandler, IoWriteHandler writeHandler) {
        return new IoHandler(
                Objects.requireNonNull(readHandler, "readHandler"),
                Objects.requireNonNull(writeHandler, "writeHandler")
        );
    }

    public boolean canRead() {
        return readHandler != null;
    }

    public boolean canWrite() {
        return writeHandler != null;
    }

    public int read(IoAccess access) {
        if (readHandler == null) {
            throw new IllegalStateException("I/O handler is not readable");
        }
        return readHandler.read(access);
    }

    public void write(IoAccess access, int value) {
        if (writeHandler == null) {
            throw new IllegalStateException("I/O handler is not writable");
        }
        writeHandler.write(access, value);
    }
}
