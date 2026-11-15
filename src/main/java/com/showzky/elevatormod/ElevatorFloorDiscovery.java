package com.showzky.elevatormod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Matches only facing contacts in the vertical column adjacent to each cabin connector. */
public final class ElevatorFloorDiscovery {
    public record Floor(BlockPos connector, int attachmentY) {}
    public record Result(List<Floor> floors, int cabinConnectors, boolean unloadedColumns) {
        public Result { floors = List.copyOf(floors); }
    }

    public static Result discover(Level level, BlockPos pulley, BlockPos attachment, Set<BlockPos> cabin) {
        TreeMap<Integer, Floor> floors = new TreeMap<>();
        int connectors = 0;
        boolean unloaded = false;
        int minRelativeY = cabin.stream().mapToInt(p -> p.getY() - attachment.getY()).min().orElse(0);
        int maxRelativeY = cabin.stream().mapToInt(p -> p.getY() - attachment.getY()).max().orElse(0);
        for (BlockPos moving : cabin) {
            BlockState state = level.getBlockState(moving);
            if (!state.is(ElevatorRegistries.FLOOR_CONNECTOR)) continue;
            connectors++;
            Direction facing = state.getValue(FacingElevatorBlock.FACING);
            BlockPos column = moving.relative(facing);
            if (!level.hasChunkAt(column)) {
                unloaded = true;
                continue;
            }
            int connectorOffset = moving.getY() - attachment.getY();
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                BlockPos stationary = new BlockPos(column.getX(), y, column.getZ());
                if (cabin.contains(stationary)) continue;
                BlockState candidate = level.getBlockState(stationary);
                if (!candidate.is(ElevatorRegistries.FLOOR_CONNECTOR)
                        || candidate.getValue(FacingElevatorBlock.FACING) != facing.getOpposite()) continue;
                int targetY = y - connectorOffset;
                if (targetY >= pulley.getY() || targetY + minRelativeY < level.getMinBuildHeight()
                        || targetY + maxRelativeY >= level.getMaxBuildHeight()) continue;
                floors.putIfAbsent(targetY, new Floor(stationary, targetY));
            }
        }
        return new Result(new ArrayList<>(floors.values()), connectors, unloaded);
    }

    private ElevatorFloorDiscovery() {}
}
