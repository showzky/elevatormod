package com.showzky.elevatormod;

import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Lets the elevator handle sneak-use before vanilla attempts item placement. */
@EventBusSubscriber(modid = ElevatorMod.MOD_ID)
public final class CamouflageInteractionHandler {
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown() && event.getItemStack().getItem() instanceof BlockItem
                && event.getLevel().getBlockState(event.getPos()).is(ElevatorRegistries.ELEVATOR_BLOCK)) {
            event.setUseBlock(TriState.TRUE);
        }
    }
    private CamouflageInteractionHandler() {}
}
