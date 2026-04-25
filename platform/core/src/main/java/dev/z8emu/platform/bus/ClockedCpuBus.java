package dev.z8emu.platform.bus;

import dev.z8emu.platform.time.TStateCounter;
import java.util.Objects;

public abstract class ClockedCpuBus implements CpuBus {
    private final TStateCounter clock;

    protected ClockedCpuBus(TStateCounter clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    protected final long clockValue() {
        return clock.value();
    }

    @Override
    public final int currentTState() {
        long tState = clock.value();
        return tState > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tState;
    }
}
