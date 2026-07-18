package dev.z8emu.app.desktop;

import dev.z8emu.machine.spectrum.SpectrumMachine;
import dev.z8emu.machine.spectrum128k.Spectrum128Machine;
import dev.z8emu.machine.spectrum48k.Spectrum48kMachine;
import dev.z8emu.machine.spectrum48k.memory.Spectrum48kMemoryMap;
import dev.z8emu.platform.video.FrameBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.z8emu.app.desktop.ProbeOutput.crc32Hex;
import static dev.z8emu.app.desktop.ProbeOutput.frameCrc32;
import static dev.z8emu.app.desktop.ProbeOutput.hex16;
import static dev.z8emu.app.desktop.ProbeOutput.hex8;
import static dev.z8emu.app.desktop.ProbeOutput.writePng;

public final class RomProbeLauncher {
    private static final String USAGE = "Usage: RomProbeLauncher <rom-path> [max-instructions] "
            + "[--stop-pc=<hex>] [--expect-frame-crc=<crc32>] [--dump-frame=<png>]";

    private RomProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        ProbeConfig config;
        try {
            config = parseArgs(args);
        } catch (IllegalArgumentException malformedArguments) {
            System.err.println(USAGE);
            System.err.println("error=" + malformedArguments.getMessage());
            System.exit(2);
            return;
        }

        byte[] romImage = Files.readAllBytes(config.romPath());
        SpectrumMachine machine = createMachine(romImage, config.romPath());

        long steps = 0;
        boolean stopPcReached = config.stopPc() != null
                && machine.cpu().registers().pc() == config.stopPc();
        try {
            while (steps < config.maxInstructions() && !stopPcReached) {
                machine.runInstruction();
                steps++;
                stopPcReached = config.stopPc() != null
                        && machine.cpu().registers().pc() == config.stopPc();
            }

            FrameBuffer frame = machine.board().renderVideoFrame();
            long crc32 = frameCrc32(frame);
            if (config.dumpFrame() != null) {
                Path parent = config.dumpFrame().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                writePng(frame, config.dumpFrame());
            }

            boolean stopExpectationFailed = config.stopPc() != null && !stopPcReached;
            boolean frameExpectationFailed = config.expectedFrameCrc32() != null
                    && crc32 != config.expectedFrameCrc32();
            boolean verified = config.stopPc() != null || config.expectedFrameCrc32() != null;

            System.out.println("status=" + (
                    stopExpectationFailed || frameExpectationFailed
                            ? "failure"
                            : (verified ? "success" : "unverified")
            ));
            printState(machine, config, steps, stopPcReached, crc32);
            if (stopExpectationFailed || frameExpectationFailed) {
                System.exit(1);
            }
        } catch (Throwable failure) {
            System.out.println("status=failure");
            printState(machine, config, steps, stopPcReached, frameCrc32(machine.board().renderVideoFrame()));
            System.out.println("failure=" + failure.getClass().getName() + ": " + failure.getMessage());
            throw failure;
        }
    }

    private static SpectrumMachine createMachine(byte[] romImage, Path romPath) {
        if (romImage.length == Spectrum128Machine.ROM_IMAGE_SIZE) {
            return new Spectrum128Machine(romImage);
        }
        if (romImage.length == Spectrum48kMemoryMap.ROM_SIZE) {
            return new Spectrum48kMachine(romImage);
        }
        throw new IllegalArgumentException(
                "Spectrum ROM must be exactly 16 KB (48K) or 32 KB (128K): " + romPath
        );
    }

    private static ProbeConfig parseArgs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Missing ROM path");
        }

        Path romPath = Path.of(args[0]).toAbsolutePath().normalize();
        long maxInstructions = 2_000_000L;
        Integer stopPc = null;
        Long expectedFrameCrc32 = null;
        Path dumpFrame = null;
        int index = 1;
        if (index < args.length && !args[index].startsWith("--")) {
            maxInstructions = Long.parseLong(args[index++]);
            if (maxInstructions <= 0) {
                throw new IllegalArgumentException("max-instructions must be positive");
            }
        }

        while (index < args.length) {
            String arg = args[index++];
            if (arg.startsWith("--stop-pc=")) {
                stopPc = parseAddress(arg.substring("--stop-pc=".length()));
            } else if (arg.startsWith("--expect-frame-crc=")) {
                expectedFrameCrc32 = parseCrc32(arg.substring("--expect-frame-crc=".length()));
            } else if (arg.startsWith("--dump-frame=")) {
                dumpFrame = Path.of(arg.substring("--dump-frame=".length())).toAbsolutePath().normalize();
            } else {
                throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }

        return new ProbeConfig(romPath, maxInstructions, stopPc, expectedFrameCrc32, dumpFrame);
    }

    private static int parseAddress(String raw) {
        return Integer.decode(raw.trim()) & 0xFFFF;
    }

    private static long parseCrc32(String raw) {
        String normalized = raw.trim().replace("_", "");
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        long value = Long.parseUnsignedLong(normalized, 16);
        if ((value & ~0xFFFF_FFFFL) != 0) {
            throw new IllegalArgumentException("CRC32 out of range: " + raw);
        }
        return value;
    }

    private static void printState(
            SpectrumMachine machine,
            ProbeConfig config,
            long steps,
            boolean stopPcReached,
            long frameCrc32
    ) {
        int pc = machine.cpu().registers().pc();
        int opcode = machine.board().memory().read(pc);

        System.out.println("model=" + machine.board().modelConfig().modelName());
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
        System.out.println("rom=" + machine.board().machineState().selectedRomIndex());
        System.out.println("frameCrc32=0x" + crc32Hex(frameCrc32));
        if (config.stopPc() != null) {
            System.out.println("expectStopPc=0x" + hex16(config.stopPc()));
            System.out.println("expectStopPcFound=" + stopPcReached);
        }
        if (config.expectedFrameCrc32() != null) {
            System.out.println("expectFrameCrc32=0x" + crc32Hex(config.expectedFrameCrc32()));
            System.out.println("expectFrameCrc32Found=" + (frameCrc32 == config.expectedFrameCrc32()));
        }
        if (config.dumpFrame() != null) {
            System.out.println("frameDump=" + config.dumpFrame());
        }
    }

    private record ProbeConfig(
            Path romPath,
            long maxInstructions,
            Integer stopPc,
            Long expectedFrameCrc32,
            Path dumpFrame
    ) {
    }
}
