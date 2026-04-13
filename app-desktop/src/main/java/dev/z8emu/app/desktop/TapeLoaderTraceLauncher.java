package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import dev.z8emu.machine.spectrum48k.tape.TapLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

public final class TapeLoaderTraceLauncher {
    private TapeLoaderTraceLauncher() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: TapeLoaderTraceLauncher <48.rom> <file.tap> [max-instructions]");
            System.exit(2);
        }

        byte[] rom = Files.readAllBytes(Path.of(args[0]).toAbsolutePath().normalize());
        Spectrum48kMachine machine = new Spectrum48kMachine(rom);
        machine.setFastLoadEnabled(false);

        try (var input = Files.newInputStream(Path.of(args[1]).toAbsolutePath().normalize())) {
            machine.board().tape().load(TapLoader.load(input));
        }

        long maxInstructions = args.length == 3 ? Long.parseLong(args[2]) : 40_000_000L;

        Queue<KeyChord> queue = new ArrayDeque<>();
        queue.add(new KeyChord(new int[][]{{1, 0}}, 6)); // A
        queue.add(new KeyChord(new int[][]{{6, 3}}, 6)); // J
        queue.add(new KeyChord(new int[][]{{7, 1}, {5, 0}}, 6)); // "
        queue.add(new KeyChord(new int[][]{{7, 1}, {5, 0}}, 6)); // "
        queue.add(new KeyChord(new int[][]{{6, 0}}, 6)); // Enter

        KeyChord active = null;
        long releaseAtFrame = -1;
        long steps = 0;
        boolean tapeStarted = false;
        int previousBlock = 0;
        long sampleHits = 0;

        while (steps < maxInstructions) {
            long frame = steps / 69_888L;
            if (steps % 69_888L == 0) {
                if (active != null && frame >= releaseAtFrame) {
                    applyChord(machine, active, false);
                    active = null;
                }
                if (active == null && !queue.isEmpty()) {
                    active = queue.poll();
                    applyChord(machine, active, true);
                    releaseAtFrame = frame + active.frames();
                }
                if (!tapeStarted && queue.isEmpty()) {
                    machine.board().tape().play();
                    tapeStarted = true;
                    System.out.println("tape-play frame=" + frame + " pc=" + hex16(machine.cpu().registers().pc()));
                }
            }

            int pcBefore = machine.cpu().registers().pc();
            machine.runInstruction();
            steps++;

            int block = machine.board().tape().currentBlockIndex();
            if (block != previousBlock) {
                var tape = machine.board().tape().debugSnapshot();
                System.out.println(
                        "block-change steps=" + steps
                                + " block=" + block + "/" + tape.totalBlocks()
                                + " pc=" + hex16(machine.cpu().registers().pc())
                                + " state=" + tape.state()
                                + " ear=" + (tape.earHigh() ? 1 : 0)
                );
                previousBlock = block;
            }

            if (pcBefore == 0x0556) {
                System.out.println(
                        "LD_BYTES steps=" + steps
                                + " A=" + hex8(machine.cpu().registers().a())
                                + " DE=" + hex16(machine.cpu().registers().de())
                                + " IX=" + hex16(machine.cpu().registers().ix())
                                + " carry=" + machine.cpu().registers().flagSet(dev.z8emu.cpu.z80.Z80Registers.FLAG_C)
                );
            }

            if (pcBefore == 0x05ED) {
                sampleHits++;
                if (sampleHits % 20000 == 0) {
                    int sampled = machine.board().ula().readPortFe(0x7FFE, machine.board().keyboard(), machine.board().tape());
                    var tape = machine.board().tape().debugSnapshot();
                    System.out.println(
                            "LD_SAMPLE steps=" + steps
                                    + " B=" + hex8(machine.cpu().registers().b())
                                    + " C=" + hex8(machine.cpu().registers().c())
                                    + " port=" + hex8(sampled)
                                    + " ear=" + (tape.earHigh() ? 1 : 0)
                                    + " state=" + tape.state()
                                    + " rem=" + tape.stateRemaining()
                                    + " byte=" + tape.byteIndex()
                                    + " bit=" + tape.bitIndex()
                    );
                }
            }

            if (steps % 5_000_000L == 0) {
                System.out.println(
                        "progress steps=" + steps
                                + " pc=" + hex16(machine.cpu().registers().pc())
                                + " tape=" + machine.board().tape().currentBlockIndex() + "/" + machine.board().tape().totalBlocks()
                                + " eof=" + machine.board().tape().isAtEnd()
                );
            }
        }
    }

    private static void applyChord(Spectrum48kMachine machine, KeyChord chord, boolean pressed) {
        for (int[] key : chord.keys()) {
            machine.board().keyboard().setKeyPressed(key[0], key[1], pressed);
        }
    }

    private static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }

    private record KeyChord(int[][] keys, long frames) {
    }
}
