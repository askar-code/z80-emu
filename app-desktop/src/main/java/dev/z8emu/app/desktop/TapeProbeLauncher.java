package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.machine.spectrum48k.tape.TapLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TapeProbeLauncher {
    private TapeProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: TapeProbeLauncher <48.rom> <file.tap> [max-instructions]");
            System.exit(2);
        }

        Path romPath = Path.of(args[0]).toAbsolutePath().normalize();
        byte[] romImage = Files.readAllBytes(romPath);
        if (romImage.length != Spectrum48kMemoryMap.ROM_SIZE) {
            throw new IllegalArgumentException("Spectrum 48K ROM must be exactly 16 KB: " + romPath);
        }

        Path tapePath = Path.of(args[1]).toAbsolutePath().normalize();
        long maxInstructions = args.length == 3 ? Long.parseLong(args[2]) : 5_000_000L;

        Spectrum48kMachine machine = new Spectrum48kMachine(romImage);
        try (var input = Files.newInputStream(tapePath)) {
            machine.board().tape().load(TapLoader.load(input));
        }
        machine.board().tape().play();

        long steps = 0;
        int previousPc = -1;
        long samePcCount = 0;

        while (steps < maxInstructions) {
            machine.runInstruction();
            steps++;

            int pc = machine.cpu().registers().pc();
            if (pc == previousPc) {
                samePcCount++;
            } else {
                previousPc = pc;
                samePcCount = 0;
            }

            if (steps % 100_000 == 0) {
                System.out.println(
                        "steps=" + steps
                                + " pc=0x" + hex16(pc)
                                + " af=0x" + hex16(machine.cpu().registers().af())
                                + " de=0x" + hex16(machine.cpu().registers().de())
                                + " iy=0x" + hex16(machine.cpu().registers().iy())
                                + " tape=" + machine.board().tape().currentBlockIndex() + "/" + machine.board().tape().totalBlocks()
                                + " playing=" + machine.board().tape().isPlaying()
                );
            }

            if (!machine.board().tape().isPlaying()) {
                System.out.println("tape-finished at steps=" + steps);
                printState(machine);
                return;
            }

            if (samePcCount > 200_000) {
                System.out.println("same-pc-loop detected at steps=" + steps);
                printState(machine);
                return;
            }
        }

        System.out.println("max-instructions-reached");
        printState(machine);
    }

    private static void printState(Spectrum48kMachine machine) {
        System.out.println("pc=0x" + hex16(machine.cpu().registers().pc()));
        System.out.println("sp=0x" + hex16(machine.cpu().registers().sp()));
        System.out.println("af=0x" + hex16(machine.cpu().registers().af()));
        System.out.println("bc=0x" + hex16(machine.cpu().registers().bc()));
        System.out.println("de=0x" + hex16(machine.cpu().registers().de()));
        System.out.println("hl=0x" + hex16(machine.cpu().registers().hl()));
        System.out.println("ix=0x" + hex16(machine.cpu().registers().ix()));
        System.out.println("iy=0x" + hex16(machine.cpu().registers().iy()));
        System.out.println("tape=" + machine.board().tape().currentBlockIndex() + "/" + machine.board().tape().totalBlocks());
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }
}
