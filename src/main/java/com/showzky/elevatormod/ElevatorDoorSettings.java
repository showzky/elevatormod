package com.showzky.elevatormod;

import net.minecraft.nbt.CompoundTag;

/**
 * How an elevator operates its doors, set from the Elevator Controller.
 * Auto Close only controls timed closing: doors always close before the cabin departs.
 */
public record ElevatorDoorSettings(boolean autoOpen, boolean autoClose, int closeDelaySeconds) {
    public static final int MIN_DELAY = 1, MAX_DELAY = 30, DEFAULT_DELAY = 5;
    public static final ElevatorDoorSettings DEFAULT = new ElevatorDoorSettings(true, true, DEFAULT_DELAY);

    public ElevatorDoorSettings { closeDelaySeconds = Math.clamp(closeDelaySeconds, MIN_DELAY, MAX_DELAY); }

    public ElevatorDoorSettings withAutoOpen(boolean value) { return new ElevatorDoorSettings(value, autoClose, closeDelaySeconds); }
    public ElevatorDoorSettings withAutoClose(boolean value) { return new ElevatorDoorSettings(autoOpen, value, closeDelaySeconds); }
    public ElevatorDoorSettings withCloseDelay(int seconds) { return new ElevatorDoorSettings(autoOpen, autoClose, seconds); }
    public long closeDelayTicks() { return closeDelaySeconds * 20L; }

    public void save(CompoundTag tag) {
        tag.putBoolean("DoorAutoOpen", autoOpen);
        tag.putBoolean("DoorAutoClose", autoClose);
        tag.putInt("DoorCloseDelay", closeDelaySeconds);
    }

    /** Missing values (worlds from older versions) fall back to the defaults. */
    public static ElevatorDoorSettings load(CompoundTag tag) {
        return new ElevatorDoorSettings(
                tag.contains("DoorAutoOpen") ? tag.getBoolean("DoorAutoOpen") : DEFAULT.autoOpen,
                tag.contains("DoorAutoClose") ? tag.getBoolean("DoorAutoClose") : DEFAULT.autoClose,
                tag.contains("DoorCloseDelay") ? tag.getInt("DoorCloseDelay") : DEFAULT.closeDelaySeconds);
    }
}
