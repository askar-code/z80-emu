package dev.z8emu.platform.bus.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IoAddressSpace {
    private static final int DEFAULT_ADDRESS_MASK = 0xFFFF;

    private final int addressMask;
    private final IoReadHandler unmappedReadHandler;
    private final List<Entry> entries = new ArrayList<>();

    public IoAddressSpace(IoReadHandler unmappedReadHandler) {
        this(DEFAULT_ADDRESS_MASK, unmappedReadHandler);
    }

    public IoAddressSpace(int addressMask, IoReadHandler unmappedReadHandler) {
        if (addressMask <= 0) {
            throw new IllegalArgumentException("addressMask must be positive");
        }
        this.addressMask = addressMask;
        this.unmappedReadHandler = Objects.requireNonNull(unmappedReadHandler, "unmappedReadHandler");
    }

    public static IoAddressSpace withUnmappedValue(int value) {
        int normalized = value & 0xFF;
        return new IoAddressSpace(access -> normalized);
    }

    public void map(String name, IoSelector selector, IoHandler handler) {
        map(name, selector, handler, 0);
    }

    public void mapRead(String name, IoSelector selector, IoReadHandler readHandler) {
        mapRead(name, selector, readHandler, 0);
    }

    public void mapRead(String name, IoSelector selector, IoReadHandler readHandler, int priority) {
        map(name, selector, IoHandler.readOnly(readHandler), priority);
    }

    public void mapWrite(String name, IoSelector selector, IoWriteHandler writeHandler) {
        mapWrite(name, selector, writeHandler, 0);
    }

    public void mapWrite(String name, IoSelector selector, IoWriteHandler writeHandler, int priority) {
        map(name, selector, IoHandler.writeOnly(writeHandler), priority);
    }

    public void mapReadWrite(
            String name,
            IoSelector selector,
            IoReadHandler readHandler,
            IoWriteHandler writeHandler
    ) {
        mapReadWrite(name, selector, readHandler, writeHandler, 0);
    }

    public void mapReadWrite(
            String name,
            IoSelector selector,
            IoReadHandler readHandler,
            IoWriteHandler writeHandler,
            int priority
    ) {
        map(name, selector, IoHandler.readWrite(readHandler, writeHandler), priority);
    }

    public void map(String name, IoSelector selector, IoHandler handler, int priority) {
        Entry entry = new Entry(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(selector, "selector"),
                Objects.requireNonNull(handler, "handler"),
                priority
        );
        ensureNoAmbiguousOverlap(entry);
        entries.add(entry);
    }

    public int read(int address) {
        return read(address, 0L, 0);
    }

    public int read(int address, long tState, int phaseTStates) {
        int normalized = normalize(address);
        Entry entry = find(normalized, true);
        IoAccess access = access(entry, normalized, tState, phaseTStates);
        if (entry == null) {
            return unmappedReadHandler.read(access) & 0xFF;
        }
        return entry.handler().read(access) & 0xFF;
    }

    public void write(int address, int value) {
        write(address, value, 0L, 0);
    }

    public void write(int address, int value, long tState, int phaseTStates) {
        int normalized = normalize(address);
        Entry entry = find(normalized, false);
        if (entry != null) {
            entry.handler().write(access(entry, normalized, tState, phaseTStates), value & 0xFF);
        }
    }

    private Entry find(int address, boolean read) {
        Entry selected = null;
        for (Entry entry : entries) {
            if (!entry.handles(read) || !entry.selector().matches(address)) {
                continue;
            }
            if (selected == null || entry.priority() > selected.priority()) {
                selected = entry;
            }
        }
        return selected;
    }

    private IoAccess access(Entry entry, int address, long tState, int phaseTStates) {
        int offset = entry == null ? address : entry.selector().offset(address);
        return new IoAccess(address, offset, tState, phaseTStates);
    }

    private int normalize(int address) {
        return address & addressMask;
    }

    private void ensureNoAmbiguousOverlap(Entry candidate) {
        for (Entry existing : entries) {
            if (!samePriorityOverlapNeedsCheck(existing, candidate)) {
                continue;
            }
            for (int address = 0; address <= addressMask; address++) {
                if (existing.selector().matches(address) && candidate.selector().matches(address)) {
                    throw new IllegalArgumentException(
                            "I/O mapping '%s' overlaps '%s' at 0x%04X with the same priority"
                                    .formatted(candidate.name(), existing.name(), address)
                    );
                }
            }
        }
    }

    private static boolean samePriorityOverlapNeedsCheck(Entry existing, Entry candidate) {
        if (existing.priority() != candidate.priority()) {
            return false;
        }
        return (existing.handler().canRead() && candidate.handler().canRead())
                || (existing.handler().canWrite() && candidate.handler().canWrite());
    }

    private record Entry(String name, IoSelector selector, IoHandler handler, int priority) {
        boolean handles(boolean read) {
            return read ? handler.canRead() : handler.canWrite();
        }
    }
}
