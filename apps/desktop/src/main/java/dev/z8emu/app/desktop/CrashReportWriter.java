package dev.z8emu.app.desktop;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

final class CrashReportWriter {
    private CrashReportWriter() {
    }

    static void write(
            String sourceLabel,
            int pc,
            Consumer<StringBuilder> machineLines,
            String lastKeyEvent,
            IntUnaryOperator memoryReader,
            Throwable failure
    ) {
        try {
            Path reportPath = Path.of("/tmp/z8-emu-last-crash.txt");
            StringWriter stack = new StringWriter();
            failure.printStackTrace(new PrintWriter(stack));

            StringBuilder body = new StringBuilder();
            body.append("source=").append(sourceLabel).append('\n');
            body.append("pc=0x").append(ProbeOutput.hex16(pc)).append('\n');
            machineLines.accept(body);
            body.append("lastKey=").append(lastKeyEvent).append('\n');
            body.append("bytesAroundPc:\n");
            for (int address = pc - 16; address <= pc + 16; address++) {
                int normalized = address & 0xFFFF;
                body.append(ProbeOutput.hex16(normalized))
                        .append(": ")
                        .append(ProbeOutput.hex8(memoryReader.applyAsInt(normalized)))
                        .append('\n');
            }
            body.append("failure=\n").append(stack);

            Files.writeString(
                    reportPath,
                    body.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            System.err.println("Wrote crash report to " + reportPath);
        } catch (IOException io) {
            System.err.println("Failed to write crash report: " + io.getMessage());
        }
    }
}
