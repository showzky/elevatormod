package com.showzky.elevatormod;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Carries the local player on a moving cabin. Players are client-authoritative, so the client
 * moves its own player by exactly the cabin movement it has received, before the player's own
 * movement runs. The player therefore stays on the floor this client collides with, instead of
 * being corrected towards the server's slightly different cabin position.
 */
@EventBusSubscriber(modid = ElevatorMod.MOD_ID, value = Dist.CLIENT)
public final class ElevatorClientPassengerCarry {
    /** How far above a part's top the player's feet may be and still count as standing on it. */
    private static final double STANDING_TOLERANCE = 0.15;
    private static final Map<PoweredPulleyBlockEntity, Double> LAST_HEIGHT = new WeakHashMap<>();

    @SubscribeEvent
    public static void beforeTick(ClientTickEvent.Pre event) {
        var minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) { LAST_HEIGHT.clear(); return; }
        for (var pulley : ElevatorCabinMotion.loaded(minecraft.level)) {
            var motion = pulley.motion;
            if (!motion.active()) { LAST_HEIGHT.remove(pulley); continue; }
            Double previous = LAST_HEIGHT.put(pulley, motion.y);
            if (previous == null) continue;
            double delta = motion.y - previous;
            // Ignore no movement and large jumps (reloads, teleports of the cabin data).
            if (Math.abs(delta) < 1e-7 || Math.abs(delta) > 2) continue;
            if (player.isPassenger() || player.isSpectator() || player.getAbilities().flying) continue;
            if (standingOn(motion, previous, player.getBoundingBox()) && player.getDeltaMovement().y <= 0.1)
                ElevatorPassengerCollision.carry(player, delta);
        }
    }

    private static boolean standingOn(ElevatorCabinMotion motion, double cabinY, AABB feet) {
        for (VoxelShape shape : motion.shapes(cabinY)) {
            if (shape.isEmpty()) continue;
            AABB box = shape.bounds();
            if (feet.maxX > box.minX && feet.minX < box.maxX && feet.maxZ > box.minZ && feet.minZ < box.maxZ
                    && Math.abs(feet.minY - box.maxY) < STANDING_TOLERANCE) return true;
        }
        return false;
    }

    private ElevatorClientPassengerCarry() {}
}
