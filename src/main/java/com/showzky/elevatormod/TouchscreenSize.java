package com.showzky.elevatormod;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Physical size and display metrics of a touchscreen. The canvas covers the display glass
 * (the housing minus a 2-pixel border) and is measured in canvas units, top-left origin.
 * Larger screens use fewer units per block, so their text is physically larger.
 */
public enum TouchscreenSize implements StringRepresentable {
    SMALL("1x1", 1, 1, 128, new Metrics(28, 2, 14, 2, 12, 4, 0)),
    TALL("1x2", 1, 2, 112, new Metrics(50, 1, 16, 3, 14, 4, 14)),
    LARGE("2x2", 2, 2, 96, new Metrics(52, 2, 18, 4, 16, 6, 14));

    public static final Codec<TouchscreenSize> CODEC = StringRepresentable.fromEnum(TouchscreenSize::values);
    /** Model pixels of housing border around the display glass. */
    public static final int BORDER_PIXELS = 2;
    /** Depth of the display glass front face, in model pixels from the rear. */
    public static final float GLASS_DEPTH_PIXELS = 3.0F;

    /**
     * @param header      height of the floor/destination header
     * @param columns     floor button columns
     * @param rowHeight   floor button height
     * @param gap         spacing between buttons
     * @param pagerHeight height of the page control row
     * @param margin      outer body margin
     * @param footer      height reserved for the status footer (0 = none)
     */
    public record Metrics(int header, int columns, int rowHeight, int gap, int pagerHeight, int margin, int footer) {}

    private final String id;
    public final int width, height, unitsPerBlock;
    public final Metrics metrics;

    TouchscreenSize(String id, int width, int height, int unitsPerBlock, Metrics metrics) {
        this.id = id; this.width = width; this.height = height; this.unitsPerBlock = unitsPerBlock; this.metrics = metrics;
    }

    public int canvasWidth() { return Math.round((width * 16 - 2 * BORDER_PIXELS) * unitsPerBlock / 16F); }
    public int canvasHeight() { return Math.round((height * 16 - 2 * BORDER_PIXELS) * unitsPerBlock / 16F); }
    public String registryName() { return "elevator_touchscreen_" + id; }
    @Override public String getSerializedName() { return id; }
}
