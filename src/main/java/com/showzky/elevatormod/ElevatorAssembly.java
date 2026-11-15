package com.showzky.elevatormod;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Finds only explicitly supported cabin components; never absorbs ordinary building blocks. */
public final class ElevatorAssembly {
    // Safety limit protects the server from accidentally connected, unbounded structures.
    private static final int MAX_COMPONENTS = 4096;
    public record Result(@Nullable BlockPos attachment, int blockCount, @Nullable String error, @Nullable BlockPos obstruction, Set<BlockPos> components) {
        public Result { components = Set.copyOf(components); }
        static Result fail(String error) { return new Result(null, 0, error, null, Set.of()); }
        static Result obstructed(BlockPos pos) { return new Result(null, 0, "obstructed", pos.immutable(), Set.of()); }
    }
    public static Result scan(Level level, BlockPos pulley) {
        BlockPos attachment = null;
        for (int y = pulley.getY() - 1; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(pulley.getX(), y, pulley.getZ());
            BlockState state = level.getBlockState(pos);
            if (state.is(ElevatorRegistries.ATTACHMENT)) { attachment = pos; break; }
            if (!state.isAir()) return Result.obstructed(pos);
        }
        if (attachment == null) return Result.fail("no_attachment");
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(attachment); visited.add(attachment);
        int blocks = 0, controllers = 0, attachments = 0;
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            BlockState state = level.getBlockState(current);
            if (state.is(ElevatorRegistries.ELEVATOR_BLOCK)) blocks++;
            if (state.is(ElevatorRegistries.CONTROLLER)) controllers++;
            if (state.is(ElevatorRegistries.ATTACHMENT)) attachments++;
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (next.getY() < level.getMinBuildHeight() || next.getY() >= level.getMaxBuildHeight() || visited.contains(next)) continue;
                if (!level.hasChunkAt(next)) return Result.fail("unloaded");
                BlockState neighbour = level.getBlockState(next);
                // A connector's contact face is a cabin boundary, not a structural join.
                if (state.is(ElevatorRegistries.FLOOR_CONNECTOR)
                        && state.getValue(FacingElevatorBlock.FACING) == direction) continue;
                if (neighbour.is(ElevatorRegistries.FLOOR_CONNECTOR)
                        && neighbour.getValue(FacingElevatorBlock.FACING) == direction.getOpposite()) continue;
                if (isCabinComponent(neighbour)) {
                    visited.add(next);
                    if (visited.size() > MAX_COMPONENTS) return Result.fail("too_large");
                    queue.addLast(next);
                }
            }
        }
        if (attachments != 1) return Result.fail("extra_attachment");
        if (blocks == 0) return Result.fail("missing_block");
        if (controllers != 1) return Result.fail("missing_controller");
        if (!addMountedTouchscreens(level, visited)) return Result.fail("too_large");
        return new Result(attachment.immutable(), visited.size(), null, null, visited);
    }

    /**
     * Touchscreens join a cabin only when mounted against one of its blocks, and always as a
     * complete display. They are leaves: nothing is reached through them, so a landing screen
     * beside a parked cabin is never absorbed.
     */
    private static boolean addMountedTouchscreens(Level level, Set<BlockPos> cabin) {
        Set<BlockPos> screens = new HashSet<>();
        for (BlockPos pos : cabin) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(direction);
                if (cabin.contains(next) || screens.contains(next) || !level.hasChunkAt(next)) continue;
                BlockState state = level.getBlockState(next);
                // A screen facing away from this cabin block has its rear mounted on it.
                if (state.getBlock() instanceof ElevatorTouchscreenBlock screen && state.getValue(ElevatorTouchscreenBlock.FACING) == direction)
                    screens.addAll(screen.intactCells(level, next, state));
            }
        }
        cabin.addAll(screens);
        return cabin.size() <= MAX_COMPONENTS;
    }
    static boolean isCabinComponent(BlockState state) {
        return state.is(ElevatorRegistries.ATTACHMENT) || state.is(ElevatorRegistries.ELEVATOR_BLOCK)
                || state.is(ElevatorRegistries.CONTROLLER) || state.is(ElevatorRegistries.FLOOR_CONNECTOR)
                || state.is(ElevatorRegistries.DOOR) || state.is(ElevatorRegistries.LIGHT);
    }
    private ElevatorAssembly() {}
}

