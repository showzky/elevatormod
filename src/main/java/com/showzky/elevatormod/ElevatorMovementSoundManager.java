package com.showzky.elevatormod;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Starts and stops one loop per loaded pulley as authoritative movement changes. */
@EventBusSubscriber(modid = ElevatorMod.MOD_ID, value = Dist.CLIENT)
public final class ElevatorMovementSoundManager {
    private static final Map<PoweredPulleyBlockEntity, ElevatorMovementSound> ACTIVE = new IdentityHashMap<>();
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        var minecraft=Minecraft.getInstance();
        var sounds=minecraft.getSoundManager();
        ACTIVE.entrySet().removeIf(entry -> {
            if(entry.getValue().shouldContinue() && (sounds.isActive(entry.getValue()) || minecraft.level.getGameTime()%20!=0)) return false;
            sounds.stop(entry.getValue());return true;
        });
        if(minecraft.level==null)return;
        for(var pulley:ElevatorCabinMotion.loaded(minecraft.level)) {
            if(ACTIVE.containsKey(pulley))continue;
            var loop=new ElevatorMovementSound(pulley);
            if(loop.shouldContinue()) { ACTIVE.put(pulley,loop);sounds.play(loop); }
        }
    }
    private ElevatorMovementSoundManager() {}
}
