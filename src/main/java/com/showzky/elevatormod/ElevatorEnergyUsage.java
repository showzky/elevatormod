package com.showzky.elevatormod;

/**
 * Cabin speed limits and the FE drawn while moving. Consumption rises linearly with the
 * configured speed but gently: 14 FE/t at 0.5 m/s, 20 FE/t at 2 m/s and 28 FE/t at 4 m/s.
 */
public final class ElevatorEnergyUsage {
    /** Speeds in blocks per tick (1 block per tick = 20 m/s). */
    public static final double MIN_SPEED = 0.025, MAX_SPEED = 0.2, DEFAULT_SPEED = 0.1;
    /** Speed setting granularity: 0.1 m/s. */
    public static final double SPEED_STEP = 0.005;
    private static final double BASE_FE_PER_TICK = 12, FE_PER_TICK_PER_METRE_PER_SECOND = 4;

    /** FE drawn per tick while the cabin moves at the given configured speed. */
    public static int perTick(double blocksPerTick) {
        return (int) Math.round(BASE_FE_PER_TICK + FE_PER_TICK_PER_METRE_PER_SECOND * metresPerSecond(blocksPerTick));
    }

    /** Clamps a requested speed to the allowed range and snaps it to 0.1 m/s steps. */
    public static double sanitizeSpeed(double blocksPerTick) {
        if (!Double.isFinite(blocksPerTick)) return DEFAULT_SPEED;
        double snapped = Math.round(blocksPerTick / SPEED_STEP) * SPEED_STEP;
        return Math.clamp(snapped, MIN_SPEED, MAX_SPEED);
    }

    public static double metresPerSecond(double blocksPerTick) { return blocksPerTick * 20; }
    public static double blocksPerTick(double metresPerSecond) { return metresPerSecond / 20; }

    /** Approximate distance in blocks the stored energy can move the cabin at full speed. */
    public static long range(int storedEnergy, double blocksPerTick) {
        return (long) (storedEnergy / (double) perTick(blocksPerTick) * blocksPerTick);
    }

    private ElevatorEnergyUsage() {}
}
