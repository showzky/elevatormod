package com.showzky.elevatormod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/**
 * Renders live content on stationary touchscreens. Screens inside a moving cabin are drawn by
 * the pulley renderer through {@link #renderCabinScreen} at the cabin's interpolated position.
 */
public final class TouchscreenRenderer implements BlockEntityRenderer<ElevatorTouchscreenBlockEntity> {
    /** Beyond this distance text is unreadable; the housing and glass still render as a block model. */
    public static final int CONTENT_VIEW_DISTANCE = 48;

    public TouchscreenRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(ElevatorTouchscreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (screen.getLevel() == null) return;
        TouchscreenDisplayPainter.paint(pose, buffers, screen.size(), screen.facing(), screen.settings(), screen.displayState(), screen.getLevel().getGameTime());
    }

    public static void renderCabinScreen(PoweredPulleyBlockEntity pulley, ElevatorCabinMotion.Part part, PoseStack pose, MultiBufferSource buffers) {
        if (!(part.state().getBlock() instanceof ElevatorTouchscreenBlock block) || !ElevatorTouchscreenBlock.isMaster(part.state()) || pulley.getLevel() == null) return;
        var state = TouchscreenDisplayState.of(TouchscreenDisplayState.Connection.CABIN, pulley, TouchscreenDisplayState.NO_STOP);
        TouchscreenDisplayPainter.paint(pose, buffers, block.size(), part.state().getValue(ElevatorTouchscreenBlock.FACING),
                TouchscreenSettings.read(part.data()), state, pulley.getLevel().getGameTime());
    }

    @Override public AABB getRenderBoundingBox(ElevatorTouchscreenBlockEntity screen) {
        // Covers a 2x2 display in any facing from its bottom-left master cell.
        return new AABB(screen.getBlockPos()).inflate(1, 0, 1).expandTowards(0, 1, 0);
    }

    @Override public int getViewDistance() { return CONTENT_VIEW_DISTANCE; }
}
