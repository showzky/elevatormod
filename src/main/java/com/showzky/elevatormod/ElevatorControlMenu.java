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
  if(p!=null){next.putInt("Energy",p.energy().getEnergyStored());next.putInt("Capacity",p.energy().getMaxEnergyStored());next.putBoolean("Active",p.motion.active());next.putBoolean("Connected",p.attachment()!=null);next.putBoolean("CanTravel",p.motion.active());next.putDouble("Speed",p.motion.speed);next.putString("Status",p.motion.status);next.putDouble("Y",p.motion.y);next.putDouble("Target",p.motion.target);
   if(kind==2) {
    // The controller is the elevator's settings panel: status, speed and door behaviour.
    next.putInt("FloorCount",p.directory().floors().size());
    next.putBoolean("AutoOpen",p.motion.doorSettings.autoOpen());
    next.putBoolean("AutoClose",p.motion.doorSettings.autoClose());
    next.putInt("CloseDelay",p.motion.doorSettings.closeDelaySeconds());
   }
  }else next.putString("Status","Not connected");
  if(!next.equals(state)){state=next;net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server,new ElevatorControlPayload(containerId,"state",next));}
 }
 public void action(String action,CompoundTag data){
  if(!stillValid(player)||player.isSpectator())return;
  if(kind==1 && action.equals("name") && player.level().getBlockEntity(pos) instanceof FloorConnectorBlockEntity be){String value=data.getString("Name");if(value.length()<=48){be.name=value.strip().replaceAll("[\\p{Cntrl}]", "");be.setChanged();}}
  var p=pulley();if(p==null)return;
  // Speed is one shared setting, adjustable from the pulley and from the controller.
  if((kind==0 || kind==2) && action.equals("speed")){p.motion.speed=ElevatorEnergyUsage.sanitizeSpeed(data.getDouble("Speed"));p.setChanged();p.sync();}
  if(kind==0){
   // Connecting links the pulley and lists floors; the cabin stays as real blocks until it travels.
   if(action.equals("connect") && !p.motion.active()){
    var scan=ElevatorAssembly.scan(player.level(),p.getBlockPos());
    if(scan.attachment()!=null){p.connect(scan);p.motion.status="Connected";}
    else p.motion.status="Connection failed: "+scan.error();
    p.sync();
   }
   if(action.equals("park") && p.motion.active() && !p.motion.disassemble())p.sync();
  }
  if(kind==2) {
   var doors=p.motion.doorSettings;
   if(action.equals("autoOpen")) doors=doors.withAutoOpen(data.getBoolean("Value"));
   else if(action.equals("autoClose")) doors=doors.withAutoClose(data.getBoolean("Value"));
   else if(action.equals("closeDelay")) doors=doors.withCloseDelay(data.getInt("Value"));
   if(!doors.equals(p.motion.doorSettings)){p.motion.doorSettings=doors;p.setChanged();p.sync();}
  }
 }
}
