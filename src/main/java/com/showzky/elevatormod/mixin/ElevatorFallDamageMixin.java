package com.showzky.elevatormod.mixin;

import com.showzky.elevatormod.ElevatorCabinMotion;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server adds fall distance from each movement packet the client reports as airborne. A fast
 * descending cabin occasionally produces such packets, and the total was dealt as fall damage on
 * arrival. Like Create does for contraption riders, a player riding a cabin starts every packet
 * with no fall distance, so at most one packet's movement counts, which never causes damage.
 * Falls outside the cabin still count.
 */
@Mixin(ServerPlayer.class)
public abstract class ElevatorFallDamageMixin {
    @Inject(method = "doCheckFallDamage", at = @At("HEAD"))
    private void elevatormod$ridersDoNotAccumulateFalls(double movementX, double movementY, double movementZ, boolean onGround, CallbackInfo info) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (ElevatorCabinMotion.carriesPlayer(player)) player.resetFallDistance();
    }
}
