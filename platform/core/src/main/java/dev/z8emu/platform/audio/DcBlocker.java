package dev.z8emu.platform.audio;

public final class DcBlocker {
    public static final double DEFAULT_ALPHA = 0.995d;

    private final double alpha;
    private int previousInputLevel;
    private double outputLevel;

    public DcBlocker(double alpha, int initialInputLevel) {
        if (Double.isNaN(alpha) || alpha < 0.0d || alpha >= 1.0d) {
            throw new IllegalArgumentException("alpha must be in the [0.0, 1.0) range");
        }
        this.alpha = alpha;
        reset(initialInputLevel);
    }

    public void reset(int initialInputLevel) {
        previousInputLevel = initialInputLevel;
        outputLevel = 0.0d;
    }

    public short nextSample(int inputLevel) {
        outputLevel = inputLevel - previousInputLevel + (alpha * outputLevel);
        previousInputLevel = inputLevel;
        return clampToPcm16(Math.round(outputLevel));
    }

    private static short clampToPcm16(long sample) {
        if (sample > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (sample < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) sample;
    }
}
