package dev.z8emu.app.desktop;

import dev.z8emu.machine.apple2.disk.Apple2ProDosBlockImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

public final class Apple2ProDosCatalogLauncher {
    private Apple2ProDosCatalogLauncher() {
    }

    public static void main(String[] args) throws IOException {
        Config config = parseArgs(args);
        if (config == null) {
            System.err.println("Usage: Apple2ProDosCatalogLauncher <disk.po> "
                    + "[--extract=<name> --output=<path>] "
                    + "[--scan=<name> --scan-boot --load-address=<hex> --scan-limit=<count>]");
            System.exit(2);
            return;
        }

        Path imagePath = config.imagePath();
        byte[] bytes = Files.readAllBytes(imagePath);
        Apple2ProDosBlockImage image = Apple2ProDosBlockImage.fromProDosOrderedBytes(bytes);

        System.out.println("source=" + imagePath);
        System.out.println("imageBytes=" + bytes.length);
        System.out.println("volume=/" + image.volumeName());
        System.out.println("rootEntries=" + image.rootDirectoryEntries().size());
        System.out.println("catalog:");
        for (Apple2ProDosBlockImage.DirectoryEntry entry : image.rootDirectoryEntries()) {
            String crc32 = entry.isStandardFile()
                    ? " crc32=0x" + crc32Hex(image.readFileData(entry))
                    : "";
            System.out.println("%s type=0x%s key=%d blocks=%d eof=%d storage=%d%s".formatted(
                    entry.name(),
                    hex8(entry.fileType()),
                    entry.keyBlock(),
                    entry.blocksUsed(),
                    entry.eof(),
                    entry.storageType(),
                    crc32
            ));
        }
        if (config.extractName() != null) {
            extractFile(image, config);
        }
        if (config.scanName() != null) {
            scanFile(image, config);
        }
        if (config.scanBoot()) {
            scanBootBlocks(image, config);
        }
    }

    private static Config parseArgs(String[] args) {
        if (args.length == 0) {
            return null;
        }
        Path imagePath = null;
        String extractName = null;
        Path outputPath = null;
        String scanName = null;
        boolean scanBoot = false;
        int loadAddress = 0;
        int scanLimit = 80;
        for (String arg : args) {
            if (arg.startsWith("--extract=")) {
                extractName = arg.substring("--extract=".length());
            } else if (arg.startsWith("--output=")) {
                outputPath = Path.of(arg.substring("--output=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--scan=")) {
                scanName = arg.substring("--scan=".length());
            } else if (arg.equals("--scan-boot")) {
                scanBoot = true;
            } else if (arg.startsWith("--load-address=")) {
                loadAddress = DesktopLaunchOptions.parseAddress(arg.substring("--load-address=".length()));
            } else if (arg.startsWith("--scan-limit=")) {
                scanLimit = parsePositiveInt(arg.substring("--scan-limit=".length()), "scan-limit");
            } else if (arg.startsWith("--")) {
                return null;
            } else if (imagePath == null) {
                imagePath = Path.of(arg).toAbsolutePath().normalize();
            } else {
                return null;
            }
        }
        if (imagePath == null || (extractName == null) != (outputPath == null)) {
            return null;
        }
        return new Config(imagePath, extractName, outputPath, scanName, scanBoot, loadAddress, scanLimit);
    }

    private static void extractFile(Apple2ProDosBlockImage image, Config config) throws IOException {
        Apple2ProDosBlockImage.DirectoryEntry entry = image.findRootEntry(config.extractName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProDOS root entry not found: " + config.extractName()
                ));
        if (!entry.isStandardFile()) {
            throw new IllegalArgumentException("Cannot extract non-standard ProDOS entry: " + entry.name());
        }
        byte[] data = image.readFileData(entry);
        Path parent = config.outputPath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(config.outputPath(), data);
        System.out.println("extracted=" + entry.name());
        System.out.println("extractPath=" + config.outputPath());
        System.out.println("extractBytes=" + data.length);
        System.out.println("extractCrc32=0x" + crc32Hex(data));
    }

    private static void scanFile(Apple2ProDosBlockImage image, Config config) {
        Apple2ProDosBlockImage.DirectoryEntry entry = image.findRootEntry(config.scanName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProDOS root entry not found: " + config.scanName()
                ));
        if (!entry.isStandardFile()) {
            throw new IllegalArgumentException("Cannot scan non-standard ProDOS entry: " + entry.name());
        }
        byte[] data = image.readFileData(entry);
        System.out.println("scan=" + entry.name());
        System.out.println("scanBytes=" + data.length);
        System.out.println("scanCrc32=0x" + crc32Hex(data));
        System.out.println("loadAddress=0x" + hex16(config.loadAddress()));
        printStrings(data, config);
        printAbsoluteReferences(data, config);
        printIoReferences(data, config);
    }

    private static void scanBootBlocks(Apple2ProDosBlockImage image, Config config) {
        byte[] block0 = image.readBlock(0);
        byte[] block1 = image.readBlock(1);
        byte[] data = new byte[block0.length + block1.length];
        System.arraycopy(block0, 0, data, 0, block0.length);
        System.arraycopy(block1, 0, data, block0.length, block1.length);
        System.out.println("bootScan=blocks0-1");
        System.out.println("bootScanBytes=" + data.length);
        System.out.println("bootScanCrc32=0x" + crc32Hex(data));
        System.out.println("loadAddress=0x" + hex16(config.loadAddress()));
        printStrings(data, config);
        printAbsoluteReferences(data, config);
        printIoReferences(data, config);
    }

    private static void printStrings(byte[] data, Config config) {
        System.out.println("strings:");
        int printed = 0;
        for (Apple2BinaryScanner.AsciiString string : Apple2BinaryScanner.printableStrings(data, 4)) {
            if (printed >= config.scanLimit()) {
                System.out.println("  ...");
                return;
            }
            int pc = (config.loadAddress() + string.offset()) & 0xFFFF;
            System.out.println("  offset=0x%s pc=0x%s \"%s\"".formatted(
                    hex16(string.offset()),
                    hex16(pc),
                    escapeString(string.value())
            ));
            printed++;
        }
        if (printed == 0) {
            System.out.println("  (none)");
        }
    }

    private static void printAbsoluteReferences(byte[] data, Config config) {
        System.out.println("absoluteRefs:");
        int printed = 0;
        for (Apple2BinaryScanner.AbsoluteReference reference
                : Apple2BinaryScanner.absoluteReferences(data, config.loadAddress())) {
            if (printed >= config.scanLimit()) {
                System.out.println("  ...");
                return;
            }
            System.out.println("  offset=0x%s pc=0x%s opcode=0x%s %s %s target=0x%s".formatted(
                    hex16(reference.offset()),
                    hex16(reference.pc()),
                    hex8(reference.opcode()),
                    reference.mnemonic(),
                    reference.addressingMode(),
                    hex16(reference.target())
            ));
            printed++;
        }
        if (printed == 0) {
            System.out.println("  (none)");
        }
    }

    private static void printIoReferences(byte[] data, Config config) {
        System.out.println("ioRefs:");
        int printed = 0;
        for (Apple2BinaryScanner.AbsoluteReference reference
                : Apple2BinaryScanner.ioReferences(data, config.loadAddress())) {
            if (printed >= config.scanLimit()) {
                System.out.println("  ...");
                return;
            }
            System.out.println("  offset=0x%s pc=0x%s opcode=0x%s %s %s target=0x%s area=%s".formatted(
                    hex16(reference.offset()),
                    hex16(reference.pc()),
                    hex8(reference.opcode()),
                    reference.mnemonic(),
                    reference.addressingMode(),
                    hex16(reference.target()),
                    Apple2BinaryScanner.apple2IoArea(reference.target())
            ));
            printed++;
        }
        if (printed == 0) {
            System.out.println("  (none)");
        }
    }

    private static int parsePositiveInt(String value, String label) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1) {
            throw new IllegalArgumentException(label + " must be positive: " + value);
        }
        return parsed;
    }

    private static String escapeString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String crc32Hex(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, data.length);
        return "%08X".formatted(crc32.getValue());
    }

    private static String hex8(int value) {
        return "%02X".formatted(value & 0xFF);
    }

    private static String hex16(int value) {
        return "%04X".formatted(value & 0xFFFF);
    }

    private record Config(
            Path imagePath,
            String extractName,
            Path outputPath,
            String scanName,
            boolean scanBoot,
            int loadAddress,
            int scanLimit
    ) {
    }
}
