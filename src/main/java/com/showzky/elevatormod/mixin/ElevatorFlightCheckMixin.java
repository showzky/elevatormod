package com.showzky.elevatormod.mixin;

import com.showzky.elevatormod.ElevatorCabinMotion;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A moving cabin is not made of world blocks, so the server's anti-flight check would see a
 * rider standing on air and kick them after a few seconds on servers without allow-flight.
 * Treat moving cabin parts under the player like blocks for that check.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ElevatorFlightCheckMixin {
    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
    private void elevatormod$cabinCountsAsGround(Entity entity, CallbackInfoReturnable<Boolean> result) {
        var area = entity.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0);
        if (!ElevatorCabinMotion.collisions(entity.level(), area).isEmpty()
                // While descending fast the server's cabin leads the player's reported position.
                || entity instanceof net.minecraft.world.entity.player.Player player && ElevatorCabinMotion.carriesPlayer(player))
            result.setReturnValue(false);
    }
}
