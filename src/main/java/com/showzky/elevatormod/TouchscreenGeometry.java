package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Converts between world space and a touchscreen's canvas. The model frame matches the
 * supplied Blockbench models: the display faces +Z (south), the rear is at Z=0 and model X
 * runs to the viewer's right, with the bottom-left (master) cell at the origin. A facing
 * rotates that frame about the master cell's vertical centre line.
 */
public final class TouchscreenGeometry {
    /** A touch on the display glass, in canvas units, and its distance along the ray. */
    public record Hit(double canvasX, double canvasY, double distance) {}

    /** World position of a screen section relative to its bottom-left master cell. */
    public static BlockPos section(BlockPos master, Direction facing, int column, int row) {
        return master.relative(facing.getCounterClockWise(), column).above(row);
    }

    /** Converts a world point to model pixels; {@code masterCorner} is the master cell's minimum corner. */
    public static Vec3 toModel(Vec3 world, Vec3 masterCorner, Direction facing) {
        double rx = world.x - masterCorner.x - 0.5, rz = world.z - masterCorner.z - 0.5;
        Direction right = facing.getCounterClockWise();
        double modelX = rx * right.getStepX() + rz * right.getStepZ();
        double modelZ = rx * facing.getStepX() + rz * facing.getStepZ();
        return new Vec3((modelX + 0.5) * 16, (world.y - masterCorner.y) * 16, (modelZ + 0.5) * 16);
    }

    /** Converts model pixels back to world space; the inverse of {@link #toModel}. */
    public static Vec3 toWorld(Vec3 model, Vec3 masterCorner, Direction facing) {
        double mx = model.x / 16 - 0.5, mz = model.z / 16 - 0.5;
        Direction right = facing.getCounterClockWise();
        return new Vec3(masterCorner.x + 0.5 + mx * right.getStepX() + mz * facing.getStepX(),
                masterCorner.y + model.y / 16,
                masterCorner.z + 0.5 + mx * right.getStepZ() + mz * facing.getStepZ());
    }

    /** Model-pixel point on the glass for a canvas coordinate. */
    public static Vec3 canvasToModel(TouchscreenSize size, double canvasX, double canvasY) {
        double scale = 16.0 / size.unitsPerBlock;
        return new Vec3(TouchscreenSize.BORDER_PIXELS + canvasX * scale,
                size.height * 16 - TouchscreenSize.BORDER_PIXELS - canvasY * scale,
                TouchscreenSize.GLASS_DEPTH_PIXELS);
    }

    /**
     * Intersects a ray with the display glass. Returns null when the ray misses the glass,
     * approaches from behind or the glass is beyond {@code reach}.
     */
    public static @Nullable Hit intersect(TouchscreenSize size, Direction facing, Vec3 masterCorner,
                                          Vec3 eye, Vec3 direction, double reach) {
        Vec3 start = toModel(eye, masterCorner, facing);
        Vec3 end = toModel(eye.add(direction.normalize().scale(reach)), masterCorner, facing);
        double plane = TouchscreenSize.GLASS_DEPTH_PIXELS;
        if (start.z <= plane || end.z >= plane) return null;
        double t = (start.z - plane) / (start.z - end.z);
        double x = start.x + (end.x - start.x) * t, y = start.y + (end.y - start.y) * t;
        double border = TouchscreenSize.BORDER_PIXELS;
        if (x < border || x > size.width * 16 - border || y < border || y > size.height * 16 - border) return null;
        double scale = size.unitsPerBlock / 16.0;
        return new Hit((x - border) * scale, (size.height * 16 - border - y) * scale, reach * t);
    }

    private TouchscreenGeometry() {}
}
