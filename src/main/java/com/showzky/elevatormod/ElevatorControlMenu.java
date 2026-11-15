package com.showzky.elevatormod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Configuration-only menu: no item slots or inventories. */
public final class ElevatorControlMenu extends AbstractContainerMenu {
 public final BlockPos pos;
 public final int kind;
 private final Player player;
 public CompoundTag state=new CompoundTag();
 public ElevatorControlMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,buf.readBlockPos(),buf.readVarInt());}
 public ElevatorControlMenu(int id,Inventory inv,BlockPos pos,int kind){super(ElevatorRegistries.OVERVIEW_MENU.get(),id);this.pos=pos;this.kind=kind;player=inv.player;}
 public static void open(Player player,BlockPos pos,int kind){player.openMenu(new SimpleMenuProvider((id,inv,p)->new ElevatorControlMenu(id,inv,pos,kind),Component.literal(kind==0?"Powered Pulley":kind==1?"Floor Connector":"Elevator Controller")),buf->{buf.writeBlockPos(pos);buf.writeVarInt(kind);});}
 public PoweredPulleyBlockEntity pulley(){
  if(kind==0 && player.level().getBlockEntity(pos) instanceof PoweredPulleyBlockEntity p)return p;
  for(var p:ElevatorCabinMotion.loaded(player.level()))if(p.motion.active())for(var part:p.motion.parts)
   if(part.state().is(ElevatorRegistries.CONTROLLER) && p.motion.origin.offset(part.offset()).equals(pos))return p;
  if(kind==2) for(var p:ElevatorCabinMotion.loaded(player.level())) if(!p.motion.active()) {
   var scan=ElevatorAssembly.scan(player.level(),p.getBlockPos());
   if(scan.attachment()!=null && scan.components().contains(pos))return p;
  }
  return null;
 }
 @Override public boolean stillValid(Player p){
  var pulley=pulley();
  if(kind==2 && pulley!=null && pulley.motion.active()){for(var part:pulley.motion.parts)if(part.state().is(ElevatorRegistries.CONTROLLER))return p.distanceToSqr(pulley.motion.origin.getX()+part.offset().getX()+0.5,pulley.motion.y+part.offset().getY()+0.5,pulley.motion.origin.getZ()+part.offset().getZ()+0.5)<64;}
  return p.level().hasChunkAt(pos)&&p.distanceToSqr(pos.getCenter())<64&&(kind==0?p.level().getBlockState(pos).is(ElevatorRegistries.PULLEY):p.level().getBlockState(pos).is(kind==1?ElevatorRegistries.FLOOR_CONNECTOR:ElevatorRegistries.CONTROLLER));
 }
 @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
 @Override public void broadcastChanges(){
  super.broadcastChanges();if(!(player instanceof ServerPlayer server))return;
  CompoundTag next=new CompoundTag();next.putInt("Height",pos.getY());
  if(kind==1 && player.level().getBlockEntity(pos) instanceof FloorConnectorBlockEntity be) {
   next.putString("Name",be.name);
   boolean connected=ElevatorCabinMotion.loaded(player.level()).stream().anyMatch(pulley -> {
    if(pulley.motion.active()) return pulley.motion.floors().stream().anyMatch(f -> f.pos().equals(pos));
    var scan=ElevatorAssembly.scan(player.level(),pulley.getBlockPos());
    return scan.attachment()!=null && ElevatorFloorDiscovery.discover(player.level(),pulley.getBlockPos(),scan.attachment(),scan.components()).floors().stream().anyMatch(f -> f.connector().equals(pos));
   });
   next.putBoolean("Connected",connected);
  }
  var p=pulley();
  if(p!=null){next.putInt("Energy",p.energy().getEnergyStored());next.putInt("Capacity",p.energy().getMaxEnergyStored());next.putBoolean("Active",p.motion.active());next.putBoolean("CanTravel",p.motion.active());next.putDouble("Speed",p.motion.speed);next.putString("Status",p.motion.status);next.putDouble("Y",p.motion.y);next.putDouble("Target",p.motion.target);
   ListTag floors=new ListTag();
   if(p.motion.active()) {
    for(var floor:p.motion.floors()){CompoundTag f=new CompoundTag();f.putInt("Y",floor.y());f.putString("Name",floor.name());floors.add(f);}
   } else if(kind==2) {
    var scan=ElevatorAssembly.scan(player.level(),p.getBlockPos());
    if(scan.attachment()!=null){
     next.putDouble("Y",scan.attachment().getY());next.putString("Status","Ready");next.putBoolean("CanTravel",true);
     for(var floor:ElevatorFloorDiscovery.discover(player.level(),p.getBlockPos(),scan.attachment(),scan.components()).floors()){
      CompoundTag f=new CompoundTag();f.putInt("Y",floor.attachmentY());
      var be=player.level().getBlockEntity(floor.connector());String name=be instanceof FloorConnectorBlockEntity connector?connector.name:"";
      f.putString("Name",name.isBlank()?"Floor Y="+floor.connector().getY():name);floors.add(f);
     }
    }
   }
   next.put("Floors",floors);
  }else next.putString("Status","Not assembled");
  if(!next.equals(state)){state=next;net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server,new ElevatorControlPayload(containerId,"state",next));}
 }
 public void action(String action,CompoundTag data){
  if(!stillValid(player)||player.isSpectator())return;
  if(kind==1 && action.equals("name") && player.level().getBlockEntity(pos) instanceof FloorConnectorBlockEntity be){String value=data.getString("Name");if(value.length()<=48){be.name=value.strip().replaceAll("[\\p{Cntrl}]", "");be.setChanged();}}
  var p=pulley();if(p==null)return;
  if(kind==0){
   if(action.equals("assemble"))p.motion.assemble();
   if(action.equals("disassemble"))p.motion.disassemble();
   if(action.equals("speed")){p.motion.speed=ElevatorEnergyUsage.sanitizeSpeed(data.getDouble("Speed"));p.setChanged();p.sync();}
  }
  if(kind==2 && action.equals("travel")) {
   int stop=data.getInt("Y");
   if(!p.motion.active()) {
    var scan=ElevatorAssembly.scan(player.level(),p.getBlockPos());
    if(scan.attachment()==null || !scan.components().contains(pos) || ElevatorFloorDiscovery.discover(player.level(),p.getBlockPos(),scan.attachment(),scan.components()).floors().stream().noneMatch(f -> f.attachmentY()==stop)) {
     player.displayClientMessage(Component.literal("Destination or cabin is no longer valid"),false);return;
    }
    if(!p.motion.assemble()) {p.sync();player.displayClientMessage(Component.literal(p.motion.status),false);return;}
   }
   p.motion.request(stop);
  }
 }
}
