package dev.z8emu.machine.spectrum48k.device;

import dev.z8emu.platform.bus.io.IoSelector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Original Kempston joystick interface.
 *
 * <p>The stock interface uses active-high direction/fire bits and only
 * decodes low-port address lines A5-A7. Consequently, its nominal port
 * {@code 0x1F} is mirrored across low-byte addresses {@code 0x00-0x1F}
 * and across every high byte.</p>
 */
public final class KempstonJoystickDevice {
    private static final IoSelector PORT_SELECTOR = IoSelector.mask(0x00E0, 0x0000);

    public static final int RIGHT = 0x01;
    public static final int LEFT = 0x02;
    public static final int DOWN = 0x04;
    public static final int UP = 0x08;
    public static final int FIRE = 0x10;
    public static final int INPUT_MASK = RIGHT | LEFT | DOWN | UP | FIRE;

    private final AtomicInteger inputState = new AtomicInteger();
    private final AtomicBoolean enabled = new AtomicBoolean();

    public static IoSelector portSelector() {
        return PORT_SELECTOR;
    }

    /**
     * Returns a selector which follows this device's current attachment state.
     * The additional mask/value pair is used for wired-AND overlap mappings.
     */
    public IoSelector enabledPortSelector(int additionalMask, int additionalValue) {
        IoSelector overlap = IoSelector.mask(0x00E0 | additionalMask, additionalValue);
        return new IoSelector() {
            @Override
            public boolean matches(int address) {
                return enabled.get() && overlap.matches(address);
            }

            @Override
            public int offset(int address) {
                return overlap.offset(address);
            }
        };
    }

    public IoSelector enabledPortSelector() {
        return enabledPortSelector(0, 0);
    }

    public boolean enabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        if (!enabled) {
            reset();
        }
        this.enabled.set(enabled);
    }

    public int readPort() {
        return inputState.get();
    }

    public void setControlPressed(int controlMask, boolean pressed) {
        int normalizedMask = controlMask & INPUT_MASK;
        if (normalizedMask == 0) {
            throw new IllegalArgumentException("controlMask must contain a Kempston input bit");
        }
        inputState.getAndUpdate(value -> pressed
                ? (value | normalizedMask)
                : (value & ~normalizedMask));
    }

    public void setInputState(int inputState) {
        this.inputState.set(inputState & INPUT_MASK);
    }

    public void reset() {
        inputState.set(0);
    }
}
