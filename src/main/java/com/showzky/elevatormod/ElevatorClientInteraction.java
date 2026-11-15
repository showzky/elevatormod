package com.showzky.elevatormod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
@EventBusSubscriber(modid=ElevatorMod.MOD_ID,value=Dist.CLIENT)
public final class ElevatorClientInteraction {
 @SubscribeEvent public static void click(PlayerInteractEvent.RightClickEmpty event){net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ElevatorControlPayload(-1,"interact",new net.minecraft.nbt.CompoundTag()));}
}
