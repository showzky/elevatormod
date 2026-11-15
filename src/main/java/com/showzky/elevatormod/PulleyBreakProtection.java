package com.showzky.elevatormod;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Restore the stored cabin before survival or creative players remove its pulley. */
@EventBusSubscriber(modid = ElevatorMod.MOD_ID)
public final class PulleyBreakProtection {
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof PoweredPulleyBlockEntity pulley
                && pulley.motion.active() && !pulley.motion.disassemble()) {
            event.setCanceled(true);
            pulley.sync();
            event.getPlayer().displayClientMessage(Component.literal(pulley.motion.status + ". The pulley cannot be removed until the cabin is safely restored."), true);
        }
    }
    private PulleyBreakProtection() {}
}
