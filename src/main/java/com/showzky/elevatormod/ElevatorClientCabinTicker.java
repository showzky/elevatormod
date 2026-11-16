package com.showzky.elevatormod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Advances every loaded cabin's client-side motion once per client tick, after server packets
 * are handled and before entities tick. Riders are then carried by exactly this tick's cabin
 * movement (ElevatorClientPassengerCarry) and collide with the cabin where it is drawn.
 */
@EventBusSubscriber(modid = ElevatorMod.MOD_ID, value = Dist.CLIENT)
public final class ElevatorClientCabinTicker {
    @SubscribeEvent
    public static void beforeClientTick(ClientTickEvent.Pre event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) return;
        for (var pulley : ElevatorCabinMotion.loaded(minecraft.level)) pulley.motion.clientTick();
    }

    private ElevatorClientCabinTicker() {}
}
