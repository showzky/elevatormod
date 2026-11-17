package com.showzky.elevatormod;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Carries the local player on a moving cabin. Players are client-authoritative, so the client
 * moves its own player by exactly the cabin movement it has received, before the player's own
 * movement runs. The player therefore stays on the floor this client collides with, instead of
 * being corrected towards the server's slightly different cabin position.
 * <p>
 * This must run after the player's previous position is recorded for the tick (entity tick
 * pre-event), so the carry is part of the frame interpolation like the cabin's own movement.
 * Carrying earlier makes the player and camera jump 20 times a second against a gliding cabin.
 */
@EventBusSubscriber(modid = ElevatorMod.MOD_ID, value = Dist.CLIENT)
public final class ElevatorClientPassengerCarry {
    /** How far above a part's top the player's feet may be and still count as standing on it. */
    private static final double STANDING_TOLERANCE = 0.15;
    private static final Map<PoweredPulleyBlockEntity, Double> LAST_HEIGHT = new WeakHashMap<>();

    @SubscribeEvent
    public static void beforePlayerTick(EntityTickEvent.Pre event) {
        var minecraft = Minecraft.getInstance();
        if (!(event.getEntity() instanceof LocalPlayer player) || player != minecraft.player || minecraft.level == null) return;
        for (var pulley : ElevatorCabinMotion.loaded(minecraft.level)) {
            var motion = pulley.motion;
            if (!motion.active()) { LAST_HEIGHT.remove(pulley); continue; }
            Double previous = LAST_HEIGHT.put(pulley, motion.y);
            if (previous == null) continue;
            double delta = motion.y - previous;
            // Ignore no movement and teleport-sized jumps (reloads of the cabin data). Bunched
            // updates at high speed move the cabin several blocks in one tick and must still carry.
            if (Math.abs(delta) < 1e-7 || Math.abs(delta) > ElevatorCabinMotion.CLIENT_SNAP_DISTANCE) continue;
            if (player.isPassenger() || player.isSpectator()) continue;
            // A descending cabin can drop away from a rider faster than gravity pulls them down,
            // so on the way down a rider slightly above the floor is still carried with it.
            double reach = STANDING_TOLERANCE + Math.max(0, -delta);
            if (!player.getAbilities().flying && standingOn(motion, previous, player.getBoundingBox(), reach) && player.getDeltaMovement().y <= 0.1)
                ElevatorPassengerCollision.carry(player, delta);
            else pushAhead(motion, previous, player, delta);
        }
    }

    /**
     * A cabin that moves into a player who is not riding it pushes them along its direction of
     * travel: below its floor when it descends onto them, on top when it rises into them. Like
     * Moving Elevators, the player is placed there directly; if that is inside the ground, the
     * game's own stuck-in-a-block handling squeezes them out sideways, instead of the cabin ending
     * up around them. Only overlaps this movement created count, so a player already inside a
     * part is not flung out.
     */
    private static void pushAhead(ElevatorCabinMotion motion, double previousY, LocalPlayer player, double delta) {
        AABB body = player.getBoundingBox().deflate(1e-7);
        List<VoxelShape> before = motion.shapes(previousY), now = motion.shapes(motion.y);
        double push = 0;
        for (int i = 0; i < now.size(); i++) {
            VoxelShape shape = now.get(i);
            if (shape.isEmpty() || !shape.bounds().intersects(body)) continue;
            if (!before.get(i).isEmpty() && before.get(i).bounds().intersects(body)) continue;
            AABB part = shape.bounds();
            double needed = delta < 0 ? part.minY - body.maxY : part.maxY - body.minY;
            if (delta < 0 ? needed < push : needed > push) push = needed;
        }
        if (push == 0) return;
        player.setPos(player.getX(), player.getY() + push, player.getZ());
        // Keep up with a descending cabin instead of being caught again next tick.
        if (delta < 0 && player.getDeltaMovement().y > delta)
            player.setDeltaMovement(player.getDeltaMovement().x, delta, player.getDeltaMovement().z);
    }

    private static boolean standingOn(ElevatorCabinMotion motion, double cabinY, AABB feet, double reach) {
        for (VoxelShape shape : motion.shapes(cabinY)) {
            if (shape.isEmpty()) continue;
            AABB box = shape.bounds();
            if (feet.maxX > box.minX && feet.minX < box.maxX && feet.maxZ > box.minZ && feet.minZ < box.maxZ
                    && feet.minY > box.maxY - STANDING_TOLERANCE && feet.minY < box.maxY + reach) return true;
        }
        return false;
    }

    private ElevatorClientPassengerCarry() {}
}
