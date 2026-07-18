package dev.z8emu.app.desktop;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Keeps an asynchronous UI publisher from queuing more than one pending update.
 * The submitted update may read the latest application state when it eventually
 * runs, so intermediate publication requests can be discarded safely.
 */
final class UiUpdateCoalescer {
    private final Consumer<Runnable> scheduler;
    private final AtomicBoolean pending = new AtomicBoolean();

    UiUpdateCoalescer(Consumer<Runnable> scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    boolean request(Runnable update) {
        Objects.requireNonNull(update, "update");
        if (!pending.compareAndSet(false, true)) {
            return false;
        }

        try {
            scheduler.accept(() -> {
                try {
                    update.run();
                } finally {
                    pending.set(false);
                }
            });
        } catch (RuntimeException | Error schedulingFailure) {
            pending.set(false);
            throw schedulingFailure;
        }
        return true;
    }
}
