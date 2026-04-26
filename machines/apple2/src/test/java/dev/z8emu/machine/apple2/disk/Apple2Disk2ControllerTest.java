package dev.z8emu.machine.apple2.disk;

import dev.z8emu.platform.bus.io.IoAccess;
import dev.z8emu.platform.time.TStateCounter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Apple2Disk2ControllerTest {
    private static final int MOTOR_OFF = 0xC0E8;
    private static final int MOTOR_ON = 0xC0E9;
    private static final int DRIVE_1 = 0xC0EA;
    private static final int Q7_LOW = 0xC0EE;
    private static final int READ_LATCH = 0xC0EC;

    @Test
    void readLatchOnlyAdvancesWhenRawBytePeriodHasElapsed() {
        TStateCounter clock = new TStateCounter();
        Apple2Disk2Controller controller = controller(clock);

        int first = read(controller, READ_LATCH);
        assertTrue((first & 0x80) != 0);
        assertEquals(1, controller.currentTrackPosition());

        assertEquals(first & 0x7F, read(controller, READ_LATCH));
        assertEquals(1, controller.currentTrackPosition());

        clock.advance(31);
        assertEquals(first & 0x7F, read(controller, READ_LATCH));
        assertEquals(1, controller.currentTrackPosition());

        clock.advance(1);
        int second = read(controller, READ_LATCH);
        assertTrue((second & 0x80) != 0);
        assertEquals(2, controller.currentTrackPosition());

        clock.advance(96);
        int afterThreeMoreRawBytes = read(controller, READ_LATCH);
        assertTrue((afterThreeMoreRawBytes & 0x80) != 0);
        assertEquals(5, controller.currentTrackPosition());
    }

    @Test
    void inactiveDriveDoesNotAdvanceReadPosition() {
        TStateCounter clock = new TStateCounter();
        Apple2Disk2Controller controller = controller(clock);

        assertTrue((read(controller, READ_LATCH) & 0x80) != 0);
        assertEquals(1, controller.currentTrackPosition());

        read(controller, MOTOR_OFF);
        clock.advance(1_000_001);

        assertEquals(0x00, read(controller, READ_LATCH));
        assertEquals(1, controller.currentTrackPosition());
    }

    @Test
    void diskBytesKeepChangingBrieflyAfterMotorOff() {
        TStateCounter clock = new TStateCounter();
        Apple2Disk2Controller controller = controller(clock);

        assertTrue((read(controller, READ_LATCH) & 0x80) != 0);
        read(controller, MOTOR_OFF);
        clock.advance(32);

        assertTrue((read(controller, READ_LATCH) & 0x80) != 0);
        assertEquals(2, controller.currentTrackPosition());
    }

    @Test
    void repeatedMotorOnAccessDoesNotDelayNextRawByte() {
        TStateCounter clock = new TStateCounter();
        Apple2Disk2Controller controller = controller(clock);

        assertTrue((read(controller, READ_LATCH) & 0x80) != 0);
        clock.advance(31);
        read(controller, MOTOR_ON);
        clock.advance(1);

        assertTrue((read(controller, READ_LATCH) & 0x80) != 0);
        assertEquals(2, controller.currentTrackPosition());
    }

    private static Apple2Disk2Controller controller(TStateCounter clock) {
        Apple2Disk2Controller controller = new Apple2Disk2Controller(clock);
        controller.insertDisk(Apple2DosDiskImage.fromDosOrderedBytes(dosImage()));
        read(controller, MOTOR_ON);
        read(controller, DRIVE_1);
        read(controller, Q7_LOW);
        return controller;
    }

    private static int read(Apple2Disk2Controller controller, int address) {
        return controller.readC0x(new IoAccess(address, address & 0x0F, 0, 0));
    }

    private static byte[] dosImage() {
        byte[] image = new byte[Apple2DosDiskImage.IMAGE_SIZE];
        for (int i = 0; i < image.length; i++) {
            image[i] = (byte) i;
        }
        return image;
    }
}
