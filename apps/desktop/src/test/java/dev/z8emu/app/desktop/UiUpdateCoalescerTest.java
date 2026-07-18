package dev.z8emu.app.desktop;

import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiUpdateCoalescerTest {
    @Test
    void keepsOnlyOneUpdatePendingUntilThatUpdateFinishes() {
        Queue<Runnable> scheduled = new ArrayDeque<>();
        UiUpdateCoalescer coalescer = new UiUpdateCoalescer(scheduled::add);
        int[] updateCount = {0};

        assertTrue(coalescer.request(() -> updateCount[0]++));
        assertFalse(coalescer.request(() -> updateCount[0] += 100));
        assertFalse(coalescer.request(() -> updateCount[0] += 1_000));
        assertEquals(1, scheduled.size());
        assertEquals(0, updateCount[0]);

        scheduled.remove().run();

        assertEquals(1, updateCount[0]);
        assertTrue(coalescer.request(() -> updateCount[0]++));
        assertEquals(1, scheduled.size());
        scheduled.remove().run();
        assertEquals(2, updateCount[0]);
    }

    @Test
    void releasesPendingSlotWhenAnUpdateFails() {
        Queue<Runnable> scheduled = new ArrayDeque<>();
        UiUpdateCoalescer coalescer = new UiUpdateCoalescer(scheduled::add);

        assertTrue(coalescer.request(() -> {
            throw new IllegalStateException("paint failed");
        }));
        assertThrows(IllegalStateException.class, () -> scheduled.remove().run());

        assertTrue(coalescer.request(() -> {
        }));
        assertEquals(1, scheduled.size());
    }

    @Test
    void releasesPendingSlotWhenTheSchedulerRejectsAnUpdate() {
        Queue<Runnable> scheduled = new ArrayDeque<>();
        int[] attempts = {0};
        UiUpdateCoalescer coalescer = new UiUpdateCoalescer(update -> {
            if (attempts[0]++ == 0) {
                throw new IllegalStateException("EDT unavailable");
            }
            scheduled.add(update);
        });

        assertThrows(IllegalStateException.class, () -> coalescer.request(() -> {
        }));
        assertTrue(coalescer.request(() -> {
        }));
        assertEquals(1, scheduled.size());
    }
}
