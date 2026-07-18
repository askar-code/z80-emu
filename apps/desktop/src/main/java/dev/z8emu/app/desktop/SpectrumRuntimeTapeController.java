package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Bridges host UI actions to the Spectrum emulation thread. Queue methods are
 * safe to call from AWT; {@link #processPending()} is called only by runSlice.
 */
final class SpectrumRuntimeTapeController {
    private final TapeDevice tape;
    private final Runnable cancelAutomation;
    private final Consumer<String> errorSink;
    private final Queue<Command> commands = new ConcurrentLinkedQueue<>();

    private volatile String sourceLabel;

    SpectrumRuntimeTapeController(
            TapeDevice tape,
            String initialSourceLabel,
            Runnable cancelAutomation,
            Consumer<String> errorSink
    ) {
        this.tape = Objects.requireNonNull(tape, "tape");
        this.sourceLabel = initialSourceLabel;
        this.cancelAutomation = Objects.requireNonNull(cancelAutomation, "cancelAutomation");
        this.errorSink = Objects.requireNonNull(errorSink, "errorSink");
    }

    void replace(SpectrumTapeFiles.LoadedTape loadedTape) {
        commands.add(new Replace(Objects.requireNonNull(loadedTape, "loadedTape")));
    }

    void eject() {
        commands.add(Action.EJECT);
    }

    void togglePlayback() {
        commands.add(Action.TOGGLE_PLAYBACK);
    }

    void stop() {
        commands.add(Action.STOP);
    }

    void rewind() {
        commands.add(Action.REWIND);
    }

    void nextBlock() {
        commands.add(Action.NEXT_BLOCK);
    }

    void previousBlock() {
        commands.add(Action.PREVIOUS_BLOCK);
    }

    void selectBlock(int zeroBasedBlockIndex) {
        commands.add(new SelectBlock(zeroBasedBlockIndex));
    }

    int processPending() {
        int processed = 0;
        Command command;
        while ((command = commands.poll()) != null) {
            try {
                process(command);
            } catch (RuntimeException failure) {
                errorSink.accept("Tape operation failed: " + failure.getMessage());
            }
            processed++;
        }
        return processed;
    }

    String statusText() {
        TapeDevice.TransportStatus status = tape.transportStatus();
        if (!status.loaded()) {
            return "none";
        }

        String state = status.playing() ? "play" : (status.atEnd() ? "eof" : "stop");
        String speed = status.playing()
                ? (SpectrumDesktopRunner.tapeTurboEnabled() ? "turbo" : "real")
                : "idle";
        return SpectrumTapeFiles.displayName(sourceLabel)
                + ":" + state
                + ":" + status.currentBlockIndex() + "/" + status.totalBlocks()
                + ":" + speed;
    }

    private void process(Command command) {
        switch (command) {
            case Replace replace -> replaceNow(replace.loadedTape());
            case SelectBlock select -> selectBlockNow(select.zeroBasedBlockIndex());
            case Action action -> process(action);
        }
    }

    private void process(Action action) {
        switch (action) {
            case TOGGLE_PLAYBACK -> {
                cancelAutomation.run();
                if (tape.isLoaded()) {
                    if (tape.isPlaying()) {
                        tape.stop();
                    } else {
                        tape.play();
                    }
                }
            }
            case STOP -> {
                cancelAutomation.run();
                tape.stop();
            }
            case REWIND -> {
                cancelAutomation.run();
                tape.rewind();
            }
            case EJECT -> {
                cancelAutomation.run();
                tape.eject();
                sourceLabel = null;
            }
            case NEXT_BLOCK -> moveBlock(1);
            case PREVIOUS_BLOCK -> moveBlock(-1);
        }
    }

    private void replaceNow(SpectrumTapeFiles.LoadedTape loadedTape) {
        boolean resumePlayback = tape.isPlaying();
        cancelAutomation.run();
        tape.load(loadedTape.tapeFile());
        sourceLabel = loadedTape.source().toString();
        if (resumePlayback) {
            tape.play();
        }
    }

    private void moveBlock(int direction) {
        cancelAutomation.run();
        int totalBlocks = tape.totalBlocks();
        if (totalBlocks == 0) {
            return;
        }

        int position = tape.blockPosition();
        if (direction > 0) {
            if (position >= totalBlocks - 1) {
                return;
            }
            tape.selectBlock(position + 1);
            return;
        }

        if (position <= 0) {
            return;
        }
        tape.selectBlock(Math.min(position - 1, totalBlocks - 1));
    }

    private void selectBlockNow(int zeroBasedBlockIndex) {
        cancelAutomation.run();
        tape.selectBlock(zeroBasedBlockIndex);
    }

    private sealed interface Command permits Action, Replace, SelectBlock {
    }

    private enum Action implements Command {
        TOGGLE_PLAYBACK,
        STOP,
        REWIND,
        EJECT,
        NEXT_BLOCK,
        PREVIOUS_BLOCK
    }

    private record Replace(SpectrumTapeFiles.LoadedTape loadedTape) implements Command {
    }

    private record SelectBlock(int zeroBasedBlockIndex) implements Command {
    }
}
