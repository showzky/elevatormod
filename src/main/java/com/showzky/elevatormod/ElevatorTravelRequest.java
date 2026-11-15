package com.showzky.elevatormod;

import net.minecraft.world.level.Level;

/**
 * Server-side request for a pulley to travel to a landing. Validates the landing against
 * fresh floor discovery, assembles a parked cabin when needed, then queues the stop with the
 * same rules as the Call Button and Elevator Controller.
 */
public final class ElevatorTravelRequest {
    public enum Result {
        ACCEPTED, WAITING_POWER, ALREADY_HERE, UNAVAILABLE, BLOCKED;
        public TouchscreenSettings.Feedback feedback(boolean call, boolean queued) {
            return switch (this) {
                case ACCEPTED -> call ? TouchscreenSettings.Feedback.CALLED : queued ? TouchscreenSettings.Feedback.QUEUED : TouchscreenSettings.Feedback.TRAVELLING;
                case WAITING_POWER -> TouchscreenSettings.Feedback.WAITING_POWER;
                case ALREADY_HERE -> TouchscreenSettings.Feedback.ALREADY_HERE;
                case UNAVAILABLE -> TouchscreenSettings.Feedback.UNAVAILABLE;
                case BLOCKED -> TouchscreenSettings.Feedback.BLOCKED;
            };
        }
    }

    public static Result request(PoweredPulleyBlockEntity pulley, int stop) {
        Level level = pulley.getLevel();
        if (level == null || level.isClientSide || pulley.isRemoved() || pulley.attachment() == null) return Result.UNAVAILABLE;
        var motion = pulley.motion;
        if (!motion.active()) {
            var scan = ElevatorAssembly.scan(level, pulley.getBlockPos());
            if (scan.attachment() == null || !scan.attachment().equals(pulley.attachment())) return Result.UNAVAILABLE;
            boolean served = ElevatorFloorDiscovery.discover(level, pulley.getBlockPos(), scan.attachment(), scan.components())
                    .floors().stream().anyMatch(floor -> floor.attachmentY() == stop);
            if (!served) return Result.UNAVAILABLE;
            if (scan.attachment().getY() == stop) return Result.ALREADY_HERE;
            if (!motion.assemble()) return Result.BLOCKED;
        } else if (motion.floors().stream().noneMatch(floor -> floor.y() == stop)) {
            return Result.UNAVAILABLE;
        }
        motion.request(stop);
        if (!motion.hasCall(stop)) return Result.ALREADY_HERE;
        return pulley.energy().getEnergyStored() < ElevatorEnergyUsage.perTick(motion.speed) ? Result.WAITING_POWER : Result.ACCEPTED;
    }

    private ElevatorTravelRequest() {}
}
