package com.showzky.elevatormod;

import java.util.Locale;

/** What a touchscreen shows and which requests it accepts. */
public enum TouchscreenMode {
    /** Lists every landing; touching one requests travel there. */
    DESTINATION,
    /** Shows the elevator position and calls it to this screen's landing. */
    CALL,
    /** Live status only; never requests movement. */
    INFORMATION;

    public String translationKey() { return "touchscreen.elevatormod.mode." + name().toLowerCase(Locale.ROOT); }

    public static TouchscreenMode byName(String name) {
        for (TouchscreenMode mode : values()) if (mode.name().equals(name)) return mode;
        return DESTINATION;
    }

    public static TouchscreenMode byId(int id) { return id >= 0 && id < values().length ? values()[id] : DESTINATION; }
}
