package com.showzky.elevatormod;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Moves entities out of the way of a moving cabin, the way Moving Elevators' collision handler
 * does: anything standing on a cabin block is put on that block's new top, and anything under a
 * descending block is pushed down below it. Snapping (instead of normal movement) keeps mobs on
 * the cabin at any speed, so they never fall through the floor or end up inside the roof.
 */
public final class ElevatorEntityMover {
    /** An entity this close above a block's top counts as standing on it and follows it down. */
    private static final double RESTING_GAP = 0.1;
    private static final double EPSILON = 1e-4;

    /**
     * @param before   each cabin block's collision shape before this tick's step
     * @param after    the same shapes after the step, in the same order
     * @param previous gives an entity's feet height before it moved this tick, so the check is
     *                 made where the entity stood relative to the cabin's old position
     */
    public static void move(Level level, List<VoxelShape> before, List<VoxelShape> after, double step,
                            Predicate<Entity> include, java.util.function.ToDoubleFunction<Entity> previous) {
        if (step == 0) return;
        AABB area = null;
        for (int i = 0; i < before.size(); i++) {
            if (before.get(i).isEmpty()) continue;
            AABB swept = before.get(i).bounds().minmax(after.get(i).bounds());
            area = area == null ? swept : area.minmax(swept);
        }
        if (area == null) return;
        for (Entity entity : level.getEntities((Entity) null, area.inflate(0.5, 1, 0.5),
                e -> include.test(e) && !e.isSpectator() && !e.noPhysics && !e.isPassenger())) {
            AABB box = entity.getBoundingBox();
            double feet = previous.applyAsDouble(entity);
            double head = feet + box.getYsize();
            double lift = Double.NEGATIVE_INFINITY, push = Double.POSITIVE_INFINITY;
            for (int i = 0; i < before.size(); i++) {
                if (before.get(i).isEmpty()) continue;
                AABB old = before.get(i).bounds(), next = after.get(i).bounds();
                if (!overlapsHorizontally(box, old)) continue;
                if (feet >= old.maxY - EPSILON) {
                    boolean resting = feet - old.maxY < RESTING_GAP;
                    // Lifted when the block rises into it; carried down when resting on a falling block.
                    if (box.minY < next.maxY || (resting && step < 0 && entity.onGround())) lift = Math.max(lift, next.maxY);
                } else if (step < 0 && head <= old.minY + EPSILON && box.maxY > next.minY) {
                    push = Math.min(push, next.minY);
                }
            }
            if (lift != Double.NEGATIVE_INFINITY) place(entity, lift);
            else if (push != Double.POSITIVE_INFINITY) place(entity, push - box.getYsize());
        }
    }

    private static void place(Entity entity, double feetY) {
        entity.setPos(entity.getX(), feetY, entity.getZ());
        var motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, 0, motion.z);
        entity.setOnGround(true);
        entity.resetFallDistance();
    }

    private static boolean overlapsHorizontally(AABB a, AABB b) {
        return a.minX < b.maxX - 1e-7 && a.maxX > b.minX + 1e-7 && a.minZ < b.maxZ - 1e-7 && a.maxZ > b.minZ + 1e-7;
    }

    private ElevatorEntityMover() {}
}
