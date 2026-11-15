package com.showzky.elevatormod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public record ElevatorControlPayload(int menu,String action,CompoundTag data) implements CustomPacketPayload {
 public static final Type<ElevatorControlPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID,"control"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ElevatorControlPayload> CODEC=StreamCodec.of((buf,p)->{buf.writeVarInt(p.menu);buf.writeUtf(p.action,32);buf.writeNbt(p.data);},buf->new ElevatorControlPayload(buf.readVarInt(),buf.readUtf(32),buf.readNbt()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playBidirectional(TYPE,CODEC,(packet,context)->{
  if(packet.data==null)return;
  if(!context.player().level().isClientSide && packet.action.equals("interact")) {
   var player=context.player();var start=player.getEyePosition();var end=start.add(player.getLookAngle().scale(5));
   for(var p:ElevatorCabinMotion.loaded(player.level())) for(var part:p.motion.parts) if(part.state().is(ElevatorRegistries.CONTROLLER)) {
    var pos=new net.minecraft.world.phys.Vec3(p.motion.origin.getX()+part.offset().getX(),p.motion.y+part.offset().getY(),p.motion.origin.getZ()+part.offset().getZ());
    var hit=new net.minecraft.world.phys.AABB(pos,pos.add(1,1,1)).clip(start,end);
    if(hit.isPresent() && player.level().clip(new net.minecraft.world.level.ClipContext(start,hit.get(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player)).getType()==net.minecraft.world.phys.HitResult.Type.MISS){ElevatorControlMenu.open(player,p.motion.origin.offset(part.offset()),2);return;}
   }
   return;
  }
  if(context.player().containerMenu instanceof ElevatorControlMenu menu && menu.containerId==packet.menu){
   if(context.player().level().isClientSide){if(packet.action.equals("state"))menu.state=packet.data;}
   else if(!packet.action.equals("state"))menu.action(packet.action,packet.data);
  }
 });}
}
