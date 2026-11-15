package com.showzky.elevatormod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** One positional loop for one loaded, moving elevator. */
public final class ElevatorMovementSound extends AbstractTickableSoundInstance {
    private final PoweredPulleyBlockEntity pulley;
    public ElevatorMovementSound(PoweredPulleyBlockEntity pulley) {
        super(ElevatorSoundEvents.MOVING.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.pulley = pulley;
        looping = true;
        delay = 0;
        updatePosition();
    }
    public boolean shouldContinue() {
        var level=Minecraft.getInstance().level;
        return level!=null && pulley.getLevel()==level && !pulley.isRemoved()
                && level.hasChunkAt(pulley.getBlockPos()) && pulley.motion.active()
                && (pulley.motion.status.equals("Going up") || pulley.motion.status.equals("Going down"));
    }
    private void updatePosition() {
        x=pulley.motion.origin.getX()+0.5;
        y=pulley.motion.displayY+0.5;
        z=pulley.motion.origin.getZ()+0.5;
    }
    @Override public void tick() {
        if(!shouldContinue()) { stop();return; }
        updatePosition();
    }
}
