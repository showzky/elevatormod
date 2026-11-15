package com.showzky.elevatormod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws a doorway's two full-height sliding panels once, from its master cell. The panel models
 * come from the supplied Elevator_Doors_Fresh model and slide along the supplied keyframes; the
 * doorway's block positions never move.
 */
public final class ElevatorDoorRenderer implements BlockEntityRenderer<ElevatorDoorBlockEntity> {
    public static final ModelResourceLocation LEFT_PANEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "block/elevator_door_left_panel"));
    public static final ModelResourceLocation RIGHT_PANEL = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "block/elevator_door_right_panel"));

    public ElevatorDoorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(ElevatorDoorBlockEntity door, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderPanels(door.getBlockState(), door.progress(partialTick), pose, buffers, light, overlay);
    }

    /**
     * Renders both panels with the master cell's minimum corner at the pose origin.
     * @param progress animation progress from 0 (closed) to 1 (open)
     */
    public static void renderPanels(BlockState state, float progress, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = state.getValue(ElevatorDoorBlock.FACING);
        float offset = ElevatorDoorMotion.panelOffset(progress) / 16F;
        var minecraft = Minecraft.getInstance();
        var renderer = minecraft.getBlockRenderer().getModelRenderer();
        var buffer = buffers.getBuffer(RenderType.cutout());
        pose.pushPose();
        // The door model is authored facing north with the right panel towards +X.
        pose.translate(0.5F, 0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
        pose.translate(-0.5F, 0F, -0.5F);
        for (boolean right : new boolean[]{false, true}) {
            pose.pushPose();
            pose.translate(right ? offset : -offset, 0F, 0F);
            renderer.renderModel(pose.last(), buffer, state, minecraft.getModelManager().getModel(right ? RIGHT_PANEL : LEFT_PANEL), 1F, 1F, 1F, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override public AABB getRenderBoundingBox(ElevatorDoorBlockEntity door) {
        // Panels slide one block outward on each side of the two-wide doorway.
        return new AABB(door.getBlockPos()).inflate(2, 0, 2).expandTowards(0, 1, 0);
    }
}
