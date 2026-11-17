package com.showzky.elevatormod;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws a moving cabin's translucent camouflage (stained glass) after the world's translucent
 * blocks. The block-entity buffer that ElevatorRopeRenderer uses is flushed before them, and
 * translucent entity render types write depth, so glass drawn there hid any ice or water behind
 * it. Drawn here, those blocks are already on screen and the glass is blended over them.
 */
@EventBusSubscriber(modid = ElevatorMod.MOD_ID, value = Dist.CLIENT)
public final class ElevatorTranslucentCabinRenderer {
    /**
     * Some rendering mods (shader pipelines) skip this stage. ElevatorRopeRenderer then draws the
     * glass itself, as before, so it never disappears.
     */
    private static final long STAGE_TIMEOUT_NANOS = 250_000_000L;
    private static long lastStageNanos = Long.MIN_VALUE / 2;

    /** True while this pass is running, so ElevatorRopeRenderer leaves translucent layers to it. */
    public static boolean isActive() { return System.nanoTime() - lastStageNanos < STAGE_TIMEOUT_NANOS; }

    @SubscribeEvent
    public static void afterTranslucentBlocks(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        lastStageNanos = System.nanoTime();
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) return;
        var camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var buffers = minecraft.renderBuffers().bufferSource();
        Set<RenderType> used = new LinkedHashSet<>();
        // This stage passes no pose stack; the model-view matrix already holds the camera rotation.
        var pose = new PoseStack();
        for (var pulley : ElevatorCabinMotion.loaded(level)) {
            var motion = pulley.motion;
            if (!motion.active() || pulley.attachment() == null || !event.getFrustum().isVisible(ElevatorRopeRenderer.renderBounds(pulley))) continue;
            double cabinY = net.minecraft.util.Mth.lerp(partialTick, motion.displayPreviousY, motion.displayY);
            var pos = pulley.getBlockPos();
            pose.pushPose();
            pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            ElevatorRopeRenderer.renderCabinParts(pulley, cabinY, pose, buffers, LevelRenderer.getLightColor(level, pos),
                    OverlayTexture.NO_OVERLAY, ElevatorRopeRenderer.Layers.TRANSLUCENT_ONLY, used);
            pose.popPose();
        }
        // Translucent entity render types sort on upload, so the cabin's own panes blend back to front.
        for (RenderType type : used) buffers.endBatch(type);
    }

    private ElevatorTranslucentCabinRenderer() {}
}
