package dev.z8emu.app.desktop;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;

abstract class AbstractHostKeyboardController implements KeyEventDispatcher, AutoCloseable {
    private final Window window;
    private volatile String lastEvent = "none";

    protected AbstractHostKeyboardController(Window window) {
        this.window = Objects.requireNonNull(window, "window");
    }

    protected final void bindToComponent(JComponent component) {
        Objects.requireNonNull(component, "component");
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
        component.setFocusable(true);
        component.setFocusTraversalKeysEnabled(false);
    }

    @Override
    public final boolean dispatchKeyEvent(KeyEvent event) {
        if (!window.isActive()) {
            return false;
        }

        if (handleMetaKey(event)) {
            return true;
        }

        if (handleTypedCharacter(event)) {
            return true;
        }

        if (handleHostAction(event)) {
            return true;
        }

        if (event.getID() == KeyEvent.KEY_PRESSED) {
            updateKeys(event, true);
        } else if (event.getID() == KeyEvent.KEY_RELEASED) {
            updateKeys(event, false);
        }

        return false;
    }

    final String lastEvent() {
        return lastEvent;
    }

    @Override
    public final void close() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    }

    protected boolean handleMetaKey(KeyEvent event) {
        return false;
    }

    protected boolean handleTypedCharacter(KeyEvent event) {
        return false;
    }

    protected boolean handleHostAction(KeyEvent event) {
        return false;
    }

    protected void updateKeys(KeyEvent event, boolean pressed) {
    }

    protected final void setLastEvent(String lastEvent) {
        this.lastEvent = Objects.requireNonNull(lastEvent, "lastEvent");
    }

    protected final void markFocusLost() {
        setLastEvent("focus-lost");
    }

    protected final boolean handleTapeHostAction(KeyEvent event, HostTapeActions hostActions) {
        Objects.requireNonNull(hostActions, "hostActions");
        if (!event.isMetaDown()) {
            return false;
        }

        return switch (event.getKeyCode()) {
            case KeyEvent.VK_P -> {
                if (event.getID() == KeyEvent.KEY_RELEASED) {
                    hostActions.toggleTapePlayback();
                    setLastEvent("host:Cmd+P");
                }
                yield true;
            }
            case KeyEvent.VK_R -> {
                if (event.getID() == KeyEvent.KEY_RELEASED) {
                    hostActions.rewindTape();
                    setLastEvent("host:Cmd+R");
                }
                yield true;
            }
            case KeyEvent.VK_S -> {
                if (event.getID() == KeyEvent.KEY_RELEASED) {
                    hostActions.stopTape();
                    setLastEvent("host:Cmd+S");
                }
                yield true;
            }
            default -> false;
        };
    }

    interface HostTapeActions {
        void toggleTapePlayback();

        void rewindTape();

        void stopTape();
    }
}

abstract class AbstractMappedHostKeyboardController<K> extends AbstractHostKeyboardController {
    protected AbstractMappedHostKeyboardController(Window window) {
        super(window);
    }

    @Override
    protected final void updateKeys(KeyEvent event, boolean pressed) {
        List<K> keys = keysFor(event.getKeyCode());
        if (keys.isEmpty()) {
            return;
        }

        for (K key : keys) {
            updateKey(key, pressed);
        }
        setLastEvent("%s:%s".formatted(pressed ? "down" : "up", KeyEvent.getKeyText(event.getKeyCode())));
    }

    protected abstract List<K> keysFor(int keyCode);

    protected abstract void updateKey(K key, boolean pressed);
}
