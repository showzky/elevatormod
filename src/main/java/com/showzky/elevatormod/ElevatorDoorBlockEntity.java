package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lives on a doorway's lower-left master cell so the two full-height panels are rendered once
 * per doorway. Holds only client-side animation progress; door state stays in the block states.
 */
public final class ElevatorDoorBlockEntity extends BlockEntity {
    private static final float STEP = 1F / ElevatorDoorMotion.TRAVEL_TICKS;
    private float progress, previousProgress;
    private boolean animationStarted;

    public ElevatorDoorBlockEntity(BlockPos pos, BlockState state) { super(ElevatorRegistries.DOOR_ENTITY.get(), pos, state); }

    /**
     * Advances the visual progress at the clip's speed. While closing it never runs more than
     * about one frame ahead of the server's collision frame, so panels hold where the server
     * holds them (for example while someone stands in the doorway).
     */
    public void clientTick(BlockState state) {
        float serverProgress = state.getValue(ElevatorDoorBlock.FRAME) / (float) ElevatorDoorMotion.FRAMES;
        if (!animationStarted) {
            progress = previousProgress = serverProgress;
            animationStarted = true;
            return;
        }
        previousProgress = progress;
        if (state.getValue(ElevatorDoorBlock.OPEN)) progress = Math.min(1F, progress + STEP);
        else {
            float lowest = serverProgress - 1.5F / ElevatorDoorMotion.FRAMES;
            progress = Math.max(Math.max(0F, progress - STEP), Math.min(previousProgress, lowest));
        }
    }

    public float progress(float partialTick) { return previousProgress + (progress - previousProgress) * partialTick; }
}
