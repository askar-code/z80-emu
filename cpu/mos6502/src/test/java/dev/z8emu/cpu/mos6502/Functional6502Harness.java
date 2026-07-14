package dev.z8emu.cpu.mos6502;

import dev.z8emu.platform.bus.CpuBus;
import java.nio.file.Files;
import java.nio.file.Path;

final class Functional6502Harness {
    private static final int MEMORY_SIZE = 0x10000;
    private static final int ENTRY_PC = 0x0400;

    private Functional6502Harness() {
    }

    static FunctionalResult runImage(Path imagePath, long maxInstructions) {
        long instructions = 0;
        long tStates = 0;
        int trapPc = -1;
        boolean trapped = false;
        Mos6502Cpu cpu = null;

        try {
            byte[] image = Files.readAllBytes(imagePath);
            if (image.length != MEMORY_SIZE) {
                throw new IllegalArgumentException("Functional test image must be exactly 65536 bytes");
            }

            HarnessBus bus = new HarnessBus();
            System.arraycopy(image, 0, bus.memory, 0, image.length);
            cpu = new Mos6502Cpu(bus, Mos6502Variant.NMOS_6502);
            cpu.registers().setPc(ENTRY_PC);

            while (instructions < maxInstructions) {
                int pcBefore = cpu.registers().pc();
                tStates += cpu.runInstruction();
                instructions++;
                if (cpu.registers().pc() == pcBefore) {
                    trapPc = pcBefore;
                    trapped = true;
                    break;
                }
            }
            if (!trapped) {
                trapPc = cpu.registers().pc();
            }
        } catch (Throwable failure) {
            if (cpu != null) {
                trapPc = cpu.registers().pc();
            }
            return new FunctionalResult(
                    instructions,
                    tStates,
                    trapPc,
                    trapped,
                    failure,
                    registerDump(cpu)
            );
        }

        return new FunctionalResult(
                instructions,
                tStates,
                trapPc,
                trapped,
                null,
                registerDump(cpu)
        );
    }

    private static String registerDump(Mos6502Cpu cpu) {
        if (cpu == null) {
            return "PC=---- A=-- X=-- Y=-- SP=-- P=--";
        }
        Mos6502Registers registers = cpu.registers();
        return "PC=%04X A=%02X X=%02X Y=%02X SP=%02X P=%02X".formatted(
                registers.pc(),
                registers.a(),
                registers.x(),
                registers.y(),
                registers.sp(),
                registers.p()
        );
    }

    record FunctionalResult(
            long instructions,
            long tStates,
            int trapPc,
            boolean trapped,
            Throwable failure,
            String registerDump
    ) {
    }

    private static final class HarnessBus implements CpuBus {
        private final byte[] memory = new byte[MEMORY_SIZE];

        @Override
        public int readMemory(int address) {
            return Byte.toUnsignedInt(memory[address & 0xFFFF]);
        }

        @Override
        public void writeMemory(int address, int value) {
            memory[address & 0xFFFF] = (byte) value;
        }
    }
}
