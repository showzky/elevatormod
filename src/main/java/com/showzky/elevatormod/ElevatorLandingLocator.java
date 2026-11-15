package com.showzky.elevatormod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Finds the landing served by a caller (call button or touchscreen): a landing connector
 * within 8 blocks horizontally, any height, same dimension. A directly adjacent connector
 * wins; otherwise the connector closest in height wins, and ties are rejected so a caller
 * never silently selects the wrong elevator or landing.
 */
public final class ElevatorLandingLocator {
    public static final int HORIZONTAL_RANGE = 8;

    public record Match(PoweredPulleyBlockEntity pulley, int stop, BlockPos connector) {}

    public static boolean nearby(BlockPos caller, BlockPos connector) {
        int dx = caller.getX() - connector.getX(), dz = caller.getZ() - connector.getZ();
        return dx * dx + dz * dz <= HORIZONTAL_RANGE * HORIZONTAL_RANGE;
    }

    /** Uses fresh floor discovery for every pulley; suitable for one-off button presses. */
    public static List<Match> live(Level level, BlockPos caller) {
        List<Match> matches = new ArrayList<>();
        for (var pulley : ElevatorCabinMotion.loaded(level)) {
            if (pulley.attachment() == null) continue;
            if (pulley.motion.active()) {
                for (var floor : pulley.motion.floors())
                    if (nearby(caller, floor.pos())) matches.add(new Match(pulley, floor.y(), floor.pos()));
            } else {
                var scan = ElevatorAssembly.scan(level, pulley.getBlockPos());
                if (scan.attachment() == null || !scan.attachment().equals(pulley.attachment())) continue;
                for (var floor : ElevatorFloorDiscovery.discover(level, pulley.getBlockPos(), scan.attachment(), scan.components()).floors())
                    if (nearby(caller, floor.connector())) matches.add(new Match(pulley, floor.attachmentY(), floor.connector()));
            }
        }
        return select(matches, caller);
    }

    /** Same rules, using each pulley's periodically refreshed floor directory. */
    public static List<Match> cached(Level level, BlockPos caller) {
        List<Match> matches = new ArrayList<>();
        for (var pulley : ElevatorCabinMotion.loaded(level)) {
            if (pulley.attachment() == null) continue;
            for (var floor : pulley.directory().floors())
                if (nearby(caller, floor.connector())) matches.add(new Match(pulley, floor.stop(), floor.connector()));
        }
        return select(matches, caller);
    }

    private static List<Match> select(List<Match> matches, BlockPos caller) {
        if (matches.isEmpty()) return matches;
        var adjacent = matches.stream().filter(match -> match.connector().distManhattan(caller) == 1).toList();
        if (!adjacent.isEmpty()) return adjacent;
        int closestHeight = matches.stream().mapToInt(match -> Math.abs(match.connector().getY() - caller.getY())).min().orElse(0);
        return matches.stream().filter(match -> Math.abs(match.connector().getY() - caller.getY()) == closestHeight).toList();
    }

    private ElevatorLandingLocator() {}
}
