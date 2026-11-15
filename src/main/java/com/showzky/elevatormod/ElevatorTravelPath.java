package com.showzky.elevatormod;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Checks every block position swept by a vertically translating stationary cabin. */
public final class ElevatorTravelPath {
    public enum Status { CLEAR, UNLOADED, OUTSIDE_WORLD, OBSTRUCTED, TOO_LARGE }
    public record Result(Status status, @Nullable BlockPos position) {}

    public static Result check(Level level, Set<BlockPos> cabin, int verticalDistance) {
        int direction = Integer.signum(verticalDistance);
        int distance = Math.abs(verticalDistance);
        if ((long) distance * cabin.size() > 100_000) return new Result(Status.TOO_LARGE, null);
        for (int step = 1; step <= distance; step++) {
            int offset = step * direction;
            for (BlockPos original : cabin) {
                BlockPos target = original.offset(0, offset, 0);
                if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()) {
                    return new Result(Status.OUTSIDE_WORLD, target);
                }
                if (!level.hasChunkAt(target)) return new Result(Status.UNLOADED, target);
                // The existing cabin will be vacated, so it is not an obstruction.
                if (!cabin.contains(target) && !level.getBlockState(target).isAir()) {
                    return new Result(Status.OBSTRUCTED, target);
                }
            }
        }
        return new Result(Status.CLEAR, null);
    }
    private ElevatorTravelPath() {}
}
