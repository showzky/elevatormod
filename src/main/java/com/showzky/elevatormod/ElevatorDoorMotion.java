package com.showzky.elevatormod;

/**
 * Panel travel taken from the supplied Elevator_Doors_Fresh animation: each panel moves 16
 * model pixels outward in 0.75 s (15 ticks) through linear keyframes at 0, 0.15, 0.6 and 0.75 s
 * (0, 1.6, 14.4 and 16 pixels). Closing plays the same curve in reverse, matching the close clip.
 * The server steps the door's FRAME property through the same curve for collision.
 */
public final class ElevatorDoorMotion {
    public static final int TRAVEL_TICKS = 15;
    public static final int FRAMES = 8;
    public static final float TRAVEL_PIXELS = 16;
    private static final float[] TIMES = {0F, 0.2F, 0.8F, 1F};
    private static final float[] OFFSETS = {0F, 1.6F, 14.4F, 16F};

    /** Outward panel offset in model pixels for an animation progress from 0 (closed) to 1 (open). */
    public static float panelOffset(float progress) {
        float t = Math.clamp(progress, 0F, 1F);
        for (int i = 1; i < TIMES.length; i++) {
            if (t <= TIMES[i]) {
                float local = (t - TIMES[i - 1]) / (TIMES[i] - TIMES[i - 1]);
                return OFFSETS[i - 1] + (OFFSETS[i] - OFFSETS[i - 1]) * local;
            }
        }
        return TRAVEL_PIXELS;
    }

    /** Tick at which the animation reaches a frame, so server frames follow the 15-tick clip. */
    public static int frameTick(int frame) { return Math.round(frame * (float) TRAVEL_TICKS / FRAMES); }

    /** Delay before stepping from {@code frame} towards {@code target}. */
    public static int stepDelay(int frame, int target) {
        int next = frame + Integer.signum(target - frame);
        return Math.max(1, Math.abs(frameTick(next) - frameTick(frame)));
    }

    private ElevatorDoorMotion() {}
}
