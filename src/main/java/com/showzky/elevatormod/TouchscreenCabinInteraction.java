package com.showzky.elevatormod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Moving cabin parts are not world blocks, so vanilla targeting passes through them. When the
 * use key is pressed and a carried touchscreen is nearer than whatever vanilla targeted, the
 * press is sent to the server instead, which recomputes and validates the touch itself.
 */
@EventBusSubscriber(modid = ElevatorMod.MOD_ID, value = Dist.CLIENT)
public final class TouchscreenCabinInteraction {
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null || player.isSpectator()) return;
        Vec3 eye = player.getEyePosition(1), look = player.getViewVector(1);
        double reach = player.blockInteractionRange();
        HitResult vanilla = minecraft.hitResult;
        double nearest = vanilla != null && vanilla.getType() != HitResult.Type.MISS ? eye.distanceTo(vanilla.getLocation()) : reach;
        TouchscreenTouchPayload touch = null;
        for (var pulley : ElevatorCabinMotion.loaded(minecraft.level)) {
            var motion = pulley.motion;
            if (!motion.active()) continue;
            for (var part : motion.parts) {
                if (!(part.state().getBlock() instanceof ElevatorTouchscreenBlock block) || !ElevatorTouchscreenBlock.isMaster(part.state())) continue;
                BlockPos offset = part.offset();
                Vec3 corner = TouchscreenInteraction.cabinCorner(pulley, offset, motion.displayY);
                var hit = TouchscreenGeometry.intersect(block.size(), part.state().getValue(ElevatorTouchscreenBlock.FACING), corner, eye, look, reach);
                if (hit != null && hit.distance() < nearest + 1e-3) {
                    nearest = hit.distance();
                    touch = new TouchscreenTouchPayload(pulley.getBlockPos(), offset, motion.displayY);
                }
            }
        }
        if (touch == null) return;
        PacketDistributor.sendToServer(touch);
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    private TouchscreenCabinInteraction() {}
}
