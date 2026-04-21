package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;

final class SpectrumAutostartRunRescue {
    private static final boolean ENABLED =
            Boolean.getBoolean("z8emu.enableAutostartRunRescue");
    private static final long DELAY_TSTATES =
            Long.getLong("z8emu.autostartRunRescueDelayTstates", 1_000_000L);

    private long waitingSinceTState = Long.MIN_VALUE;
    private boolean rescuedCurrentWait;

    void tick(SpectrumMachine machine) {
        if (!ENABLED) {
            return;
        }
        if (!isAutostartRunPending(machine)) {
            waitingSinceTState = Long.MIN_VALUE;
            rescuedCurrentWait = false;
            return;
        }

        long now = machine.currentTState();
        if (waitingSinceTState == Long.MIN_VALUE) {
            waitingSinceTState = now;
            rescuedCurrentWait = false;
            return;
        }

        if (!rescuedCurrentWait && now - waitingSinceTState >= DELAY_TSTATES) {
            machine.board().memory().write(0x5C08, 0x00);
            machine.cpu().registers().setPc(0x1EA1);
            rescuedCurrentWait = true;
        }
    }

    private boolean isAutostartRunPending(SpectrumMachine machine) {
        if (!machine.board().tape().isPlaying() || machine.board().tape().currentBlockIndex() < 2) {
            return false;
        }
        int pc = machine.cpu().registers().pc();
        if (pc >= 0x4000) {
            return false;
        }

        int newPpc = machine.board().memory().read(0x5C42) | (machine.board().memory().read(0x5C43) << 8);
        if (newPpc != 0) {
            return false;
        }

        int lastKey = machine.board().memory().read(0x5C08);
        return lastKey == 0x0D;
    }
}
