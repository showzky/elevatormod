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

    /** A contact's position is relative to the rope attachment. */
    public record CabinConnector(BlockPos offset, Direction facing) {}

    public static Result discover(Level level, BlockPos pulley, BlockPos attachment, Set<BlockPos> cabin) {
        List<CabinConnector> connectors = new ArrayList<>();
        for (BlockPos pos : cabin) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ElevatorRegistries.FLOOR_CONNECTOR))
                connectors.add(new CabinConnector(pos.subtract(attachment), state.getValue(FacingElevatorBlock.FACING)));
        }
        int minY = cabin.stream().mapToInt(p -> p.getY() - attachment.getY()).min().orElse(0);
        int maxY = cabin.stream().mapToInt(p -> p.getY() - attachment.getY()).max().orElse(0);
        return discover(level, pulley, attachment, connectors, minY, maxY, cabin);
    }

    public static Result discover(Level level, BlockPos pulley, BlockPos attachment,
                                  List<CabinConnector> connectors, int minRelativeY,
                                  int maxRelativeY, Set<BlockPos> excludedPositions) {
        TreeMap<Integer, Floor> floors = new TreeMap<>();
        boolean unloaded = false;
        for (CabinConnector connector : connectors) {
            BlockPos column = attachment.offset(connector.offset()).relative(connector.facing());
            if (!level.hasChunkAt(column)) {
                unloaded = true;
                continue;
            }
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                BlockPos stationary = new BlockPos(column.getX(), y, column.getZ());
                if (excludedPositions.contains(stationary)) continue;
                BlockState candidate = level.getBlockState(stationary);
                if (!candidate.is(ElevatorRegistries.FLOOR_CONNECTOR)
                        || candidate.getValue(FacingElevatorBlock.FACING) != connector.facing().getOpposite()) continue;
                int targetY = y - connector.offset().getY();
                if (targetY >= pulley.getY() || targetY + minRelativeY < level.getMinBuildHeight()
                        || targetY + maxRelativeY >= level.getMaxBuildHeight()) continue;
                floors.putIfAbsent(targetY, new Floor(stationary, targetY));
            }
        }
        return new Result(new ArrayList<>(floors.values()), connectors.size(), unloaded);
    }

    private ElevatorFloorDiscovery() {}
}
