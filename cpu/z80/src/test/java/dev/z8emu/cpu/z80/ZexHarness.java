package dev.z8emu.cpu.z80;

import dev.z8emu.platform.bus.CpuBus;
import java.io.IOException;
import java.io.InputStream;

final class ZexHarness {
    private static final int MEMORY_SIZE = 0x10000;
    private static final int PROGRAM_LOAD_ADDRESS = 0x0100;

    private ZexHarness() {
    }

    static ZexResult runResource(String resourceName, long maxInstructions) throws IOException {
        byte[] program = readResourceBytes(resourceName);
        HarnessBus bus = new HarnessBus();
        bus.loadProgram(PROGRAM_LOAD_ADDRESS, program);
        bus.installCpmTraps();

        Z80Cpu cpu = new Z80Cpu(bus);
        bus.attachCpu(cpu);
        cpu.registers().setPc(PROGRAM_LOAD_ADDRESS);

        long instructions = 0;
        long tStates = 0;

        try {
            while (!bus.finished() && instructions < maxInstructions) {
                tStates += cpu.runInstruction();
                instructions++;
            }
        } catch (Throwable failure) {
            return new ZexResult(resourceName, instructions, tStates, bus.output(), bus.finished(), failure);
        }

        return new ZexResult(resourceName, instructions, tStates, bus.output(), bus.finished(), null);
    }

    private static byte[] readResourceBytes(String resourceName) throws IOException {
        try (InputStream stream = ZexHarness.class.getResourceAsStream("/zex/" + resourceName)) {
            if (stream == null) {
                throw new IOException("Missing test resource: " + resourceName);
            }
            return stream.readAllBytes();
        }
    }

    record ZexResult(
            String resourceName,
            long instructions,
            long tStates,
            String output,
            boolean finished,
            Throwable failure
    ) {
    }

    private static final class HarnessBus implements CpuBus {
        private final byte[] memory = new byte[MEMORY_SIZE];
        private final StringBuilder output = new StringBuilder();
        private Z80Cpu cpu;
        private boolean finished;

        void attachCpu(Z80Cpu cpu) {
            this.cpu = cpu;
        }

        void loadProgram(int address, byte[] program) {
            if (address < 0 || address + program.length > memory.length) {
                throw new IllegalArgumentException("Program does not fit in harness memory");
            }

            System.arraycopy(program, 0, memory, address, program.length);
        }

        void installCpmTraps() {
            memory[0x0000] = (byte) 0xD3; // OUT (0),A - test finished
            memory[0x0001] = 0x00;

            memory[0x0005] = (byte) 0xDB; // IN A,(0) - emit output through bus callback
            memory[0x0006] = 0x00;
            memory[0x0007] = (byte) 0xC9; // RET
        }

        String output() {
            return output.toString();
        }

        boolean finished() {
            return finished;
        }

        @Override
        public int fetchOpcode(int address) {
            return readMemory(address);
        }

        @Override
        public int readMemory(int address) {
            return Byte.toUnsignedInt(memory[address & 0xFFFF]);
        }

        @Override
        public void writeMemory(int address, int value) {
            memory[address & 0xFFFF] = (byte) value;
        }

        @Override
        public int readPort(int port) {
            if ((port & 0xFF) == 0x00) {
                emitBdosOutput();
            }
            return 0xFF;
        }

        @Override
        public void writePort(int port, int value) {
            if ((port & 0xFF) == 0x00) {
                finished = true;
            }
        }

        @Override
        public int acknowledgeInterrupt() {
            return 0xFF;
        }

        @Override
        public void onRefresh(int irValue) {
        }

        @Override
        public int currentTState() {
            return 0;
        }

        private void emitBdosOutput() {
            if (cpu == null) {
                throw new IllegalStateException("CPU is not attached to the harness bus");
            }

            int operation = cpu.registers().c();
            if (operation == 2) {
                output.append((char) cpu.registers().e());
            } else if (operation == 9) {
                int address = cpu.registers().de();
                while (readMemory(address) != '$') {
                    output.append((char) readMemory(address));
                    address = (address + 1) & 0xFFFF;
                }
            }
        }
    }
}
