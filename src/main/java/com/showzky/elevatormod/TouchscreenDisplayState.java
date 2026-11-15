package com.showzky.elevatormod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a touchscreen shows, derived from the linked pulley's state. Built the same way
 * on the server (to resolve touches) and on clients (to render), so every viewer sees the same
 * information and the server never relies on client-side layout.
 */
public record TouchscreenDisplayState(Connection connection, List<FloorEntry> floors, int currentIndex,
                                      int destinationIndex, int callIndex, Travel travel, Status status,
                                      boolean callPending) {
    public static final int NO_STOP = Integer.MIN_VALUE;

    public enum Connection {
        /** Linked to a landing through the caller rules. */
        LANDING,
        /** Part of the elevator cabin itself. */
        CABIN,
        SEARCHING, NO_ELEVATOR, AMBIGUOUS, OFFLINE;
        public boolean linked() { return this == LANDING || this == CABIN; }
        public String translationKey() { return "touchscreen.elevatormod.connection." + name().toLowerCase(Locale.ROOT); }
    }
    public enum Travel { UP, DOWN, IDLE }
    public enum Status {
        READY, MOVING, NO_POWER, FAULT;
        public String translationKey() { return "touchscreen.elevatormod.status." + name().toLowerCase(Locale.ROOT); }
    }
    /** A landing, listed top floor first. */
    public record FloorEntry(int stop, String name, boolean queued) {}

    public static TouchscreenDisplayState unlinked(Connection connection) {
        return new TouchscreenDisplayState(connection, List.of(), -1, -1, -1, Travel.IDLE, Status.READY, false);
    }

    public boolean linked() { return connection.linked(); }
    public @Nullable FloorEntry current() { return currentIndex >= 0 ? floors.get(currentIndex) : null; }
    public @Nullable FloorEntry destination() { return destinationIndex >= 0 ? floors.get(destinationIndex) : null; }
    public @Nullable FloorEntry callFloor() { return callIndex >= 0 ? floors.get(callIndex) : null; }

    /**
     * @param pulley    the linked pulley, or null when it is unknown or unloaded
     * @param callStop  attachment height of this screen's landing, or {@link #NO_STOP}
     */
    public static TouchscreenDisplayState of(Connection connection, @Nullable PoweredPulleyBlockEntity pulley, int callStop) {
        if (!connection.linked()) return unlinked(connection);
        if (pulley == null || pulley.isRemoved() || pulley.attachment() == null) return unlinked(Connection.OFFLINE);
        var motion = pulley.motion;
        double y = motion.active() ? motion.y : pulley.attachment().getY();
        boolean moving = motion.active() && Math.abs(motion.target - y) > 1e-6;
        List<ElevatorFloorDirectory.Floor> sorted = new ArrayList<>(pulley.directory().floors());
        sorted.sort(Comparator.comparingInt(ElevatorFloorDirectory.Floor::stop).reversed());
        List<FloorEntry> floors = new ArrayList<>(sorted.size());
        int current = -1, destination = -1, call = -1;
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < sorted.size(); i++) {
            var floor = sorted.get(i);
            floors.add(new FloorEntry(floor.stop(), floor.name(), motion.hasCall(floor.stop())));
            double distance = Math.abs(floor.stop() - y);
            // While travelling the nearest landing is shown, as on a real position indicator.
            if (distance < closest && (moving || distance < 1e-3)) { closest = distance; current = i; }
            if (moving && floor.stop() == (int) motion.target) destination = i;
            if (floor.stop() == callStop) call = i;
        }
        Travel travel = !moving ? Travel.IDLE : motion.target > y ? Travel.UP : Travel.DOWN;
        Status status = statusOf(motion.status);
        if (status == Status.READY && moving) status = Status.MOVING;
        boolean pending = callStop != NO_STOP && motion.hasCall(callStop);
        return new TouchscreenDisplayState(connection, List.copyOf(floors), current, destination, call, travel, status, pending);
    }

    private static Status statusOf(String status) {
        String text = status.toLowerCase(Locale.ROOT);
        if (text.startsWith("waiting for fe")) return Status.NO_POWER;
        if (text.contains("obstruct") || text.contains("unavailable") || text.contains("blocked")
                || text.contains("failed") || text.contains("close the cabin")) return Status.FAULT;
        return Status.READY;
    }
}
