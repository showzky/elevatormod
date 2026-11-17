package com.showzky.elevatormod;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds and operates the doors an elevator controls while parked: the cabin's own doors and the
 * landing doors directly in front of them. Doors are identified by their master (lower-left) cell.
 */
public final class ElevatorDoorControl {
    /** How far out from a cabin door, along its facing axis, a landing door may stand. */
    private static final int LANDING_DOOR_REACH = 2;

    public static List<BlockPos> controlledDoors(Level level, Collection<BlockPos> cabin) {
        Set<BlockPos> cabinCells = cabin instanceof Set<BlockPos> set ? set : Set.copyOf(cabin);
        Set<BlockPos> masters = new LinkedHashSet<>();
        for (BlockPos pos : cabinCells) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(ElevatorRegistries.DOOR)) continue;
            masters.add(ElevatorDoorBlock.anchor(pos, state));
            Direction facing = state.getValue(ElevatorDoorBlock.FACING);
            for (Direction side : new Direction[] {facing, facing.getOpposite()}) {
                for (int distance = 1; distance <= LANDING_DOOR_REACH; distance++) {
                    BlockPos candidate = pos.relative(side, distance);
                    if (cabinCells.contains(candidate) || !level.hasChunkAt(candidate)) continue;
                    BlockState landing = level.getBlockState(candidate);
                    if (landing.is(ElevatorRegistries.DOOR) && landing.getValue(ElevatorDoorBlock.FACING).getAxis() == facing.getAxis())
                        masters.add(ElevatorDoorBlock.anchor(candidate, landing));
                }
            }
        }
        return List.copyOf(masters);
    }

    /** True when every controlled door is fully shut. Doors that no longer exist do not count. */
    public static boolean allClosed(Level level, List<BlockPos> doors) {
        for (BlockPos door : doors) {
            BlockState state = level.getBlockState(door);
            if (state.is(ElevatorRegistries.DOOR) && !ElevatorDoorBlock.isClosed(state)) return false;
        }
        return true;
    }

    /** True when any controlled door has been told to open (it may still be sliding). */
    public static boolean anyOpen(Level level, List<BlockPos> doors) {
        for (BlockPos door : doors) {
            BlockState state = level.getBlockState(door);
            if (state.is(ElevatorRegistries.DOOR) && state.getValue(ElevatorDoorBlock.OPEN)) return true;
        }
        return false;
    }

    /** Opens or closes each door that is not already heading that way, so animations are not restarted. */
    public static void setOpen(Level level, List<BlockPos> doors, boolean open) {
        for (BlockPos door : doors) {
            BlockState state = level.getBlockState(door);
            if (state.is(ElevatorRegistries.DOOR) && state.getValue(ElevatorDoorBlock.OPEN) != open)
                ElevatorDoorBlock.setOpen(level, door, open);
        }
    }

    private ElevatorDoorControl() {}
}
