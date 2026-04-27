package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.Apple2Machine;
import dev.z8emu.machine.apple2.Apple2Memory;
import dev.z8emu.machine.apple2.Apple2ModelConfig;
import dev.z8emu.machine.apple2.Apple2VideoDevice;
import dev.z8emu.machine.apple2.disk.Apple2Disk2Controller;
import dev.z8emu.machine.apple2.disk.Apple2Disk2TraceEvent;
import dev.z8emu.machine.apple2.disk.Apple2DosDiskImageLoader;
import dev.z8emu.machine.apple2.disk.Apple2Gcr35Media;
import dev.z8emu.machine.apple2.disk.Apple2ProDosBlockImage;
import dev.z8emu.machine.apple2.disk.Apple2SuperDriveController;
import dev.z8emu.machine.apple2.disk.Apple2SuperDriveTraceEvent;
import dev.z8emu.machine.apple2.disk.Apple2WozDiskImage;
import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.video.FrameBuffer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

public final class Apple2RomProbeLauncher {
    private static final long DEFAULT_MAX_INSTRUCTIONS = 2_000_000L;
    private static final int DEFAULT_KEY_POLL_PC = 0xFD21;

    private Apple2RomProbeLauncher() {
    }

    public static void main(String[] args) throws IOException {
        ProbeConfig config = parseArgs(args);
        if (config == null) {
            System.err.println("Usage: Apple2RomProbeLauncher <system-rom|memory-image> [max-instructions] [--machine=apple2|apple2e] [--disk=<disk.do|disk.dsk|disk.woz>] [--disk2-rom=<disk2.rom>] [--superdrive35-rom=<controller-rom>] [--superdrive35-media=<disk.po>] [--superdrive35-slot=<slot>] [--superdrive35-warmup-tstates=<count>] [--host-warmup-instructions=<count>] [--prodos-boot=<disk.po>] [--prodos-boot-blocks=<disk.po>] [--prodos-boot-load-address=<hex>] [--prodos-boot-entry-address=<hex>] [--prodos-boot-slot=<slot>] [--set-a=<hex>] [--set-x=<hex>] [--keys=<script>] [--expect-screen=<text>] [--expect-frame-crc=<crc32>] [--key-poll-pc=<hex[,hex...]>] [--stop-pc=<hex[,hex...]>] [--watch-addr=<hex[,hex...]>] [--poke-on-pc=<pc>:<addr>=<value>[,<addr>=<value>][;...]] [--profile-pc-callers=<pc[,pc...]>] [--profile-pc-top=<count>] [--trace-io] [--trace-soft-switches] [--trace-disk2] [--trace-superdrive] [--trace-limit=<count>] [--trace-tail] [--dump-frame=<png>]");
            System.exit(2);
            return;
        }

        Path imagePath = config.imagePath();
        byte[] image = Apple2RomImageLoader.load(config.machineKind(), imagePath);
        if (!isSupportedProbeImageSize(config.machineKind(), image.length)) {
            throw new IllegalArgumentException("Apple II image is not supported by %s: %s"
                    .formatted(modelConfigFor(config.machineKind()).modelName(), imagePath));
        }

        Apple2Machine machine = Apple2Machine.fromLaunchImage(modelConfigFor(config.machineKind()), image);
        TraceCollector traceCollector = new TraceCollector(machine, config.traceOptions());
        Apple2SuperDriveController superDrive35Controller = null;
        SuperDrive35MediaLoad superDrive35MediaLoad = null;
        if (config.superDrive35RomPath() != null) {
            superDrive35Controller = machine.installSuperDrive35Controller(
                    config.superDrive35Slot(),
                    readSuperDrive35ControllerRom(config.superDrive35RomPath())
            );
            if (config.superDrive35MediaPath() != null) {
                superDrive35MediaLoad = loadSuperDrive35Media(config.superDrive35MediaPath());
                superDrive35Controller.insertGcr35Media(Apple2Gcr35Media.fromProDosBlockImage(superDrive35MediaLoad.image()));
            }
            traceCollector.install();
            if (config.superDrive35WarmupTStates() > 0) {
                superDrive35Controller.onHostTStatesElapsed(config.superDrive35WarmupTStates());
            }
        } else {
            if (config.superDrive35MediaPath() != null) {
                throw new IllegalArgumentException("--superdrive35-media requires --superdrive35-rom");
            }
            traceCollector.install();
        }
        if (config.hostWarmupInstructions() > 0) {
            traceCollector.pause();
            runHostWarmup(machine, config.hostWarmupInstructions());
            traceCollector.resume();
            if (config.proDosBootBlocksPath() != null) {
                machine.board().cpuBus().writeMemory(0xC006, 0x00);
            }
        }
        ProDosBootLoad proDosBootLoad = loadProDosBoot(
                proDosBootImagePath(config),
                config.proDosBootLoadAddress(),
                config.proDosBootEntryAddress(),
                config.proDosBootSlot(),
                config.proDosBootPath() != null
        );
        if (proDosBootLoad != null) {
            if (superDrive35Controller != null && !proDosBootLoad.shimInstalled() && superDrive35MediaLoad == null) {
                superDrive35Controller.insertGcr35Media(Apple2Gcr35Media.fromProDosBlockImage(proDosBootLoad.image()));
            }
            if (proDosBootLoad.shimInstalled()) {
                machine.installProDosBlockShim(proDosBootLoad.image());
            }
            machine.loadProgram(proDosBootLoad.program(), proDosBootLoad.loadAddress());
            machine.setProgramCounter(proDosBootLoad.entryAddress());
            machine.cpu().registers().setX(proDosBootLoad.slot() << 4);
        }
        applyInitialRegisters(machine, config);
        if (config.disk2RomPath() != null) {
            machine.loadDisk2SlotRom(readDisk2Rom(config.disk2RomPath()));
        }
        if (config.diskPath() != null) {
            insertDisk2Media(machine, config.diskPath());
            if (config.disk2RomPath() != null) {
                machine.bootDiskFromSlot6();
            }
        }
        String keyScript = decodeScript(config.keyScript());
        String expectedScreen = config.expectedScreen() == null ? null : decodeScript(config.expectedScreen());
        int injectedKeys = 0;
        boolean expectationMet = false;
        int stopPc = -1;
        long pokesApplied = 0;
        long[] pcHits = config.profilePcTop() > 0 ? new long[0x10000] : null;
        long[][] pcCallerHits = config.profilePcCallers().length > 0
                ? new long[config.profilePcCallers().length][0x10000]
                : null;
        int lastExecutedPc = -1;
        int lastExecutedOpcode = -1;

        long steps = 0;
        try {
            while (steps < config.maxInstructions()) {
                int pc = machine.cpu().registers().pc();
                if (pcHits != null) {
                    pcHits[pc]++;
                }
                profilePcCaller(machine, pc, config.profilePcCallers(), pcCallerHits);
                pokesApplied += applyPcPokes(machine, pc, config.pcPokes());
                if (isPcMatch(pc, config.stopPcs())) {
                    stopPc = pc;
                    break;
                }
                if (shouldInjectKey(machine, keyScript, injectedKeys, config.keyPollPcs())) {
                    machine.board().keyboard().pressKey(normalizeKey(keyScript.charAt(injectedKeys)));
                    injectedKeys++;
                }

                lastExecutedPc = pc;
                lastExecutedOpcode = machine.board().cpuBus().readMemory(pc);
                machine.runInstruction();
                steps++;

                if (expectedScreen != null && (steps % 100) == 0 && screenContains(machine, expectedScreen)) {
                    expectationMet = true;
                    break;
                }
            }

            traceCollector.pause();
            FrameProbeResult frameResult = renderFrameIfRequested(
                    machine,
                    config.dumpFramePath(),
                    config.expectedFrameCrc32()
            );
            boolean screenExpectationFailed = expectedScreen != null && !expectationMet;
            boolean frameExpectationFailed = frameResult != null
                    && frameResult.expectedCrc32() != null
                    && frameResult.crc32() != frameResult.expectedCrc32();
            System.out.println("status=" + status(
                    expectationMet,
                    stopPc,
                    frameResult,
                    screenExpectationFailed,
                    frameExpectationFailed
            ));
            printState(machine, steps, imagePath, image.length, keyScript.length(), injectedKeys, expectedScreen,
                    config.watchAddrs(), lastExecutedPc, lastExecutedOpcode, config.traceOptions().traceSuperDrive());
            printProDosBootLoad(proDosBootLoad);
            printSuperDrive35MediaLoad(superDrive35MediaLoad);
            printStopPc(stopPc);
            printPokesApplied(pokesApplied);
            printPcProfile(pcHits, config.profilePcTop());
            printPcCallerProfile(pcCallerHits, config.profilePcCallers(), config.profilePcTop());
            traceCollector.print();
            printFrameResult(frameResult);
            if (screenExpectationFailed || frameExpectationFailed) {
                System.exit(1);
            }
        } catch (Throwable failure) {
            traceCollector.pause();
            System.out.println("status=failure");
            printState(machine, steps, imagePath, image.length, keyScript.length(), injectedKeys, expectedScreen,
                    config.watchAddrs(), lastExecutedPc, lastExecutedOpcode, config.traceOptions().traceSuperDrive());
            printProDosBootLoad(proDosBootLoad);
            printSuperDrive35MediaLoad(superDrive35MediaLoad);
            printStopPc(stopPc);
            printPokesApplied(pokesApplied);
            printPcProfile(pcHits, config.profilePcTop());
            printPcCallerProfile(pcCallerHits, config.profilePcCallers(), config.profilePcTop());
            traceCollector.print();
            printFrameResult(renderFrameIfRequested(
                    machine,
                    config.dumpFramePath(),
                    config.expectedFrameCrc32()
            ));
            System.out.println("failure=" + failure.getClass().getName() + ": " + failure.getMessage());
            throw failure;
        }
    }

    private static ProbeConfig parseArgs(String[] args) {
        if (args.length == 0) {
            return null;
        }

        List<String> positional = new ArrayList<>(2);
        String keyScript = "";
        String expectedScreen = null;
        Long expectedFrameCrc32 = null;
        int[] keyPollPcs = new int[]{DEFAULT_KEY_POLL_PC};
        int[] stopPcs = new int[0];
        int[] watchAddrs = new int[0];
        List<PcPoke> pcPokes = List.of();
        int[] profilePcCallers = new int[0];
        int profilePcTop = 0;
        Path dumpFramePath = null;
        Path diskPath = null;
        Path disk2RomPath = null;
        Path superDrive35RomPath = null;
        Path superDrive35MediaPath = null;
        int superDrive35Slot = Apple2SuperDriveController.DEFAULT_SLOT;
        int superDrive35WarmupTStates = 0;
        long hostWarmupInstructions = 0;
        DesktopMachineKind machineKind = DesktopMachineKind.APPLE2;
        Path proDosBootPath = null;
        Path proDosBootBlocksPath = null;
        int proDosBootLoadAddress = 0x0800;
        Integer proDosBootEntryAddress = null;
        int proDosBootSlot = 6;
        Integer initialA = null;
        Integer initialX = null;
        boolean traceIo = false;
        boolean traceSoftSwitches = false;
        boolean traceDisk2 = false;
        boolean traceSuperDrive = false;
        int traceLimit = TraceOptions.DEFAULT_LIMIT;
        boolean traceTail = false;
        for (String arg : args) {
            if (arg.startsWith("--machine=")) {
                machineKind = parseApple2MachineKind(arg.substring("--machine=".length()));
            } else if (arg.startsWith("--keys=")) {
                keyScript = arg.substring("--keys=".length());
            } else if (arg.startsWith("--expect-screen=")) {
                expectedScreen = arg.substring("--expect-screen=".length());
            } else if (arg.startsWith("--expect-frame-crc=")) {
                expectedFrameCrc32 = parseCrc32(arg.substring("--expect-frame-crc=".length()));
            } else if (arg.startsWith("--key-poll-pc=")) {
                keyPollPcs = parseAddresses(arg.substring("--key-poll-pc=".length()));
            } else if (arg.startsWith("--stop-pc=")) {
                stopPcs = parseAddresses(arg.substring("--stop-pc=".length()));
            } else if (arg.startsWith("--watch-addr=")) {
                watchAddrs = parseAddresses(arg.substring("--watch-addr=".length()));
            } else if (arg.startsWith("--poke-on-pc=")) {
                pcPokes = parsePcPokes(arg.substring("--poke-on-pc=".length()));
            } else if (arg.startsWith("--profile-pc-callers=")) {
                profilePcCallers = parseAddresses(arg.substring("--profile-pc-callers=".length()));
            } else if (arg.startsWith("--profile-pc-top=")) {
                profilePcTop = Integer.parseInt(arg.substring("--profile-pc-top=".length()));
                if (profilePcTop < 0) {
                    return null;
                }
            } else if (arg.startsWith("--dump-frame=")) {
                dumpFramePath = Path.of(arg.substring("--dump-frame=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--disk=")) {
                diskPath = Path.of(arg.substring("--disk=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--disk2-rom=")) {
                disk2RomPath = Path.of(arg.substring("--disk2-rom=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--superdrive35-rom=")) {
                superDrive35RomPath = Path.of(arg.substring("--superdrive35-rom=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--superdrive35-media=")) {
                superDrive35MediaPath = Path.of(arg.substring("--superdrive35-media=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--superdrive35-slot=")) {
                superDrive35Slot = Integer.parseInt(arg.substring("--superdrive35-slot=".length()));
                if (superDrive35Slot < 1 || superDrive35Slot > 7) {
                    throw new IllegalArgumentException("Apple II SuperDrive slot must be in range 1..7: " + superDrive35Slot);
                }
            } else if (arg.startsWith("--superdrive35-warmup-tstates=")) {
                superDrive35WarmupTStates = Integer.parseInt(arg.substring("--superdrive35-warmup-tstates=".length()));
                if (superDrive35WarmupTStates < 0) {
                    throw new IllegalArgumentException("SuperDrive warmup t-states must be non-negative: " + superDrive35WarmupTStates);
                }
            } else if (arg.startsWith("--host-warmup-instructions=")) {
                hostWarmupInstructions = Long.parseLong(arg.substring("--host-warmup-instructions=".length()));
                if (hostWarmupInstructions < 0) {
                    throw new IllegalArgumentException("Host warmup instructions must be non-negative: " + hostWarmupInstructions);
                }
            } else if (arg.startsWith("--prodos-boot=")) {
                proDosBootPath = Path.of(arg.substring("--prodos-boot=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--prodos-boot-blocks=")) {
                proDosBootBlocksPath = Path.of(arg.substring("--prodos-boot-blocks=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--prodos-boot-load-address=")) {
                proDosBootLoadAddress = parseAddress(arg.substring("--prodos-boot-load-address=".length()));
            } else if (arg.startsWith("--prodos-boot-entry-address=")) {
                proDosBootEntryAddress = parseAddress(arg.substring("--prodos-boot-entry-address=".length()));
            } else if (arg.startsWith("--prodos-boot-slot=")) {
                proDosBootSlot = Integer.parseInt(arg.substring("--prodos-boot-slot=".length()));
                if (proDosBootSlot < 1 || proDosBootSlot > 7) {
                    throw new IllegalArgumentException("Apple II boot slot must be in range 1..7: " + proDosBootSlot);
                }
            } else if (arg.startsWith("--set-a=")) {
                initialA = parseAddress(arg.substring("--set-a=".length())) & 0xFF;
            } else if (arg.startsWith("--set-x=")) {
                initialX = parseAddress(arg.substring("--set-x=".length())) & 0xFF;
            } else if (arg.equals("--trace-io")) {
                traceIo = true;
            } else if (arg.equals("--trace-soft-switches")) {
                traceSoftSwitches = true;
            } else if (arg.equals("--trace-disk2")) {
                traceDisk2 = true;
            } else if (arg.equals("--trace-superdrive")) {
                traceSuperDrive = true;
            } else if (arg.startsWith("--trace-limit=")) {
                traceLimit = Integer.parseInt(arg.substring("--trace-limit=".length()));
                if (traceLimit < 0) {
                    return null;
                }
            } else if (arg.equals("--trace-tail")) {
                traceTail = true;
            } else if (arg.startsWith("--")) {
                return null;
            } else {
                positional.add(arg);
            }
        }

        if (positional.isEmpty() || positional.size() > 2) {
            return null;
        }
        if (diskPath != null && proDosBootPath != null) {
            throw new IllegalArgumentException("--disk and --prodos-boot cannot be combined in one probe run");
        }
        if (diskPath != null && proDosBootBlocksPath != null) {
            throw new IllegalArgumentException("--disk and --prodos-boot-blocks cannot be combined in one probe run");
        }
        if (proDosBootPath != null && proDosBootBlocksPath != null) {
            throw new IllegalArgumentException("--prodos-boot and --prodos-boot-blocks cannot be combined in one probe run");
        }
        if (superDrive35RomPath != null && (diskPath != null || disk2RomPath != null || proDosBootPath != null)) {
            throw new IllegalArgumentException("--superdrive35-rom cannot be combined with --disk, --disk2-rom, or shimmed --prodos-boot yet");
        }
        if (superDrive35MediaPath != null && superDrive35RomPath == null) {
            throw new IllegalArgumentException("--superdrive35-media requires --superdrive35-rom");
        }
        long maxInstructions = positional.size() == 2
                ? Long.parseLong(positional.get(1))
                : DEFAULT_MAX_INSTRUCTIONS;
        return new ProbeConfig(
                Path.of(positional.get(0)).toAbsolutePath().normalize(),
                maxInstructions,
                keyScript,
                expectedScreen,
                expectedFrameCrc32,
                keyPollPcs,
                stopPcs,
                watchAddrs,
                pcPokes,
                profilePcCallers,
                profilePcTop,
                dumpFramePath,
                diskPath,
                disk2RomPath,
                superDrive35RomPath,
                superDrive35MediaPath,
                superDrive35Slot,
                superDrive35WarmupTStates,
                hostWarmupInstructions,
                machineKind,
                proDosBootPath,
                proDosBootBlocksPath,
                proDosBootLoadAddress,
                proDosBootEntryAddress,
                proDosBootSlot,
                initialA,
                initialX,
                new TraceOptions(traceIo, traceSoftSwitches, traceDisk2, traceSuperDrive, traceLimit, traceTail)
        );
    }

    private static DesktopMachineKind parseApple2MachineKind(String value) {
        DesktopMachineKind kind = DesktopMachineDefinitions.parse(value).kind();
        return switch (kind) {
            case APPLE2, APPLE2E -> kind;
            case SPECTRUM48, SPECTRUM128, RADIO86RK, CPC6128 ->
                    throw new IllegalArgumentException("Apple II probe supports only apple2/apple2e machines: " + value);
        };
    }

    private static Apple2ModelConfig modelConfigFor(DesktopMachineKind kind) {
        return switch (kind) {
            case APPLE2 -> Apple2ModelConfig.appleIIPlus();
            case APPLE2E -> Apple2ModelConfig.appleIIe128K();
            case SPECTRUM48, SPECTRUM128, RADIO86RK, CPC6128 ->
                    throw new IllegalArgumentException("Expected Apple II machine kind: " + kind);
        };
    }

    private static boolean isSupportedProbeImageSize(DesktopMachineKind kind, int length) {
        return modelConfigFor(kind).supportsLaunchImageSize(length);
    }

    private static byte[] readDisk2Rom(Path romPath) throws IOException {
        byte[] rom = Files.readAllBytes(romPath);
        if (rom.length != Apple2Disk2Controller.SLOT_ROM_SIZE) {
            throw new IllegalArgumentException("Disk II slot ROM must be exactly 256 bytes: " + romPath);
        }
        return rom;
    }

    private static byte[] readSuperDrive35ControllerRom(Path romPath) throws IOException {
        byte[] rom = Files.readAllBytes(romPath);
        if (rom.length != Apple2SuperDriveController.CONTROLLER_ROM_SIZE) {
            throw new IllegalArgumentException("SuperDrive controller ROM must be exactly 32 KB: " + romPath);
        }
        return rom;
    }

    private static void insertDisk2Media(Apple2Machine machine, Path diskPath) throws IOException {
        byte[] imageBytes = Files.readAllBytes(diskPath);
        if (Apple2WozDiskImage.hasWoz1Header(imageBytes)) {
            machine.insertDisk(Apple2WozDiskImage.fromWoz1Bytes(imageBytes));
            return;
        }
        machine.insertDisk(Apple2DosDiskImageLoader.load(diskPath));
    }

    private static Path proDosBootImagePath(ProbeConfig config) {
        return config.proDosBootPath() == null ? config.proDosBootBlocksPath() : config.proDosBootPath();
    }

    private static void applyInitialRegisters(Apple2Machine machine, ProbeConfig config) {
        if (config.initialA() != null) {
            machine.cpu().registers().setA(config.initialA());
        }
        if (config.initialX() != null) {
            machine.cpu().registers().setX(config.initialX());
        }
    }

    private static ProDosBootLoad loadProDosBoot(
            Path imagePath,
            int loadAddress,
            Integer configuredEntryAddress,
            int slot,
            boolean shimInstalled
    )
            throws IOException {
        if (imagePath == null) {
            return null;
        }
        Apple2ProDosBlockImage image = Apple2ProDosBlockImage.fromProDosOrderedBytes(Files.readAllBytes(imagePath));
        byte[] block0 = image.readBlock(0);
        byte[] block1 = image.readBlock(1);
        byte[] program = new byte[block0.length + block1.length];
        System.arraycopy(block0, 0, program, 0, block0.length);
        System.arraycopy(block1, 0, program, block0.length, block1.length);
        return new ProDosBootLoad(
                imagePath,
                image,
                image.volumeName(),
                program,
                loadAddress,
                configuredEntryAddress == null ? ((loadAddress + 1) & 0xFFFF) : configuredEntryAddress,
                slot,
                crc32(program),
                shimInstalled
        );
    }

    private static SuperDrive35MediaLoad loadSuperDrive35Media(Path imagePath) throws IOException {
        Apple2ProDosBlockImage image = Apple2ProDosBlockImage.fromProDosOrderedBytes(Files.readAllBytes(imagePath));
        return new SuperDrive35MediaLoad(imagePath, image, image.volumeName());
    }

    private static void runHostWarmup(Apple2Machine machine, long instructions) {
        for (long i = 0; i < instructions; i++) {
            machine.runInstruction();
        }
    }

    private static int parseAddress(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        return Integer.parseInt(normalized, 16) & 0xFFFF;
    }

    private static long parseCrc32(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        long parsed = Long.parseUnsignedLong(normalized, 16);
        if ((parsed & ~0xFFFF_FFFFL) != 0) {
            throw new IllegalArgumentException("CRC32 value out of range: " + value);
        }
        return parsed;
    }

    private static int[] parseAddresses(String value) {
        String[] parts = value.split(",");
        int[] addresses = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            addresses[i] = parseAddress(parts[i]);
        }
        return addresses;
    }

    private static List<PcPoke> parsePcPokes(String value) {
        List<PcPoke> pokes = new ArrayList<>();
        for (String entry : value.split(";")) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid --poke-on-pc entry: " + entry);
            }
            int pc = parseAddress(parts[0]);
            String[] assignments = parts[1].split(",");
            int[] addresses = new int[assignments.length];
            int[] values = new int[assignments.length];
            for (int i = 0; i < assignments.length; i++) {
                String[] assignment = assignments[i].split("=", 2);
                if (assignment.length != 2) {
                    throw new IllegalArgumentException("Invalid --poke-on-pc assignment: " + assignments[i]);
                }
                addresses[i] = parseAddress(assignment[0]);
                values[i] = parseAddress(assignment[1]) & 0xFF;
            }
            pokes.add(new PcPoke(pc, addresses, values));
        }
        return pokes;
    }

    private static long applyPcPokes(Apple2Machine machine, int pc, List<PcPoke> pcPokes) {
        long applied = 0;
        for (PcPoke pcPoke : pcPokes) {
            if (pcPoke.pc() == pc) {
                for (int i = 0; i < pcPoke.addresses().length; i++) {
                    machine.board().cpuBus().writeMemory(pcPoke.addresses()[i], pcPoke.values()[i]);
                    applied++;
                }
            }
        }
        return applied;
    }

    private static void profilePcCaller(Apple2Machine machine, int pc, int[] targetPcs, long[][] callerHits) {
        if (callerHits == null) {
            return;
        }
        for (int i = 0; i < targetPcs.length; i++) {
            if (pc == targetPcs[i]) {
                callerHits[i][stackReturnAddress(machine)]++;
            }
        }
    }

    private static int stackReturnAddress(Apple2Machine machine) {
        int sp = machine.cpu().registers().sp();
        int low = machine.board().cpuBus().readMemory(0x0100 | ((sp + 1) & 0xFF));
        int high = machine.board().cpuBus().readMemory(0x0100 | ((sp + 2) & 0xFF));
        return ((high << 8) | low) & 0xFFFF;
    }

    private static boolean shouldInjectKey(Apple2Machine machine, String keyScript, int injectedKeys, int[] keyPollPcs) {
        return injectedKeys < keyScript.length()
                && !machine.board().keyboard().strobe()
                && isPcMatch(machine.cpu().registers().pc(), keyPollPcs);
    }

    private static boolean isPcMatch(int pc, int[] pcs) {
        for (int expectedPc : pcs) {
            if (pc == expectedPc) {
                return true;
            }
        }
        return false;
    }

    private static String status(
            boolean expectationMet,
            int stopPc,
            FrameProbeResult frameResult,
            boolean screenExpectationFailed,
            boolean frameExpectationFailed
    ) {
        if (screenExpectationFailed) {
            return stopPc >= 0 ? "stop-pc-reached-expectation-not-met" : "expectation-not-met";
        }
        if (frameExpectationFailed) {
            return stopPc >= 0 ? "stop-pc-reached-frame-expectation-not-met" : "frame-expectation-not-met";
        }
        if (expectationMet) {
            return "expectation-met";
        }
        if (frameResult != null && frameResult.expectedCrc32() != null) {
            return "frame-expectation-met";
        }
        if (stopPc >= 0) {
            return "stop-pc-reached";
        }
        return "max-instructions-reached";
    }

    private static int normalizeKey(char key) {
        if (key >= 'a' && key <= 'z') {
            return Character.toUpperCase(key) & 0x7F;
        }
        return key & 0x7F;
    }

    private static String decodeScript(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '<') {
                int end = value.indexOf('>', i + 1);
                if (end > i) {
                    String token = value.substring(i + 1, end).toUpperCase(Locale.ROOT);
                    Character tokenCharacter = decodeAngleToken(token);
                    if (tokenCharacter != null) {
                        decoded.append(tokenCharacter);
                        i = end;
                        continue;
                    }
                }
            }
            if (character == '\\' && i + 1 < value.length()) {
                char escaped = value.charAt(++i);
                switch (escaped) {
                    case 'r' -> decoded.append('\r');
                    case 'n' -> decoded.append('\n');
                    case 't' -> decoded.append('\t');
                    case '\\' -> decoded.append('\\');
                    case 'x' -> {
                        if (i + 2 >= value.length()) {
                            throw new IllegalArgumentException("Incomplete hex escape in key script");
                        }
                        String hex = value.substring(i + 1, i + 3);
                        decoded.append((char) Integer.parseInt(hex, 16));
                        i += 2;
                    }
                    default -> decoded.append(escaped);
                }
            } else {
                decoded.append(character);
            }
        }
        return decoded.toString();
    }

    private static Character decodeAngleToken(String token) {
        return switch (token) {
            case "CR", "ENTER", "RETURN" -> '\r';
            case "LF", "NL", "NEWLINE" -> '\n';
            case "SP", "SPACE" -> ' ';
            case "TAB" -> '\t';
            case "ESC", "ESCAPE" -> 0x1B;
            case "BS", "BACKSPACE", "LEFT" -> 0x08;
            case "RIGHT" -> 0x15;
            default -> null;
        };
    }

    private static void printState(
            Apple2Machine machine,
            long steps,
            Path imagePath,
            int imageLength,
            int keyCount,
            int injectedKeys,
            String expectedScreen,
            int[] watchAddrs,
            int lastExecutedPc,
            int lastExecutedOpcode,
            boolean dumpSuperDriveWindows
    ) {
        int pc = machine.cpu().registers().pc();
        int opcode = machine.board().cpuBus().readMemory(pc);
        String[] visibleLines = visibleLines(machine);
        String visibleText = String.join("\n", visibleLines);

        System.out.println("source=" + imagePath);
        System.out.println("imageBytes=" + imageLength);
        System.out.println("steps=" + steps);
        System.out.println("pc=0x" + hex16(pc));
        System.out.println("opcode=0x" + hex8(opcode));
        if (lastExecutedPc >= 0) {
            System.out.println("lastPc=0x" + hex16(lastExecutedPc));
            System.out.println("lastOpcode=0x" + hex8(lastExecutedOpcode));
        }
        System.out.println("a=0x" + hex8(machine.cpu().registers().a()));
        System.out.println("x=0x" + hex8(machine.cpu().registers().x()));
        System.out.println("y=0x" + hex8(machine.cpu().registers().y()));
        System.out.println("sp=0x" + hex8(machine.cpu().registers().sp()));
        System.out.println("p=0x" + hex8(machine.cpu().registers().p()));
        System.out.println("t=" + machine.currentTState());
        System.out.println("textPage=" + (machine.board().softSwitches().page2() ? 2 : 1));
        System.out.println("textMode=" + machine.board().softSwitches().textMode());
        System.out.println("key=0x" + hex8(machine.board().keyboard().readData()));
        System.out.println("keysInjected=" + injectedKeys + "/" + keyCount);
        if (expectedScreen != null) {
            System.out.println("expectScreen=" + printable(expectedScreen));
            System.out.println("expectScreenFound=" + visibleText.contains(expectedScreen));
        }
        System.out.println("speaker=" + (machine.board().speaker().high() ? 1 : 0));
        printSuperDrive35State(machine, dumpSuperDriveWindows);
        System.out.println("visibleChars=" + countVisibleCharacters(visibleLines));
        System.out.println("visibleCrc32=0x" + crc32Hex(visibleText.getBytes(StandardCharsets.US_ASCII)));
        System.out.println("screen:");
        for (int row = 0; row < visibleLines.length; row++) {
            System.out.println("%02d|%s".formatted(row, visibleLines[row]));
        }

        System.out.println("bytesAroundPc:");
        for (int address = pc - 16; address <= pc + 16; address++) {
            int normalized = address & 0xFFFF;
            System.out.println(hex16(normalized) + ": " + hex8(machine.board().cpuBus().readMemory(normalized)));
        }

        if (watchAddrs.length > 0) {
            System.out.println("watchedBytes:");
            for (int address : watchAddrs) {
                System.out.println(hex16(address) + ": " + hex8(machine.board().cpuBus().readMemory(address)));
            }
        }
    }

    private static void printSuperDrive35State(Apple2Machine machine, boolean dumpWindows) {
        Apple2SuperDriveController controller = machine.board().superDrive35Controller();
        if (controller == null) {
            return;
        }
        System.out.println("superdrive35=installed");
        System.out.println("superdrive35Slot=" + controller.slot());
        System.out.println("superdrive35ControllerRom=loaded");
        System.out.println("superdrive35BankSelect=0x" + hex8(controller.bankSelect()));
        System.out.println("superdrive35Windows=cnxx:controller-ram-7b00-readonly c800:shared-ram");
        System.out.println("superdrive35CpuPc=0x" + hex16(controller.controllerCpuPc()));
        System.out.println("superdrive35CpuInstructions=" + controller.controllerInstructions());
        System.out.println("superdrive35Track=" + controller.selectedTrack());
        System.out.println("superdrive35Side=" + controller.selectedSide());
        System.out.println("superdrive35DiskInserted=" + (controller.diskInserted() ? 1 : 0));
        System.out.println("superdrive35Led=" + (controller.diagnosticLedOn() ? 1 : 0));
        System.out.println("superdrive35SwimDataReady=" + (controller.swimDataReady() ? 1 : 0));
        System.out.println("superdrive35Swim=data:0x%s handshake:0x%s status:0x%s mode:0x%s".formatted(
                hex8(controller.controllerIoByte(0x00)),
                hex8(controller.controllerIoByte(0x0C)),
                hex8(controller.controllerIoByte(0x0E)),
                hex8(controller.controllerIoByte(0x0F))
        ));
        if (controller.controllerFault() != null) {
            System.out.println("superdrive35CpuFault=" + printable(controller.controllerFault()));
        }
        if (dumpWindows) {
            System.out.println("superdrive35Ram0000=" + controllerRamBytes(controller, 0x0000, 16));
            System.out.println("superdrive35Ram0010=" + controllerRamBytes(controller, 0x0010, 32));
            System.out.println("superdrive35Ram0050=" + controllerRamBytes(controller, 0x0050, 16));
            System.out.println("superdrive35CnxxC500=" + controllerRamBytes(controller, Apple2SuperDriveController.CNXX_RAM_OFFSET, 16));
            System.out.println("superdrive35C800BankWindow=" + controllerRamBytes(
                    controller,
                    controller.bankSelect() * Apple2SuperDriveController.C800_BANK_SIZE,
                    16
            ));
            System.out.println("superdrive35C800FixedWindow=" + controllerRamBytes(
                    controller,
                    Apple2SuperDriveController.C800_FIXED_RAM_OFFSET,
                    16
            ));
        }
    }

    private static String controllerRamBytes(Apple2SuperDriveController controller, int startAddress, int length) {
        StringBuilder bytes = new StringBuilder(length * 3);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                bytes.append(' ');
            }
            bytes.append(hex8(controller.controllerRamByte(startAddress + i)));
        }
        return bytes.toString();
    }

    private static void printProDosBootLoad(ProDosBootLoad bootLoad) {
        if (bootLoad == null) {
            return;
        }
        System.out.println("proDosBootImage=" + bootLoad.imagePath());
        System.out.println("proDosBootMode=" + (bootLoad.shimInstalled() ? "shim" : "blocks-only"));
        System.out.println("proDosBootVolume=/" + bootLoad.volumeName());
        System.out.println("proDosBootBytes=" + bootLoad.program().length);
        System.out.println("proDosBootLoadAddress=0x" + hex16(bootLoad.loadAddress()));
        System.out.println("proDosBootEntryAddress=0x" + hex16(bootLoad.entryAddress()));
        System.out.println("proDosBootSlot=" + bootLoad.slot());
        System.out.println("proDosBootCrc32=0x" + crc32Hex(bootLoad.crc32()));
    }

    private static void printSuperDrive35MediaLoad(SuperDrive35MediaLoad mediaLoad) {
        if (mediaLoad == null) {
            return;
        }
        System.out.println("superdrive35MediaImage=" + mediaLoad.imagePath());
        System.out.println("superdrive35MediaVolume=/" + mediaLoad.volumeName());
    }

    private static void printStopPc(int stopPc) {
        if (stopPc >= 0) {
            System.out.println("stopPc=0x" + hex16(stopPc));
        }
    }

    private static void printPokesApplied(long pokesApplied) {
        if (pokesApplied > 0) {
            System.out.println("pokesApplied=" + pokesApplied);
        }
    }

    private static void printPcProfile(long[] pcHits, int topCount) {
        if (pcHits == null || topCount <= 0) {
            return;
        }
        int printed = 0;
        System.out.println("pcProfileTop:");
        while (printed < topCount) {
            int bestPc = -1;
            long bestHits = 0;
            for (int pc = 0; pc < pcHits.length; pc++) {
                long hits = pcHits[pc];
                if (hits > bestHits) {
                    bestHits = hits;
                    bestPc = pc;
                }
            }
            if (bestPc < 0) {
                break;
            }
            System.out.println("%02d|pc=0x%s hits=%d".formatted(printed + 1, hex16(bestPc), bestHits));
            pcHits[bestPc] = 0;
            printed++;
        }
    }

    private static void printPcCallerProfile(long[][] callerHits, int[] targetPcs, int topCount) {
        if (callerHits == null) {
            return;
        }
        int callersToPrint = topCount > 0 ? topCount : 12;
        for (int targetIndex = 0; targetIndex < targetPcs.length; targetIndex++) {
            System.out.println("pcCallerProfile target=0x" + hex16(targetPcs[targetIndex]) + ":");
            long[] hits = callerHits[targetIndex];
            for (int printed = 0; printed < callersToPrint; printed++) {
                int bestReturn = -1;
                long bestHits = 0;
                for (int returnAddress = 0; returnAddress < hits.length; returnAddress++) {
                    if (hits[returnAddress] > bestHits) {
                        bestHits = hits[returnAddress];
                        bestReturn = returnAddress;
                    }
                }
                if (bestReturn < 0) {
                    break;
                }
                int callerResume = (bestReturn + 1) & 0xFFFF;
                System.out.println("%02d|return=0x%s resume=0x%s hits=%d"
                        .formatted(printed + 1, hex16(bestReturn), hex16(callerResume), bestHits));
                hits[bestReturn] = 0;
            }
        }
    }

    private static FrameProbeResult renderFrameIfRequested(
            Apple2Machine machine,
            Path dumpFramePath,
            Long expectedFrameCrc32
    ) throws IOException {
        if (dumpFramePath == null && expectedFrameCrc32 == null) {
            return null;
        }
        FrameBuffer frame = machine.board().renderVideoFrame();
        if (dumpFramePath != null) {
            Path parent = dumpFramePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writePng(frame, dumpFramePath);
        }
        return new FrameProbeResult(
                dumpFramePath,
                frame.width(),
                frame.height(),
                frameCrc32(frame),
                expectedFrameCrc32
        );
    }

    private static void printFrameResult(FrameProbeResult frameResult) {
        if (frameResult == null) {
            return;
        }
        if (frameResult.dumpPath() != null) {
            System.out.println("frameDump=" + frameResult.dumpPath());
        }
        System.out.println("frameSize=" + frameResult.width() + "x" + frameResult.height());
        System.out.println("frameCrc32=0x" + crc32Hex(frameResult.crc32()));
        if (frameResult.expectedCrc32() != null) {
            System.out.println("expectFrameCrc32=0x" + crc32Hex(frameResult.expectedCrc32()));
            System.out.println("expectFrameCrc32Found=" + (frameResult.crc32() == frameResult.expectedCrc32()));
        }
    }

    private static void writePng(FrameBuffer frame, Path target) throws IOException {
        BufferedImage image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, frame.width(), frame.height(), frame.pixels(), 0, frame.width());
        ImageIO.write(image, "png", target.toFile());
    }

    private static boolean screenContains(Apple2Machine machine, String expectedScreen) {
        return String.join("\n", visibleLines(machine)).contains(expectedScreen);
    }

    private static String[] visibleLines(Apple2Machine machine) {
        String[] lines = new String[Apple2VideoDevice.TEXT_ROWS];
        int textPageBase = machine.board().softSwitches().page2()
                ? Apple2Memory.TEXT_PAGE_2_START
                : Apple2Memory.TEXT_PAGE_1_START;
        for (int row = 0; row < lines.length; row++) {
            StringBuilder line = new StringBuilder(Apple2VideoDevice.TEXT_COLUMNS);
            for (int column = 0; column < Apple2VideoDevice.TEXT_COLUMNS; column++) {
                int address = Apple2Memory.textPageAddress(textPageBase, row, column);
                line.append(renderCharacter(machine.board().memory().read(address)));
            }
            lines[row] = line.toString();
        }
        return lines;
    }

    private static char renderCharacter(int screenCode) {
        int normalized = screenCode & 0x3F;
        if (normalized < 0x20) {
            normalized += 0x40;
        }
        if (normalized >= 0x20 && normalized <= 0x7E) {
            return (char) normalized;
        }
        return '.';
    }

    private static int countVisibleCharacters(String[] visibleLines) {
        int count = 0;
        for (String line : visibleLines) {
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) != ' ') {
                    count++;
                }
            }
        }
        return count;
    }

    private static String crc32Hex(byte[] data) {
        return "%08X".formatted(crc32(data));
    }

    private static long crc32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, data.length);
        return crc32.getValue();
    }

    private static String crc32Hex(long value) {
        return "%08X".formatted(value & 0xFFFF_FFFFL);
    }

    private static long frameCrc32(FrameBuffer frame) {
        CRC32 crc32 = new CRC32();
        for (int pixel : frame.pixels()) {
            crc32.update((pixel >>> 24) & 0xFF);
            crc32.update((pixel >>> 16) & 0xFF);
            crc32.update((pixel >>> 8) & 0xFF);
            crc32.update(pixel & 0xFF);
        }
        return crc32.getValue();
    }

    private static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }

    private static String printable(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static final class TraceCollector {
        private final Apple2Machine machine;
        private final TraceOptions options;
        private final List<String> events = new ArrayList<>();
        private long dropped;
        private int nextTailIndex;
        private boolean recording = true;

        private TraceCollector(Apple2Machine machine, TraceOptions options) {
            this.machine = machine;
            this.options = options;
        }

        void install() {
            if (options.traceIo() || options.traceSoftSwitches()) {
                machine.board().setIoTraceSink(this::recordIo);
            }
            if (options.traceDisk2()) {
                machine.board().setDisk2TraceSink(this::recordDisk2);
            }
            if (options.traceSuperDrive()) {
                machine.board().setSuperDriveTraceSink(this::recordSuperDrive);
            }
        }

        void pause() {
            recording = false;
        }

        void resume() {
            recording = true;
        }

        private void recordIo(String mappingName, boolean read, IoAccess access, int value) {
            if (!recording) {
                return;
            }
            boolean softSwitch = isApple2SoftSwitch(access.address());
            if (!options.traceIo() && !(options.traceSoftSwitches() && softSwitch)) {
                return;
            }
            String kind = options.traceIo() ? "io" : "softSwitch";
            record("%s|t=%d pc=0x%s %s name=%s addr=0x%s off=0x%s value=0x%s".formatted(
                    kind,
                    access.tState(),
                    hex16(machine.cpu().registers().pc()),
                    read ? "R" : "W",
                    mappingName,
                    hex16(access.address()),
                    hex16(access.offset()),
                    hex8(value)
            ));
        }

        private void recordDisk2(Apple2Disk2TraceEvent event) {
            if (!recording) {
                return;
            }
            record("disk2|t=%d pc=0x%s %s switch=%s addr=0x%s off=0x%s value=0x%s track=%d halfTrack=%d pos=%d motor=%d spinning=%d drive=%d q6=%d q7=%d".formatted(
                    event.tState(),
                    hex16(machine.cpu().registers().pc()),
                    event.read() ? "R" : "W",
                    event.switchName(),
                    hex16(event.address()),
                    hex16(event.offset()),
                    hex8(event.value()),
                    event.track(),
                    event.halfTrack(),
                    event.trackPosition(),
                    event.motorOn() ? 1 : 0,
                    event.spinning() ? 1 : 0,
                    event.drive1Selected() ? 1 : 2,
                    event.q6() ? 1 : 0,
                    event.q7() ? 1 : 0
            ));
        }

        private void recordSuperDrive(Apple2SuperDriveTraceEvent event) {
            if (!recording) {
                return;
            }
            if (event.source() == Apple2SuperDriveTraceEvent.Source.HOST) {
                record("superdrive|src=host t=%d pc=0x%s %s region=%s addr=0x%s off=0x%s value=0x%s bank=0x%s cpc=0x%s instr=%d".formatted(
                        machine.currentTState(),
                        hex16(machine.cpu().registers().pc()),
                        event.read() ? "R" : "W",
                        event.region(),
                        hex16(event.address()),
                        hex16(event.offset()),
                        hex8(event.value()),
                        hex8(event.bankSelect()),
                        hex16(event.controllerPc()),
                        event.controllerInstructions()
                ));
            } else {
                record("superdrive|src=controller instr=%d pc=0x%s %s region=%s addr=0x%s off=0x%s value=0x%s bank=0x%s".formatted(
                        event.controllerInstructions(),
                        hex16(event.controllerPc()),
                        event.read() ? "R" : "W",
                        event.region(),
                        hex16(event.address()),
                        hex8(event.offset()),
                        hex8(event.value()),
                        hex8(event.bankSelect())
                ));
            }
        }

        private static boolean isApple2SoftSwitch(int address) {
            int normalized = address & 0xFFFF;
            return normalized >= Apple2Memory.IO_START && normalized < Apple2Memory.IO_END_EXCLUSIVE;
        }

        private void record(String line) {
            if (options.limit() == 0) {
                dropped++;
                return;
            }
            if (events.size() < options.limit()) {
                events.add(line);
            } else if (options.tail()) {
                events.set(nextTailIndex, line);
                nextTailIndex = (nextTailIndex + 1) % options.limit();
                dropped++;
            } else {
                dropped++;
            }
        }

        void print() {
            if (!options.enabled()) {
                return;
            }
            System.out.println("traceEvents=" + events.size());
            if (dropped > 0) {
                System.out.println("traceDropped=" + dropped);
            }
            if (options.tail()) {
                System.out.println("traceMode=tail");
            }
            System.out.println("trace:");
            int start = options.tail() && dropped > 0 ? nextTailIndex : 0;
            for (int i = 0; i < events.size(); i++) {
                int eventIndex = (start + i) % events.size();
                System.out.println("%04d|%s".formatted(i + 1, events.get(eventIndex)));
            }
        }
    }

    private record TraceOptions(
            boolean traceIo,
            boolean traceSoftSwitches,
            boolean traceDisk2,
            boolean traceSuperDrive,
            int limit,
            boolean tail
    ) {
        private static final int DEFAULT_LIMIT = 256;

        boolean enabled() {
            return traceIo || traceSoftSwitches || traceDisk2 || traceSuperDrive;
        }
    }

    private record ProbeConfig(
            Path imagePath,
            long maxInstructions,
            String keyScript,
            String expectedScreen,
            Long expectedFrameCrc32,
            int[] keyPollPcs,
            int[] stopPcs,
            int[] watchAddrs,
            List<PcPoke> pcPokes,
            int[] profilePcCallers,
            int profilePcTop,
            Path dumpFramePath,
            Path diskPath,
            Path disk2RomPath,
            Path superDrive35RomPath,
            Path superDrive35MediaPath,
            int superDrive35Slot,
            int superDrive35WarmupTStates,
            long hostWarmupInstructions,
            DesktopMachineKind machineKind,
            Path proDosBootPath,
            Path proDosBootBlocksPath,
            int proDosBootLoadAddress,
            Integer proDosBootEntryAddress,
            int proDosBootSlot,
            Integer initialA,
            Integer initialX,
            TraceOptions traceOptions
    ) {
    }

    private record FrameProbeResult(
            Path dumpPath,
            int width,
            int height,
            long crc32,
            Long expectedCrc32
    ) {
    }

    private record ProDosBootLoad(
            Path imagePath,
            Apple2ProDosBlockImage image,
            String volumeName,
            byte[] program,
            int loadAddress,
            int entryAddress,
            int slot,
            long crc32,
            boolean shimInstalled
    ) {
    }

    private record SuperDrive35MediaLoad(
            Path imagePath,
            Apple2ProDosBlockImage image,
            String volumeName
    ) {
    }

    private record PcPoke(
            int pc,
            int[] addresses,
            int[] values
    ) {
    }
}
