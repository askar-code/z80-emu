package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RomProbeLauncher {
    private RomProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args.length > 2) {
            System.err.println("Usage: RomProbeLauncher <rom-path> [max-instructions]");
            System.exit(2);
        }

        Path romPath = Path.of(args[0]).toAbsolutePath().normalize();
        byte[] romImage = Files.readAllBytes(romPath);
        if (romImage.length != Spectrum48kMemoryMap.ROM_SIZE) {
            throw new IllegalArgumentException("Spectrum 48K ROM must be exactly 16 KB: " + romPath);
        }

        long maxInstructions = args.length == 2 ? Long.parseLong(args[1]) : 2_000_000L;
        Spectrum48kMachine machine = new Spectrum48kMachine(romImage);

        long steps = 0;
        try {
            while (steps < maxInstructions) {
                machine.runInstruction();
                steps++;
            }

            System.out.println("status=max-instructions-reached");
            printState(machine, steps);
        } catch (Throwable failure) {
            System.out.println("status=failure");
            printState(machine, steps);
            System.out.println("failure=" + failure.getClass().getName() + ": " + failure.getMessage());
            throw failure;
        }
    }

    private static void printState(Spectrum48kMachine machine, long steps) {
        int pc = machine.cpu().registers().pc();
        int opcode = machine.board().memory().read(pc);

        System.out.println("steps=" + steps);
        System.out.println("pc=0x" + hex16(pc));
        System.out.println("opcode=0x" + hex8(opcode));
        System.out.println("sp=0x" + hex16(machine.cpu().registers().sp()));
        System.out.println("af=0x" + hex16(machine.cpu().registers().af()));
        System.out.println("bc=0x" + hex16(machine.cpu().registers().bc()));
        System.out.println("de=0x" + hex16(machine.cpu().registers().de()));
        System.out.println("hl=0x" + hex16(machine.cpu().registers().hl()));
        System.out.println("ix=0x" + hex16(machine.cpu().registers().ix()));
        System.out.println("iy=0x" + hex16(machine.cpu().registers().iy()));
        System.out.println("t=" + machine.currentTState());
    }

    private static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }
}
