package com.showzky.elevatormod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;

/** Draws one texture-mapped rope segment per block, so the supplied texture repeats. */
public class ElevatorRopeRenderer implements BlockEntityRenderer<PoweredPulleyBlockEntity> {
    public static final net.minecraft.client.resources.model.ModelResourceLocation DRUM = net.minecraft.client.resources.model.ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID,"block/powered_pulley_rotating_drum"));
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "textures/block/elevator_rope.png");
    public ElevatorRopeRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(PoweredPulleyBlockEntity pulley, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        double drumAngle=net.minecraft.util.Mth.lerp(partialTick,pulley.motion.drumPreviousAngle,pulley.motion.drumAngle);
        pose.pushPose();pose.translate(0.5,0.3125,0.5);pose.mulPose(Axis.XP.rotationDegrees((float)drumAngle));pose.translate(-0.5,-0.3125,-0.5);
        var mc=net.minecraft.client.Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.solid()),pulley.getBlockState(),mc.getModelManager().getModel(DRUM),1,1,1,light,overlay);
        pose.popPose();
        if (pulley.attachment() == null) return;
        var motion=pulley.motion;
        double cabinY=motion.active()?net.minecraft.util.Mth.lerp(partialTick,motion.displayPreviousY,motion.displayY):pulley.attachment().getY();
        if (motion.active()) renderCabinParts(pulley, cabinY, pose, buffers, light, overlay,
                ElevatorTranslucentCabinRenderer.isActive() ? Layers.ALL_BUT_TRANSLUCENT : Layers.ALL, null);
        double length = pulley.getBlockPos().getY() - cabinY - 1;
        if (length <= 0) return;
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        for (int segment = 0; segment < length; segment++) {
            pose.pushPose();
            float segmentHeight=(float)Math.min(1,length-segment);
            pose.translate(0, -segment-segmentHeight, 0);
            for (int side = 0; side < 4; side++) {
                PoseStack.Pose current = pose.last();
                vertex(vertices, current, -0.0625f, 0, -0.0625f, 0, 1, light, overlay);
                vertex(vertices, current, -0.0625f, segmentHeight, -0.0625f, 0, 0, light, overlay);
                vertex(vertices, current, 0.0625f, segmentHeight, -0.0625f, 0.5f, 0, light, overlay);
                vertex(vertices, current, 0.0625f, 0, -0.0625f, 0.5f, 1, light, overlay);
                pose.mulPose(Axis.YP.rotationDegrees(90));
            }
            pose.popPose();
        }
        pose.popPose();
    }
    /** Which of the moving cabin's layers a pass draws. */
    public enum Layers { ALL, ALL_BUT_TRANSLUCENT, TRANSLUCENT_ONLY }

    /**
     * Draws the moving cabin's blocks, relative to the pulley. Translucent camouflage (stained
     * glass) is normally drawn later by ElevatorTranslucentCabinRenderer, after the world's own
     * translucent blocks: drawn here, it would write depth before them and hide ice or water
     * behind it. Every render type used is added to {@code usedTypes} when it is not null.
     */
    public static void renderCabinParts(PoweredPulleyBlockEntity pulley, double cabinY, PoseStack pose, MultiBufferSource buffers,
                                        int light, int overlay, Layers layers, java.util.Set<RenderType> usedTypes) {
        var motion = pulley.motion;
        var mc = net.minecraft.client.Minecraft.getInstance();
        var poweredLights = ElevatorCabinLighting.poweredLights(motion.parts);
        for (var part : motion.parts) {
            if (layers == Layers.TRANSLUCENT_ONLY && part.camouflage() == null) continue;
            pose.pushPose();
            pose.translate(motion.origin.getX()+part.offset().getX()-pulley.getBlockPos().getX(),cabinY+part.offset().getY()-pulley.getBlockPos().getY(),motion.origin.getZ()+part.offset().getZ()-pulley.getBlockPos().getZ());
            var position=net.minecraft.core.BlockPos.containing(motion.origin.getX()+part.offset().getX(),cabinY+part.offset().getY(),motion.origin.getZ()+part.offset().getZ());
            int ambient = pulley.getLevel().hasChunkAt(position) ? net.minecraft.client.renderer.LevelRenderer.getLightColor(pulley.getLevel(), position) : light;
            int partLight = net.minecraft.client.renderer.LightTexture.pack(
                    ElevatorCabinLighting.blockLight(part.offset(), poweredLights, net.minecraft.client.renderer.LightTexture.block(ambient)),
                    net.minecraft.client.renderer.LightTexture.sky(ambient));
            var modelData=part.camouflage()==null ? net.neoforged.neoforge.client.model.data.ModelData.EMPTY : net.neoforged.neoforge.client.model.data.ModelData.of(ElevatorBlockEntity.CAMOUFLAGE,part.camouflage());
            var dispatcher=mc.getBlockRenderer();
            if (part.camouflage()==null) dispatcher.renderSingleBlock(part.state(),pose,buffers,partLight,overlay,modelData,null);
            else {
                var model=dispatcher.getBlockModel(part.state());
                int color=mc.getBlockColors().getColor(part.camouflage(),pulley.getLevel(),position,0);
                float red=(color >> 16 & 255)/255f, green=(color >> 8 & 255)/255f, blue=(color & 255)/255f;
                for (var type:model.getRenderTypes(part.state(),net.minecraft.util.RandomSource.create(42),modelData)) {
                    boolean translucent = type == RenderType.translucent();
                    if (translucent ? layers == Layers.ALL_BUT_TRANSLUCENT : layers == Layers.TRANSLUCENT_ONLY) continue;
                    var entityType = net.neoforged.neoforge.client.RenderTypeHelper.getEntityRenderType(type,false);
                    if (usedTypes != null) usedTypes.add(entityType);
                    dispatcher.getModelRenderer().renderModel(pose.last(),buffers.getBuffer(entityType),part.state(),model,red,green,blue,partLight,overlay,modelData,type);
                }
            }
            if (layers != Layers.TRANSLUCENT_ONLY) {
                // Doors and touchscreens draw once per structure, from their master part, in cabin-local space.
                if (part.state().is(ElevatorRegistries.DOOR) && ElevatorDoorBlock.isMaster(part.state()))
                    ElevatorDoorRenderer.renderPanels(part.state(), 0F, pose, buffers, partLight, overlay);
                if (ElevatorTouchscreenBlock.isMaster(part.state())) TouchscreenRenderer.renderCabinScreen(pulley, part, pose, buffers);
            }
            pose.popPose();
        }
    }
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, int overlay) {
        out.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, -1);
    }
    /**
     * NeoForge skips this renderer when its bounding box is out of view. It draws the rope and the
     * whole moving cabin, so the box must cover them, not just the pulley block: otherwise the
     * cabin blinks out whenever the pulley leaves the screen.
     */
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(PoweredPulleyBlockEntity pulley) { return renderBounds(pulley); }

    public static net.minecraft.world.phys.AABB renderBounds(PoweredPulleyBlockEntity pulley) {
        var bounds = new net.minecraft.world.phys.AABB(pulley.getBlockPos());
        var motion = pulley.motion;
        if (motion.active()) {
            var from = motion.cabinBounds(motion.displayPreviousY);
            var to = motion.cabinBounds(motion.displayY);
            if (from != null) bounds = bounds.minmax(from);
            if (to != null) bounds = bounds.minmax(to);
        } else if (pulley.attachment() != null) {
            bounds = bounds.minmax(new net.minecraft.world.phys.AABB(pulley.attachment()));
        }
        return bounds;
    }
    @Override public boolean shouldRenderOffScreen(PoweredPulleyBlockEntity pulley) { return true; }
    @Override public int getViewDistance() { return 256; }
}
