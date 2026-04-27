package dev.z8emu.machine.apple2.disk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Apple2SwimControllerTest {
    @Test
    void modeWritesAreReflectedThroughStatusRegister() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();

        assertEquals(0xFF, controller.read(0x0E));

        controller.read(0x0D);
        controller.write(0x0F, 0x1F);
        assertEquals(0x5F, controller.read(0x0E));

        controller.read(0x0D);
        controller.write(0x0F, 0x00);
        assertEquals(0x40, controller.read(0x0E));
    }

    @Test
    void selfTestShiftPatternResetsThroughOffsetSix() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();

        controller.write(0x06, 0x00);

        assertEquals(0x01, controller.read(0x0B));
        assertEquals(0x02, controller.read(0x0B));
        assertEquals(0x04, controller.read(0x0B));
        assertEquals(0x08, controller.read(0x0B));
        assertEquals(0x10, controller.read(0x0B));
        assertEquals(0x20, controller.read(0x0B));
        assertEquals(0x40, controller.read(0x0B));
        assertEquals(0x80, controller.read(0x0B));
        assertEquals(0xFE, controller.read(0x0B));
        assertEquals(0xFD, controller.read(0x0B));
        assertEquals(0xFB, controller.read(0x0B));
        assertEquals(0xF7, controller.read(0x0B));
        assertEquals(0xEF, controller.read(0x0B));
        assertEquals(0xDF, controller.read(0x0B));
        assertEquals(0xBF, controller.read(0x0B));
        assertEquals(0x7F, controller.read(0x0B));
    }

    @Test
    void diskReadLatchDoesNotEchoLastWriteByte() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();

        controller.write(0x00, 0x57);

        assertEquals(0xFF, controller.read(0x00));
    }

    @Test
    void iwmSwitchSequenceEntersIsmParameterRamMode() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();

        switchToIsmMode(controller);
        assertEquals(0x40, controller.read(0x0E));

        controller.write(0x03, 0x18);
        controller.write(0x03, 0x41);
        controller.write(0x03, 0x2E);
        controller.write(0x06, 0x18);

        assertEquals(0x18, controller.read(0x0B));
        assertEquals(0x41, controller.read(0x0B));
        assertEquals(0x2E, controller.read(0x0B));
    }

    @Test
    void ismModeCanReturnToIwmRegisterMode() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();

        switchToIsmMode(controller);
        assertEquals(0x40, controller.read(0x0E));

        controller.write(0x06, 0x40);

        assertEquals(0xFF, controller.read(0x0E));
    }

    @Test
    void mediaStreamSetsReadyStatusAndSuppliesReadBytes() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();
        controller.read(0x0D);
        controller.write(0x0F, 0x1F);
        controller.insertMediaStream(new CyclingStream(0xD5, 0xAA));

        assertEquals(0x5F, controller.read(0x0E));
        controller.onControllerCyclesElapsed(31);
        assertEquals(0x5F, controller.read(0x0E));
        controller.onControllerCyclesElapsed(1);

        assertEquals(0xDF, controller.read(0x0E));
        controller.read(0x0C);
        assertEquals(0xD5, controller.read(0x00));
        assertEquals(0x5F, controller.read(0x0E));

        controller.onControllerCyclesElapsed(32);

        assertEquals(0xDF, controller.read(0x0E));
        controller.read(0x0C);
        assertEquals(0xAA, controller.read(0x00));
    }

    @Test
    void f8WriteToOffsetSixReturnsSwimToInitialSelfTestState() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();
        controller.read(0x0D);
        controller.write(0x0F, 0x00);
        assertEquals(0x40, controller.read(0x0E));

        controller.write(0x06, 0xF8);

        assertEquals(0xFF, controller.read(0x0E));
        assertEquals(0xFF, controller.read(0x00));
    }

    @Test
    void f8WriteToOffsetSixResetsIwmStateAfterModeLatchEnteredIsmMode() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();

        controller.read(0x0D);
        switchToIsmMode(controller);
        assertEquals(0x40, controller.read(0x0E));

        controller.write(0x06, 0xF8);

        assertEquals(0xFF, controller.read(0x0E));
        assertEquals(0xFF, controller.read(0x00));
    }

    @Test
    void iwmFastModeHandshakeDropsBitSixForWriteReadyPolling() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();

        controller.read(0x0D);
        controller.write(0x0F, 0x08);

        assertEquals(0x48, controller.read(0x0E));
        assertEquals(0xB0, controller.read(0x0C));
    }

    @Test
    void iwmFastModeDataWriteClearsReadyUntilNextMediaByteArrives() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();
        controller.read(0x0D);
        controller.write(0x0F, 0x08);
        controller.insertMediaStream(new CyclingStream(0xC3));
        controller.onControllerCyclesElapsed(32);
        assertEquals(0xC8, controller.statusRegister());

        assertEquals(0xC3, controller.read(0x0C));
        controller.write(0x0D, 0xC8);

        assertEquals(0x08, controller.modeRegister());
        assertEquals(0x48, controller.statusRegister());
        controller.onControllerCyclesElapsed(31);
        assertEquals(0x48, controller.statusRegister());
        controller.onControllerCyclesElapsed(1);
        assertEquals(0xC8, controller.statusRegister());
    }

    @Test
    void activeSwitchIsReflectedInIwmStatusBitFive() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();
        controller.read(0x0D);
        controller.write(0x0F, 0x1F);

        assertEquals(0x5F, controller.statusRegister());

        controller.read(0x09);
        assertEquals(0x7F, controller.statusRegister());

        controller.read(0x08);
        assertEquals(0x5F, controller.statusRegister());
    }

    @Test
    void externalDrive35SignalsOverrideReadyStatusDuringDriveProbe() {
        Apple2SwimController controller = new Apple2SwimController();
        int[] externalStatusBit = {-1};
        controller.setDrive35Signals(new Apple2SwimController.Drive35Signals() {
            @Override
            public int statusBit(int phases, boolean active) {
                return active ? externalStatusBit[0] : -1;
            }

            @Override
            public void phaseChanged(int phase, boolean high, int phases, boolean active) {
            }
        });
        controller.reset();
        controller.read(0x0D);
        controller.write(0x0F, 0x1F);
        controller.read(0x09);
        controller.insertMediaStream(new CyclingStream(0xD5));
        controller.onControllerCyclesElapsed(32);

        assertEquals(0xFF, controller.statusRegister());

        externalStatusBit[0] = 0;
        assertEquals(0x7F, controller.statusRegister());

        externalStatusBit[0] = 1;
        assertEquals(0xFF, controller.statusRegister());
    }

    @Test
    void ismReadModeFeedsSyntheticMediaWhenActionBitIsSet() {
        Apple2SwimController controller = new Apple2SwimController();
        controller.reset();
        controller.insertMediaStream(new CyclingStream(0xD5));
        switchToIsmMode(controller);

        controller.onControllerCyclesElapsed(32);
        assertEquals(0x0C, controller.read(0x0F));

        controller.write(0x07, 0x08);
        controller.onControllerCyclesElapsed(32);

        assertEquals(0x8C, controller.read(0x0F));
        assertEquals(0xD5, controller.read(0x09));
    }

    private static void switchToIsmMode(Apple2SwimController controller) {
        controller.write(0x0F, 0x4F);
        controller.write(0x0F, 0x0F);
        controller.write(0x0F, 0x4F);
        controller.write(0x0F, 0x4F);
    }

    private static final class CyclingStream implements Apple2SwimMediaStream {
        private final int[] bytes;
        private int offset;

        private CyclingStream(int... bytes) {
            this.bytes = bytes;
        }

        @Override
        public int nextByte() {
            int value = bytes[offset];
            offset = (offset + 1) % bytes.length;
            return value;
        }

        @Override
        public void reset() {
            offset = 0;
        }
    }
}
