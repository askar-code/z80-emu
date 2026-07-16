package dev.z8emu.machine.cpc;

import dev.z8emu.machine.cpc.device.CpcFdcDevice;
import dev.z8emu.machine.cpc.disk.CpcDskLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpcFdcDeviceTraceTest {
    @Test
    void reportsReadCommandAndResultPhaseWithoutChangingTheTransfer() throws IOException {
        CpcFdcDevice fdc = new CpcFdcDevice();
        fdc.insertDisk(CpcDskLoader.load(standardDskImage()));
        fdc.writeMotorControl(0x01);
        List<CommandEvent> commands = new ArrayList<>();
        List<ResultEvent> results = new ArrayList<>();
        fdc.setTraceSink(new CpcFdcDevice.TraceSink() {
            @Override
            public void command(int commandByte, int[] args) {
                commands.add(new CommandEvent(commandByte, args));
            }

            @Override
            public void result(int commandByte, int st0) {
                results.add(new ResultEvent(commandByte, st0));
            }
        });

        writeFdc(fdc, 0x46, 0x00, 0x00, 0x00, 0xC1, 0x02, 0xC1, 0x2A, 0xFF);

        assertEquals(1, commands.size());
        assertEquals(0x46, commands.get(0).commandByte());
        assertArrayEquals(new int[]{0x00, 0x00, 0x00, 0xC1, 0x02, 0xC1, 0x2A, 0xFF}, commands.get(0).args());
        assertTrue(results.isEmpty());

        byte[] actual = new byte[512];
        for (int index = 0; index < actual.length; index++) {
            actual[index] = (byte) fdc.readDataRegister();
        }

        assertArrayEquals(sectorPattern(), actual);
        assertEquals(List.of(new ResultEvent(0x46, 0x00)), results);
    }

    @Test
    void nullTraceSinkLeavesCommandBehaviorUnchanged() throws IOException {
        CpcFdcDevice fdc = new CpcFdcDevice();
        fdc.insertDisk(CpcDskLoader.load(standardDskImage()));
        fdc.writeMotorControl(0x01);
        fdc.setTraceSink(null);

        writeFdc(fdc, 0x4A, 0x00);

        assertEquals(0x00, fdc.readDataRegister());
        assertEquals(0x00, fdc.readDataRegister());
        assertEquals(0x00, fdc.readDataRegister());
        assertEquals(0x00, fdc.readDataRegister());
        assertEquals(0x00, fdc.readDataRegister());
        assertEquals(0xC1, fdc.readDataRegister());
        assertEquals(0x02, fdc.readDataRegister());
    }

    private static void writeFdc(CpcFdcDevice fdc, int... values) {
        for (int value : values) {
            fdc.writeDataRegister(value);
        }
    }

    private static byte[] standardDskImage() {
        byte[] image = new byte[0x400];
        putAscii(image, 0x000, "MV - CPCEMU Disk-File\r\nDisk-Info\r\n");
        image[0x30] = 1;
        image[0x31] = 1;
        image[0x32] = 0x00;
        image[0x33] = 0x03;

        putAscii(image, 0x100, "Track-Info\r\n");
        image[0x110] = 0;
        image[0x111] = 0;
        image[0x114] = 2;
        image[0x115] = 1;
        image[0x116] = 0x2A;
        image[0x117] = (byte) 0xE5;
        image[0x118] = 0;
        image[0x119] = 0;
        image[0x11A] = (byte) 0xC1;
        image[0x11B] = 2;

        byte[] sector = sectorPattern();
        System.arraycopy(sector, 0, image, 0x200, sector.length);
        return image;
    }

    private static byte[] sectorPattern() {
        byte[] sector = new byte[512];
        for (int index = 0; index < sector.length; index++) {
            sector[index] = (byte) index;
        }
        return sector;
    }

    private static void putAscii(byte[] target, int offset, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }

    private record CommandEvent(int commandByte, int[] args) {
    }

    private record ResultEvent(int commandByte, int st0) {
    }
}
