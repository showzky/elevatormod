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
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "textures/block/elevator_rope.png");
    public ElevatorRopeRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(PoweredPulleyBlockEntity pulley, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (pulley.attachment() == null) return;
        int length = pulley.getBlockPos().getY() - pulley.attachment().getY() - 1;
        if (length <= 0) return;
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        for (int segment = 0; segment < length; segment++) {
            pose.pushPose();
            pose.translate(0, -segment - 1, 0);
            for (int side = 0; side < 4; side++) {
                PoseStack.Pose current = pose.last();
                vertex(vertices, current, -0.0625f, 0, -0.0625f, 0, 1, light, overlay);
                vertex(vertices, current, -0.0625f, 1, -0.0625f, 0, 0, light, overlay);
                vertex(vertices, current, 0.0625f, 1, -0.0625f, 0.5f, 0, light, overlay);
                vertex(vertices, current, 0.0625f, 0, -0.0625f, 0.5f, 1, light, overlay);
                pose.mulPose(Axis.YP.rotationDegrees(90));
            }
            pose.popPose();
        }
        pose.popPose();
    }
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, int overlay) {
        out.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, -1);
    }
    @Override public boolean shouldRenderOffScreen(PoweredPulleyBlockEntity pulley) { return true; }
    @Override public int getViewDistance() { return 256; }
}
