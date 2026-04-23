package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import dev.z8emu.machine.spectrum48k.device.TapeDevice;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.machine.spectrum48k.tape.TapeBlock;
import dev.z8emu.machine.spectrum48k.tape.TapeLoaders;
import dev.z8emu.platform.video.FrameBuffer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class SpectrumTapeProbeLauncher {
    private static final long DEFAULT_MAX_TSTATES = 2_000_000_000L;
    private static final boolean NUDGE_AUTOSTART_WAIT =
            Boolean.getBoolean("z8emu.probeNudgeAutostartWait");
    private static final boolean FORCE_RUN_AUTOSTART_WAIT =
            Boolean.getBoolean("z8emu.probeForceRunAutostartWait");
    private static final int MENU_PRESS_FRAMES =
            Integer.getInteger("z8emu.probeMenuPressFrames", 12);
    private static final int PLAY_DELAY_FRAMES =
            Integer.getInteger("z8emu.probePlayDelayFrames", 0);
    private static final int[] BREAK_PCS = parseAddressList(System.getProperty("z8emu.probeBreakPcs"));
    private static final boolean EXIT_ON_BREAK =
            Boolean.parseBoolean(System.getProperty("z8emu.probeExitOnBreak", "false"));
    private static final long BREAK_AFTER_TSTATE =
            Long.getLong("z8emu.probeBreakAfterTState", Long.MIN_VALUE);
    private static final int[] WATCH_ADDRESSES = parseAddressList(System.getProperty("z8emu.probeWatchAddrs"));
    private static final int PRE_TRACE_FROM_PC = parseAddress(System.getProperty("z8emu.probePreTraceFromPc"), -1);
    private static final int PRE_TRACE_TO_PC = parseAddress(System.getProperty("z8emu.probePreTraceToPc"), -1);
    private static final int TRACE_FROM_PC = parseAddress(System.getProperty("z8emu.probeTraceFromPc"), -1);
    private static final int TRACE_TO_PC = parseAddress(System.getProperty("z8emu.probeTraceToPc"), -1);
    private static final long TRACE_AFTER_TSTATE =
            Long.getLong("z8emu.probeTraceAfterTState", Long.MIN_VALUE);
    private static final int[][] START_POKES = parsePokes(System.getProperty("z8emu.probePokes"));
    private static final int[][] STICKY_POKES = parsePokes(System.getProperty("z8emu.probeStickyPokes"));
    private static final int[] WRITE_WATCH_ADDRESSES = parseAddressList(System.getProperty("z8emu.probeWriteWatchAddrs"));
    private static final int DUMP_FROM = parseAddress(System.getProperty("z8emu.probeDumpFrom"), -1);
    private static final int DUMP_LENGTH = Integer.getInteger("z8emu.probeDumpLength", 0);
    private static final int BLOCK_SUMMARY_LIMIT = Integer.getInteger("z8emu.probeBlockSummaryLimit", 0);
    private static final String NEXT_TAPE_PATH = System.getProperty("z8emu.probeNextTape");
    private static final boolean NEXT_TAPE_IMMEDIATE_PLAY =
            Boolean.parseBoolean(System.getProperty("z8emu.probeNextTapeImmediatePlay", "true"));
    private static final long NEXT_TAPE_DELAY_TSTATES =
            Long.getLong("z8emu.probeNextTapeDelayTStates", 0L);
    private static final int[] EOF_KEY = parseKeySpec(System.getProperty("z8emu.probeEofKey"));
    private static final long POST_EOF_TSTATES =
            Long.getLong("z8emu.probePostEofTStates", 0L);
    private static final long[] DEFAULT_MILESTONES = {
            20_000_000L,
            80_000_000L,
            150_000_000L,
            220_000_000L,
            300_000_000L,
            600_000_000L,
            1_000_000_000L,
            1_500_000_000L
    };

    private SpectrumTapeProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: SpectrumTapeProbeLauncher <rom-path> <tape-path> [output-dir]");
            System.exit(2);
        }

        Path romPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path tapePath = Path.of(args[1]).toAbsolutePath().normalize();
        Path outputDir = args.length == 3
                ? Path.of(args[2]).toAbsolutePath().normalize()
                : Path.of("/tmp/z8-emu-spectrum-probe");
        Files.createDirectories(outputDir);

        SpectrumMachine machine = createMachine(Files.readAllBytes(romPath), romPath);
        machine.board().tape().load(TapeLoaders.load(tapePath));
        installWriteWatch(machine);

        long[] milestones = parseMilestones(System.getProperty("z8emu.probeMilestones"));
        long maxTStates = Long.getLong("z8emu.probeMaxTStates", DEFAULT_MAX_TSTATES);

        runMenuToTapeLoaderScenario(machine);
        applyPokes(machine, START_POKES);

        List<String> summary = new ArrayList<>();
        appendBlockSummary(machine, summary);
        int milestoneIndex = 0;
        boolean autostartWaitNudged = false;
        boolean autostartRunForced = false;
        boolean nextTapeLoaded = false;
        SpectrumAutostartRunRescue autostartRunRescue = new SpectrumAutostartRunRescue();
        boolean breakTriggered = false;
        int[] watchedValues = snapshotWatchValues(machine);
        long eofReachedAt = -1;
        while (machine.currentTState() < maxTStates
                && shouldContinue(machine, nextTapeLoaded, eofReachedAt)) {
            appendPcTrace(machine, summary, true);
            applyPokes(machine, STICKY_POKES);
            if (shouldLoadNextTape(machine, nextTapeLoaded, eofReachedAt)) {
                loadNextTape(machine, summary);
                nextTapeLoaded = true;
                eofReachedAt = -1;
            }
            if (!autostartRunForced && shouldForceRunAutostartWait(machine)) {
                machine.cpu().registers().setPc(0x1EA1);
                autostartRunForced = true;
            }
            if (!autostartWaitNudged && shouldNudgeAutostartWait(machine)) {
                press(machine, 6, 0, 3);
                autostartWaitNudged = true;
            }
            autostartRunRescue.tick(machine);
            machine.runInstruction();
            appendPcTrace(machine, summary, false);
            if (WATCH_ADDRESSES.length > 0) {
                appendWatchChanges(machine, watchedValues, summary);
            }
            if (machine.board().tape().isAtEnd() && eofReachedAt < 0) {
                eofReachedAt = machine.currentTState();
            }
            if (!breakTriggered && shouldBreak(machine)) {
                String dump = dumpState(machine);
                summary.add(dump);
                System.out.println(dump);
                if (DUMP_FROM >= 0 && DUMP_LENGTH > 0) {
                    String memoryDump = dumpMemory(machine, DUMP_FROM, DUMP_LENGTH);
                    summary.add(memoryDump);
                    System.out.println(memoryDump);
                }
                breakTriggered = true;
                if (EXIT_ON_BREAK) {
                    break;
                }
            }
            while (milestoneIndex < milestones.length && machine.currentTState() >= milestones[milestoneIndex]) {
                summary.add(saveSnapshot(machine, outputDir, "t" + milestones[milestoneIndex]));
                milestoneIndex++;
            }
        }

        if (machine.board().tape().isAtEnd() && EOF_KEY != null) {
            press(machine, EOF_KEY[0], EOF_KEY[1], EOF_KEY[2]);
            summary.add(saveSnapshot(machine, outputDir, "after-eof-key"));
        }
        summary.add(saveSnapshot(machine, outputDir, machine.board().tape().isAtEnd() ? "eof" : "max"));
        Files.write(outputDir.resolve("summary.txt"), summary);

        for (String line : summary) {
            System.out.println(line);
        }
        System.out.println("outputDir=" + outputDir);
    }

    private static SpectrumMachine createMachine(byte[] romImage, Path romPath) {
        if (romImage.length == Spectrum128Machine.ROM_IMAGE_SIZE) {
            return new Spectrum128Machine(romImage);
        }
        if (romImage.length == Spectrum48kMemoryMap.ROM_SIZE) {
            return new Spectrum48kMachine(romImage);
        }
        throw new IllegalArgumentException("Unsupported Spectrum ROM size for " + romPath + ": " + romImage.length);
    }

    private static void runMenuToTapeLoaderScenario(SpectrumMachine machine) {
        waitForPc(machine, 0x3685, 20_000_000L);
        press(machine, 6, 0, MENU_PRESS_FRAMES);
        if (PLAY_DELAY_FRAMES > 0) {
            runFrames(machine, PLAY_DELAY_FRAMES);
        }
        SpectrumTapeAutostartSupport.waitForLoaderReadyForPlayback(machine, machine.currentTState() + 20_000_000L);
        machine.board().tape().play();
    }

    private static void waitForPc(SpectrumMachine machine, int targetPc, long maxTStates) {
        while (machine.currentTState() < maxTStates) {
            if (machine.cpu().registers().pc() == targetPc) {
                return;
            }
            machine.runInstruction();
        }
        throw new IllegalStateException("Timed out waiting for PC=0x" + hex16(targetPc));
    }

    private static void press(SpectrumMachine machine, int row, int column, int frames) {
        machine.board().keyboard().setKeyPressed(row, column, true);
        runFrames(machine, frames);
        machine.board().keyboard().setKeyPressed(row, column, false);
        runFrames(machine, frames);
    }

    private static void runFrames(SpectrumMachine machine, int frames) {
        long frameTStates = machine.board().modelConfig().frameTStates();
        for (int i = 0; i < frames; i++) {
            long target = machine.currentTState() + frameTStates;
            while (machine.currentTState() < target) {
                machine.runInstruction();
            }
        }
    }

    private static String saveSnapshot(SpectrumMachine machine, Path outputDir, String label) throws IOException {
        FrameBuffer frame = machine.board().renderVideoFrame();
        String fileName = "%s-pc-%s-t-%d-blk-%d-of-%d.png".formatted(
                label,
                hex16(machine.cpu().registers().pc()),
                machine.currentTState(),
                machine.board().tape().currentBlockIndex(),
                machine.board().tape().totalBlocks()
        );
        Path imagePath = outputDir.resolve(fileName);
        writePng(frame, imagePath);

        return "%s pc=0x%s t=%d blk=%d/%d eof=%s rom=%d bank=%d screen=%d tape=%s image=%s".formatted(
                label,
                hex16(machine.cpu().registers().pc()),
                machine.currentTState(),
                machine.board().tape().currentBlockIndex(),
                machine.board().tape().totalBlocks(),
                machine.board().tape().isAtEnd(),
                machine.board().machineState().selectedRomIndex(),
                machine.board().machineState().topRamBankIndex(),
                machine.board().machineState().activeScreenBankIndex(),
                machine.board().tape().isPlaying() ? "play" : (machine.board().tape().isAtEnd() ? "eof" : "stop"),
                imagePath
        );
    }

    private static void writePng(FrameBuffer frame, Path target) throws IOException {
        BufferedImage image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, frame.width(), frame.height(), frame.pixels(), 0, frame.width());
        ImageIO.write(image, "png", target.toFile());
    }

    private static long[] parseMilestones(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MILESTONES.clone();
        }
        String[] parts = raw.split(",");
        long[] milestones = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            milestones[i] = Long.parseLong(parts[i].trim());
        }
        return milestones;
    }

    private static int[] parseAddressList(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[0];
        }
        String[] parts = raw.split(",");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String token = parts[i].trim();
            if (token.startsWith("0x") || token.startsWith("0X")) {
                values[i] = Integer.parseInt(token.substring(2), 16) & 0xFFFF;
            } else {
                values[i] = Integer.decode(token) & 0xFFFF;
            }
        }
        return values;
    }

    private static int parseAddress(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String token = raw.trim();
        if (token.startsWith("0x") || token.startsWith("0X")) {
            return Integer.parseInt(token.substring(2), 16) & 0xFFFF;
        }
        return Integer.decode(token) & 0xFFFF;
    }

    private static int[][] parsePokes(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[0][];
        }
        String[] parts = raw.split(",");
        int[][] pokes = new int[parts.length][2];
        for (int i = 0; i < parts.length; i++) {
            String token = parts[i].trim();
            int separator = token.indexOf('=');
            if (separator < 0) {
                separator = token.indexOf(':');
            }
            if (separator < 0) {
                throw new IllegalArgumentException("Invalid poke token: " + token);
            }
            pokes[i][0] = parseAddress(token.substring(0, separator), 0);
            pokes[i][1] = parseAddress(token.substring(separator + 1), 0) & 0xFF;
        }
        return pokes;
    }

    private static int[] parseKeySpec(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid EOF key spec: " + raw);
        }
        return new int[]{
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
        };
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }

    private static boolean shouldBreak(SpectrumMachine machine) {
        if (BREAK_PCS.length == 0) {
            return false;
        }
        if (machine.currentTState() < BREAK_AFTER_TSTATE) {
            return false;
        }
        int pc = machine.cpu().registers().pc();
        for (int breakPc : BREAK_PCS) {
            if (pc == breakPc) {
                return true;
            }
        }
        return false;
    }

    private static int[] snapshotWatchValues(SpectrumMachine machine) {
        int[] values = new int[WATCH_ADDRESSES.length];
        for (int i = 0; i < WATCH_ADDRESSES.length; i++) {
            values[i] = machine.board().memory().read(WATCH_ADDRESSES[i]);
        }
        return values;
    }

    private static void applyPokes(SpectrumMachine machine, int[][] pokes) {
        for (int[] poke : pokes) {
            machine.board().memory().write(poke[0], poke[1]);
        }
    }

    private static void appendBlockSummary(SpectrumMachine machine, List<String> summary) {
        if (BLOCK_SUMMARY_LIMIT <= 0) {
            return;
        }
        for (int i = 0; i < BLOCK_SUMMARY_LIMIT; i++) {
            TapeBlock block = machine.board().tape().debugBlock(i);
            if (block == null) {
                break;
            }
            String line = "block"
                    + " idx=" + i
                    + " dataLen=" + block.data().length
                    + " prefixPulses=" + block.prefixPulseLengthsTStates().length
                    + " zeroLen=" + block.zeroBitPulseLengthTStates()
                    + " oneLen=" + block.oneBitPulseLengthTStates()
                    + " usedBits=" + block.usedBitsInLastByte()
                    + " pauseMs=" + block.pauseAfterMillis()
                    + " stop=" + block.stopTapeAfterBlock()
                    + " stop48=" + block.stopTapeIf48kMode()
                    + " firstByte=" + (block.data().length == 0 ? "--" : hex8(block.data()[0]));
            summary.add(line);
            System.out.println(line);
        }
    }

    private static void loadNextTape(SpectrumMachine machine, List<String> summary) throws IOException {
        Path nextTapePath = Path.of(NEXT_TAPE_PATH).toAbsolutePath().normalize();
        machine.board().tape().load(TapeLoaders.load(nextTapePath));
        if (NEXT_TAPE_IMMEDIATE_PLAY) {
            machine.board().tape().play();
        }
        String line = "nextTape"
                + " t=" + machine.currentTState()
                + " path=" + nextTapePath
                + " playMode=" + (NEXT_TAPE_IMMEDIATE_PLAY ? "play" : "stop");
        summary.add(line);
        System.out.println(line);
    }

    private static boolean shouldContinue(SpectrumMachine machine, boolean nextTapeLoaded, long eofReachedAt) {
        if (!machine.board().tape().isAtEnd()) {
            return true;
        }
        if (NEXT_TAPE_PATH != null && !NEXT_TAPE_PATH.isBlank() && !nextTapeLoaded) {
            return true;
        }
        if (POST_EOF_TSTATES <= 0 || eofReachedAt < 0) {
            return false;
        }
        return machine.currentTState() < eofReachedAt + POST_EOF_TSTATES;
    }

    private static boolean shouldLoadNextTape(SpectrumMachine machine, boolean nextTapeLoaded, long eofReachedAt) {
        if (nextTapeLoaded || NEXT_TAPE_PATH == null || NEXT_TAPE_PATH.isBlank()) {
            return false;
        }
        if (!machine.board().tape().isAtEnd() || eofReachedAt < 0) {
            return false;
        }
        return machine.currentTState() >= eofReachedAt + NEXT_TAPE_DELAY_TSTATES;
    }

    private static void installWriteWatch(SpectrumMachine machine) {
        if (WRITE_WATCH_ADDRESSES.length == 0) {
            return;
        }
        machine.board().memory().setWriteListener((address, oldValue, newValue) -> {
            for (int watchedAddress : WRITE_WATCH_ADDRESSES) {
                if (watchedAddress != address) {
                    continue;
                }
                System.out.println("write"
                        + " t=" + machine.currentTState()
                        + " pc=0x" + hex16(machine.cpu().registers().pc())
                        + " addr=0x" + hex16(address)
                        + " old=0x" + hex8(oldValue)
                        + " new=0x" + hex8(newValue)
                        + " af=0x" + hex16(machine.cpu().registers().af())
                        + " bc=0x" + hex16(machine.cpu().registers().bc())
                        + " de=0x" + hex16(machine.cpu().registers().de())
                        + " hl=0x" + hex16(machine.cpu().registers().hl())
                        + " ix=0x" + hex16(machine.cpu().registers().ix())
                        + " iy=0x" + hex16(machine.cpu().registers().iy()));
                return;
            }
        });
    }

    private static void appendWatchChanges(SpectrumMachine machine, int[] watchedValues, List<String> summary) {
        for (int i = 0; i < WATCH_ADDRESSES.length; i++) {
            int current = machine.board().memory().read(WATCH_ADDRESSES[i]);
            if (current == watchedValues[i]) {
                continue;
            }
            String line = "watch"
                    + " t=" + machine.currentTState()
                    + " pc=0x" + hex16(machine.cpu().registers().pc())
                    + " addr=0x" + hex16(WATCH_ADDRESSES[i])
                    + " old=0x" + hex8(watchedValues[i])
                    + " new=0x" + hex8(current)
                    + " af=0x" + hex16(machine.cpu().registers().af())
                    + " bc=0x" + hex16(machine.cpu().registers().bc())
                    + " de=0x" + hex16(machine.cpu().registers().de())
                    + " hl=0x" + hex16(machine.cpu().registers().hl())
                    + " ix=0x" + hex16(machine.cpu().registers().ix())
                    + " iy=0x" + hex16(machine.cpu().registers().iy());
            summary.add(line);
            System.out.println(line);
            watchedValues[i] = current;
        }
    }

    private static void appendPcTrace(SpectrumMachine machine, List<String> summary, boolean beforeInstruction) {
        if (machine.currentTState() < TRACE_AFTER_TSTATE) {
            return;
        }
        int from = beforeInstruction ? PRE_TRACE_FROM_PC : TRACE_FROM_PC;
        int to = beforeInstruction ? PRE_TRACE_TO_PC : TRACE_TO_PC;
        if (from < 0 || to < 0) {
            return;
        }
        int pc = machine.cpu().registers().pc();
        if (pc < from || pc > to) {
            return;
        }
        String line = (beforeInstruction ? "pretrace" : "trace")
                + " t=" + machine.currentTState()
                + " pc=0x" + hex16(pc)
                + " op=0x" + hex8(machine.board().memory().read(pc))
                + " af=0x" + hex16(machine.cpu().registers().af())
                + " bc=0x" + hex16(machine.cpu().registers().bc())
                + " de=0x" + hex16(machine.cpu().registers().de())
                + " hl=0x" + hex16(machine.cpu().registers().hl())
                + " ix=0x" + hex16(machine.cpu().registers().ix())
                + " iy=0x" + hex16(machine.cpu().registers().iy())
                + " sp=0x" + hex16(machine.cpu().registers().sp())
                + " lastk=0x" + hex8(machine.board().memory().read(0x5C08))
                + " flags=0x" + hex8(machine.board().memory().read(0x5C3B))
                + " tvflag=0x" + hex8(machine.board().memory().read(0x5C3C))
                + " v5b66=0x" + hex8(machine.board().memory().read(0x5B66))
                + " v5c67=0x" + hex8(machine.board().memory().read(0x5C67))
                + " v5c6b=0x" + hex8(machine.board().memory().read(0x5C6B));
        summary.add(line);
        System.out.println(line);
    }

    private static String dumpState(SpectrumMachine machine) {
        int pc = machine.cpu().registers().pc();
        int newPpc = readWord(machine, 0x5C42);
        int prog = readWord(machine, 0x5C53);
        int nxtlin = readWord(machine, 0x5C55);
        int flags = machine.board().memory().read(0x5C3B);
        int tvflag = machine.board().memory().read(0x5C3C);
        int lastk = machine.board().memory().read(0x5C08);
        int bankm = machine.board().memory().read(0x5B5C);
        int v5b66 = machine.board().memory().read(0x5B66);
        int v5c67 = machine.board().memory().read(0x5C67);
        int v5c6b = machine.board().memory().read(0x5C6B);
        int w5c80 = readWord(machine, 0x5C80);
        int w5c82 = readWord(machine, 0x5C82);
        int w5c84 = readWord(machine, 0x5C84);
        int w5c86 = readWord(machine, 0x5C86);
        int w5c88 = readWord(machine, 0x5C88);
        int w5c8a = readWord(machine, 0x5C8A);
        int sp = machine.cpu().registers().sp();
        int stack0 = readWord(machine, sp);
        int stack1 = readWord(machine, (sp + 2) & 0xFFFF);
        int stack2 = readWord(machine, (sp + 4) & 0xFFFF);
        TapeDevice.TapeDebugSnapshot tapeSnapshot = machine.board().tape().debugSnapshot();

        return "break"
                + " pc=0x" + hex16(pc)
                + " t=" + machine.currentTState()
                + " frame=" + machine.board().ula().frameCounter()
                + " rom=" + machine.board().machineState().selectedRomIndex()
                + " bank=" + machine.board().machineState().topRamBankIndex()
                + " blk=" + machine.board().tape().currentBlockIndex() + "/" + machine.board().tape().totalBlocks()
                + " play=" + machine.board().tape().isPlaying()
                + " ear=" + (machine.board().tape().earHigh() ? 1 : 0)
                + " tapeState=" + tapeSnapshot.state()
                + " tapeByte=" + tapeSnapshot.byteIndex()
                + " tapeBit=" + tapeSnapshot.bitIndex()
                + " tapeHalf=" + tapeSnapshot.halfPulseIndex()
                + " tapePilotRemaining=" + tapeSnapshot.pilotPulsesRemaining()
                + " af=0x" + hex16(machine.cpu().registers().af())
                + " bc=0x" + hex16(machine.cpu().registers().bc())
                + " de=0x" + hex16(machine.cpu().registers().de())
                + " hl=0x" + hex16(machine.cpu().registers().hl())
                + " ix=0x" + hex16(machine.cpu().registers().ix())
                + " iy=0x" + hex16(machine.cpu().registers().iy())
                + " sp=0x" + hex16(sp)
                + " r=0x" + hex16(machine.cpu().registers().r())
                + " flags=0x" + hex16(flags)
                + " tvflag=0x" + hex16(tvflag)
                + " lastk=0x" + hex16(lastk)
                + " v5b66=0x" + hex16(v5b66)
                + " v5c67=0x" + hex16(v5c67)
                + " v5c6b=0x" + hex16(v5c6b)
                + " w5c80=0x" + hex16(w5c80)
                + " w5c82=0x" + hex16(w5c82)
                + " w5c84=0x" + hex16(w5c84)
                + " w5c86=0x" + hex16(w5c86)
                + " w5c88=0x" + hex16(w5c88)
                + " w5c8a=0x" + hex16(w5c8a)
                + " stack0=0x" + hex16(stack0)
                + " stack1=0x" + hex16(stack1)
                + " stack2=0x" + hex16(stack2)
                + " newppc=0x" + hex16(newPpc)
                + " prog=0x" + hex16(prog)
                + " nxtlin=0x" + hex16(nxtlin)
                + " bankm=0x" + hex16(bankm);
    }

    private static int readWord(SpectrumMachine machine, int address) {
        return machine.board().memory().read(address)
                | (machine.board().memory().read((address + 1) & 0xFFFF) << 8);
    }

    private static String dumpMemory(SpectrumMachine machine, int start, int length) {
        StringBuilder builder = new StringBuilder();
        builder.append("memdump");
        for (int offset = 0; offset < length; offset++) {
            int address = (start + offset) & 0xFFFF;
            if ((offset % 16) == 0) {
                builder.append('\n').append(hex16(address)).append(':');
            }
            builder.append(' ').append(hex8(machine.board().memory().read(address)));
        }
        return builder.toString();
    }

    private static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
    }

    private static boolean shouldNudgeAutostartWait(SpectrumMachine machine) {
        if (!NUDGE_AUTOSTART_WAIT) {
            return false;
        }
        return isAutostartEditorWait(machine);
    }

    private static boolean shouldForceRunAutostartWait(SpectrumMachine machine) {
        if (!FORCE_RUN_AUTOSTART_WAIT) {
            return false;
        }
        return isAutostartEditorWait(machine);
    }

    private static boolean isAutostartEditorWait(SpectrumMachine machine) {
        int pc = machine.cpu().registers().pc();
        boolean inAutostartHandoff = pc == 0x25E3
                || pc == 0x25E5
                || pc == 0x366F
                || pc == 0x3683
                || pc == 0x3888;
        if (!inAutostartHandoff) {
            return false;
        }
        int newPpc = machine.board().memory().read(0x5C42) | (machine.board().memory().read(0x5C43) << 8);
        return machine.board().machineState().selectedRomIndex() == 0
                && newPpc == 0
                && machine.board().tape().isPlaying()
                && machine.board().tape().currentBlockIndex() >= 2;
    }
}
