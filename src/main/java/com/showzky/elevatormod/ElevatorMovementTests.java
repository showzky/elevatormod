package com.showzky.elevatormod;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorMovementTests {
 private static final class ArrivalProbe {
  final net.minecraft.world.level.Level level;final BlockPos origin;int count;double y;
  ArrivalProbe(net.minecraft.world.level.Level level,BlockPos origin){this.level=level;this.origin=origin;}
  @net.neoforged.bus.api.SubscribeEvent public void onSound(net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition event){
   if(event.getLevel()==level && event.getSound()!=null && event.getSound().value()==ElevatorSoundEvents.ARRIVAL.get()
      && Math.abs(event.getPosition().x-(origin.getX()+0.5))<0.01 && Math.abs(event.getPosition().z-(origin.getZ()+0.5))<0.01){count++;y=event.getPosition().y;}
  }
 }
 @GameTest(template="empty",timeoutTicks=100)
 public static void callButtonAmbiguous(GameTestHelper test) {
  var level=test.getLevel();BlockPos first=test.absolutePos(new BlockPos(2,2,1));
  for(int xOffset:new int[]{0,4}) {
   BlockPos origin=first.south(xOffset),pulleyPos=origin.above(7);
   level.setBlock(pulleyPos,ElevatorRegistries.PULLEY.get().defaultBlockState(),3);
   level.setBlock(origin,ElevatorRegistries.ATTACHMENT.get().defaultBlockState(),3);
   level.setBlock(origin.below(),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
   level.setBlock(origin.west(),ElevatorRegistries.CONTROLLER.get().defaultBlockState(),3);
   level.setBlock(origin.east(),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.EAST),3);
   level.setBlock(origin.east(2),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.WEST),3);
   var pulley=(PoweredPulleyBlockEntity)level.getBlockEntity(pulleyPos);
   test.assertTrue(pulley.motion.assemble(),"Elevator must connect: "+pulley.motion.status);
   test.assertTrue(pulley.motion.disassemble(),"Elevator must park: "+pulley.motion.status);
  }
  BlockPos button=first.east(4).south(2);
  level.setBlock(button,ElevatorRegistries.CALL_BUTTON.get().defaultBlockState(),3);
  var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
  var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(button),Direction.UP,button,false);
  ((ElevatorCallButtonBlock)ElevatorRegistries.CALL_BUTTON.get()).useWithoutItem(level.getBlockState(button),level,button,player,hit);
  test.assertTrue(!level.getBlockState(button).getValue(ElevatorCallButtonBlock.POWERED),"Ambiguous button must not accept a call");
  for(int xOffset:new int[]{0,4}) {
   var pulley=(PoweredPulleyBlockEntity)level.getBlockEntity(first.south(xOffset).above(7));
   test.assertTrue(!pulley.motion.active(),"Ambiguous button must not start either elevator");
  }
  test.succeed();
 }
 @GameTest(template="empty",timeoutTicks=260)
 public static void callButtonAndLight(GameTestHelper test) {
  var level=test.getLevel();BlockPos origin=test.absolutePos(new BlockPos(3,2,3));BlockPos pulleyPos=test.absolutePos(new BlockPos(3,9,3));
  test.assertTrue(ElevatorCallButtonBlock.nearby(new BlockPos(0,level.getMinBuildHeight(),0),new BlockPos(0,level.getMaxBuildHeight()-1,0)),"Button range must include the full world height");
  test.assertTrue(!ElevatorCallButtonBlock.nearby(new BlockPos(0,level.getMinBuildHeight(),0),new BlockPos(9,level.getMaxBuildHeight()-1,0)),"Horizontal range must still be enforced");
  level.setBlock(pulleyPos,ElevatorRegistries.PULLEY.get().defaultBlockState(),3);
  level.setBlock(origin,ElevatorRegistries.ATTACHMENT.get().defaultBlockState(),3);
  level.setBlock(origin.below(),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  BlockPos lightPos=origin.below().south();level.setBlock(lightPos,ElevatorRegistries.LIGHT.get().defaultBlockState(),3);
  level.setBlock(origin.west(),ElevatorRegistries.CONTROLLER.get().defaultBlockState(),3);
  level.setBlock(origin.east(),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.EAST),3);
  level.setBlock(origin.east(2),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.WEST),3);
  level.setBlock(origin.east(2).above(3),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.WEST),3);
  BlockPos lowerButton=origin.east(3),upperButton=origin.east(7).above(9);
  level.setBlock(lowerButton,ElevatorRegistries.CALL_BUTTON.get().defaultBlockState(),3);
  level.setBlock(upperButton,ElevatorRegistries.CALL_BUTTON.get().defaultBlockState(),3);
  var pulley=(PoweredPulleyBlockEntity)level.getBlockEntity(pulleyPos);
  test.assertTrue(pulley.motion.assemble(),"Elevator must connect");
  test.assertTrue(pulley.motion.parts.stream().anyMatch(part -> part.state().is(ElevatorRegistries.LIGHT)),"Light must join cabin");
  var offLights=ElevatorCabinLighting.poweredLights(pulley.motion.parts);
  test.assertTrue(offLights.isEmpty() && ElevatorCabinLighting.blockLight(BlockPos.ZERO,offLights,4)==4,"Unpowered fixture must preserve ambient light");
  test.assertTrue(pulley.motion.disassemble(),"Elevator must park before call");
  pulley.energy().receiveEnergy(10000,false);int before=pulley.energy().getEnergyStored();ElevatorLightPower.tick(pulley);
  test.assertTrue(level.getBlockState(lightPos).getValue(ElevatorLightBlock.LIT) && pulley.energy().getEnergyStored()==before-1,"Powered parked fixture must draw FE and emit light");
  var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
  var button=(ElevatorCallButtonBlock)ElevatorRegistries.CALL_BUTTON.get();
  var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(upperButton),Direction.UP,upperButton,false);
  button.useWithoutItem(level.getBlockState(upperButton),level,upperButton,player,hit);
  test.assertTrue(pulley.motion.active() && pulley.motion.hasCall(origin.getY()+3),"Upper call from six blocks above its connector must assemble and start trip");
  test.assertTrue(level.getBlockState(upperButton).getValue(ElevatorCallButtonBlock.POWERED),"Call indicator must activate");
  var lowerHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(lowerButton),Direction.UP,lowerButton,false);
  button.useWithoutItem(level.getBlockState(lowerButton),level,lowerButton,player,lowerHit);
  test.assertTrue(pulley.motion.hasCall(origin.getY()),"Call to lower floor must queue during travel");
  button.useWithoutItem(level.getBlockState(lowerButton),level,lowerButton,player,lowerHit);
  int powerBefore=pulley.energy().getEnergyStored();ElevatorLightPower.tick(pulley);
  test.assertTrue(pulley.energy().getEnergyStored()==powerBefore-1 && pulley.motion.parts.stream().anyMatch(part -> part.state().is(ElevatorRegistries.LIGHT) && part.state().getValue(ElevatorLightBlock.LIT)),"Moving fixture must draw power and retain lit state");
  var lights=ElevatorCabinLighting.poweredLights(pulley.motion.parts);
  test.assertTrue(lights.size()==1 && ElevatorCabinLighting.blockLight(lights.getFirst(),lights,0)==15,"Powered moving fixture must illuminate its own position");
  test.assertTrue(ElevatorCabinLighting.blockLight(lights.getFirst().east(3),lights,0)==12,"Cabin light must diminish with distance");
  test.assertTrue(ElevatorCabinLighting.blockLight(lights.getFirst().east(3),lights,14)==14,"Cabin light must preserve brighter ambient light");
  pulley.consume(100000);ElevatorLightPower.tick(pulley);
  test.assertTrue(pulley.motion.parts.stream().anyMatch(part -> part.state().is(ElevatorRegistries.LIGHT) && !part.state().getValue(ElevatorLightBlock.LIT)),"Moving light must switch off without FE");
  pulley.energy().receiveEnergy(10000,false);ElevatorLightPower.tick(pulley);
  test.assertTrue(pulley.motion.parts.stream().anyMatch(part -> part.state().is(ElevatorRegistries.LIGHT) && part.state().getValue(ElevatorLightBlock.LIT)),"Moving light must switch back on after power returns");
  var tag=new net.minecraft.nbt.CompoundTag();pulley.motion.save(tag);pulley.motion.load(tag);
  test.assertTrue(pulley.motion.hasCall(origin.getY()) && tag.getIntArray("CallQueue").length==1,"Queued calls must survive save/load without duplicates");
  test.runAfterDelay(190,()->{
   test.assertTrue(!pulley.motion.active() && pulley.attachment().equals(origin),"Elevator must finish queued call at lower floor: "+pulley.motion.status);
   test.assertTrue(!level.getBlockState(lowerButton).getValue(ElevatorCallButtonBlock.POWERED) && !level.getBlockState(upperButton).getValue(ElevatorCallButtonBlock.POWERED),"Call indicators must clear on arrival");
   test.assertTrue(level.getBlockState(lightPos).is(ElevatorRegistries.LIGHT),"Light must restore with cabin");
   test.succeed();
  });
 }
 @GameTest(template="empty",timeoutTicks=100)
 public static void camouflageInteractions(GameTestHelper test) {
  var level=test.getLevel();BlockPos pos=test.absolutePos(new BlockPos(3,2,3));
  level.setBlock(pos,ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  var block=(ElevatorBlock)ElevatorRegistries.ELEVATOR_BLOCK.get();var be=(ElevatorBlockEntity)level.getBlockEntity(pos);
  var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
  player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.getInventory().clearContent();player.setShiftKeyDown(false);
  var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);
  var stone=new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.STONE,3);
  player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stone);
  var normalEvent=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,pos,hit);
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(normalEvent);
  test.assertTrue(normalEvent.getUseBlock()==net.neoforged.neoforge.common.util.TriState.DEFAULT,"Ordinary right-click must retain normal block placement");
  test.assertTrue(block.useItemOn(stone,level.getBlockState(pos),level,pos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)==net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION && stone.getCount()==3 && be.camouflage()==null,"Ordinary use must not apply camouflage");
  player.gameMode.useItemOn(player,level,stone,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
  test.assertTrue(level.getBlockState(pos.above()).is(net.minecraft.world.level.block.Blocks.STONE) && stone.getCount()==2 && be.camouflage()==null,"Ordinary right-click must place the held block beside the elevator block");
  level.removeBlock(pos.above(),false);stone.setCount(3);
  player.setShiftKeyDown(true);
  var sneakEvent=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,pos,hit);
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(sneakEvent);
  test.assertTrue(sneakEvent.getUseBlock()==net.neoforged.neoforge.common.util.TriState.TRUE,"Sneak use must reach the elevator block through vanilla's secondary-use handling");
  player.gameMode.useItemOn(player,level,stone,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
  test.assertTrue(stone.getCount()==2 && be.camouflage().is(net.minecraft.world.level.block.Blocks.STONE),"Applying camouflage must consume one");
  var clientCopy=new ElevatorBlockEntity(pos,level.getBlockState(pos));
  clientCopy.loadWithComponents(be.getUpdateTag(level.registryAccess()),level.registryAccess());
  test.assertTrue(clientCopy.camouflage()!=null && clientCopy.camouflage().is(net.minecraft.world.level.block.Blocks.STONE),"Update tag must apply camouflage to a receiving block entity");
  block.useItemOn(stone,level.getBlockState(pos),level,pos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
  test.assertTrue(stone.getCount()==2,"Same camouflage must not consume another item");
  var dirt=new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.DIRT,2);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,dirt);
  block.useItemOn(dirt,level.getBlockState(pos),level,pos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
  test.assertTrue(dirt.getCount()==1 && be.camouflage().is(net.minecraft.world.level.block.Blocks.DIRT) && player.getInventory().contains(new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.STONE)),"Swap must consume one and return old material");
  for(var invalid:java.util.List.of(net.minecraft.world.level.block.Blocks.CHEST,net.minecraft.world.level.block.Blocks.GLASS,net.minecraft.world.level.block.Blocks.STONE_SLAB,ElevatorRegistries.ELEVATOR_BLOCK.get())) {
   var stack=new net.minecraft.world.item.ItemStack(invalid,2);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
   block.useItemOn(stack,level.getBlockState(pos),level,pos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
   test.assertTrue(stack.getCount()==2 && be.camouflage().is(net.minecraft.world.level.block.Blocks.DIRT),"Invalid camouflage must not consume or change state: "+invalid);
  }
  player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);player.setShiftKeyDown(true);
  block.useWithoutItem(level.getBlockState(pos),level,pos,player,hit);
  test.assertTrue(be.camouflage()==null && player.getInventory().contains(new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.DIRT)),"Sneak removal must return material");
  var clearTag=be.getUpdateTag(level.registryAccess());
  test.assertTrue(!clearTag.isEmpty(),"Camouflage removal update must not be discarded as an empty packet");
  clientCopy.loadWithComponents(clearTag,level.registryAccess());
  test.assertTrue(clientCopy.camouflage()==null,"Removal update must clear camouflage on a receiving block entity");
  player.setShiftKeyDown(true);player.getInventory().clearContent();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  var creative=new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.STONE,2);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,creative);
  block.useItemOn(creative,level.getBlockState(pos),level,pos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
  test.assertTrue(creative.getCount()==2 && be.camouflage().is(net.minecraft.world.level.block.Blocks.STONE),"Creative application must not consume items");
  var drops=net.minecraft.world.level.block.Block.getDrops(level.getBlockState(pos),level,pos,be,player,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
  test.assertTrue(drops.stream().anyMatch(stack -> stack.is(ElevatorRegistries.ELEVATOR_BLOCK.get().asItem())) && drops.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.STONE)),"Break drops must include elevator and camouflage blocks");
  player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);player.setShiftKeyDown(true);
  block.useWithoutItem(level.getBlockState(pos),level,pos,player,hit);
  test.assertTrue(be.camouflage()==null && !player.getInventory().contains(new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.STONE)),"Creative removal must not give a material");
  player.setShiftKeyDown(false);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.getInventory().clearContent();test.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100)
 public static void parkRetry(GameTestHelper test) {
  var level=test.getLevel();BlockPos origin=test.absolutePos(new BlockPos(3,2,3));BlockPos pulleyPos=test.absolutePos(new BlockPos(3,9,3));
  level.setBlock(pulleyPos,ElevatorRegistries.PULLEY.get().defaultBlockState(),3);
  level.setBlock(origin,ElevatorRegistries.ATTACHMENT.get().defaultBlockState(),3);
  level.setBlock(origin.below(),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  level.setBlock(origin.west(),ElevatorRegistries.CONTROLLER.get().defaultBlockState(),3);
  level.setBlock(origin.east(),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.EAST),3);
  level.setBlock(origin.east(2),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.WEST),3);
  var pulley=(PoweredPulleyBlockEntity)level.getBlockEntity(pulleyPos);
  test.assertTrue(pulley.motion.assemble(),"Manual assembly must succeed");
  level.setBlock(origin,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
  pulley.motion.tick();
  test.assertTrue(pulley.motion.active() && pulley.motion.status.equals("Restoration blocked"),"Blocked parking must retain cabin and report status");
  level.removeBlock(origin,false);
  test.runAfterDelay(25,()->{
   test.assertTrue(!pulley.motion.active() && level.getBlockState(origin).is(ElevatorRegistries.ATTACHMENT),"Parking must retry and restore cabin");
   test.succeed();
  });
 }
 @GameTest(template="empty",timeoutTicks=240)
 public static void elevatedConnector(GameTestHelper test) {
  var level=test.getLevel();
  BlockPos origin=test.absolutePos(new BlockPos(3,2,3));
  BlockPos pulleyPos=test.absolutePos(new BlockPos(3,9,3));
  level.setBlock(pulleyPos,ElevatorRegistries.PULLEY.get().defaultBlockState(),3);
  level.setBlock(origin,ElevatorRegistries.ATTACHMENT.get().defaultBlockState(),3);
  level.setBlock(origin.below(),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  level.setBlock(origin.west(),ElevatorRegistries.CONTROLLER.get().defaultBlockState(),3);
  level.setBlock(origin.east(),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  level.setBlock(origin.east().above(),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.EAST),3);
  level.setBlock(origin.east(2).above(),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.WEST),3);
  BlockPos top=new BlockPos(origin.getX()+2,pulleyPos.getY(),origin.getZ());
  level.setBlock(top,ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.WEST),3);
  var pulley=(PoweredPulleyBlockEntity)level.getBlockEntity(pulleyPos);
  var display=new ElevatorCabinMotion(pulley);
  display.y=10;display.clientTick();
  test.assertTrue(display.displayPreviousY==10 && display.displayY==10,"Initial client position must snap");
  display.y=11;display.clientTick();
  test.assertTrue(display.displayPreviousY==10 && display.displayY==11,"Client positions must preserve the interpolation interval");
  test.assertTrue(net.minecraft.util.Mth.lerp(0.5,display.displayPreviousY,display.displayY)==10.5,"Partial tick must interpolate the cabin");
  display.y=20;display.clientTick();
  test.assertTrue(display.displayPreviousY==20 && display.displayY==20,"Large updates must snap display coordinates");

  var scan=ElevatorAssembly.scan(level,pulleyPos);
  test.assertTrue(scan.attachment()!=null,"Cabin must scan");
  var before=ElevatorFloorDiscovery.discover(level,pulleyPos,scan.attachment(),scan.components()).floors().stream().map(ElevatorFloorDiscovery.Floor::attachmentY).collect(java.util.stream.Collectors.toSet());
  test.assertTrue(before.contains(pulleyPos.getY()-1),"Connector at pulley height must be detected");
  test.assertTrue(pulley.motion.assemble(),"Cabin must assemble");
  var after=pulley.motion.floors().stream().map(ElevatorCabinMotion.Floor::y).collect(java.util.stream.Collectors.toSet());
  test.assertTrue(before.equals(after),"Floor stops must match before and after assembly: "+before+" vs "+after);
  pulley.energy().receiveEnergy(10000,false);
  var probe=new ArrivalProbe(level,origin);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(probe);
  pulley.motion.request(pulleyPos.getY()-1);
  test.runAfterDelay(150,()->{
   test.assertTrue(Math.abs(pulley.motion.y-(pulleyPos.getY()-1))<0.001,"Elevator must reach the top floor: "+pulley.motion.status);
   test.assertTrue(probe.count==1 && Math.abs(probe.y-(pulleyPos.getY()-0.5))<0.001,"Arrival chime must play exactly once at the cabin: "+probe.count);
   net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(probe);
   test.succeed();
  });
 }
 @GameTest(template="empty",timeoutTicks=240)
 public static void roundTrip(GameTestHelper test){
  var level=test.getLevel();
  BlockPos pulleyPos=test.absolutePos(new BlockPos(3,9,3));
  BlockPos origin=test.absolutePos(new BlockPos(3,2,3));
  level.setBlock(pulleyPos,ElevatorRegistries.PULLEY.get().defaultBlockState(),3);
  level.setBlock(origin,ElevatorRegistries.ATTACHMENT.get().defaultBlockState(),3);
  level.setBlock(origin.below(),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  level.setBlock(origin.below().south(),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  level.setBlock(origin.south(2).below(),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  level.setBlock(origin.south(2),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  level.setBlock(origin.south(2).above(),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  level.setBlock(origin.south(2).above(2),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  level.setBlock(origin.south().above(2),ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(),3);
  BlockPos doorRoot=origin.south(3);
  for(var part:ElevatorDoorBlock.Part.values()) {
   boolean right=part==ElevatorDoorBlock.Part.LOWER_RIGHT || part==ElevatorDoorBlock.Part.UPPER_RIGHT;
   boolean upper=part==ElevatorDoorBlock.Part.UPPER_LEFT || part==ElevatorDoorBlock.Part.UPPER_RIGHT;
   BlockPos doorPos=doorRoot.east(right?1:0).above(upper?1:0);
   level.setBlock(doorPos,ElevatorRegistries.DOOR.get().defaultBlockState().setValue(ElevatorDoorBlock.FACING,Direction.NORTH).setValue(ElevatorDoorBlock.PART,part),3);
  }
  level.setBlock(origin.west(),ElevatorRegistries.CONTROLLER.get().defaultBlockState(),3);
  level.setBlock(origin.east(),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.EAST),3);
  level.setBlock(origin.east(2),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.WEST),3);
  level.setBlock(origin.east(2).above(3),ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING,Direction.WEST),3);
  var pulley=(PoweredPulleyBlockEntity)level.getBlockEntity(pulleyPos);
  var scan=ElevatorAssembly.scan(level,pulleyPos);
  test.assertTrue(scan.attachment()!=null,"Stationary cabin must be detected");
  var discovered=ElevatorFloorDiscovery.discover(level,pulleyPos,scan.attachment(),scan.components());
  test.assertTrue(discovered.floors().size()==2,"Opposing connectors at two heights must be detected before assembly");
  test.assertTrue(discovered.floors().stream().anyMatch(f -> f.attachmentY()==origin.getY()),"Aligned connector must identify current floor");
  var camouflage=(ElevatorBlockEntity)level.getBlockEntity(origin.below());
  camouflage.setCamouflage(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
  var region=new net.minecraft.world.phys.AABB(origin).inflate(6);
  int itemCount=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,region).size();
  test.assertTrue(pulley.motion.assemble(),"Cabin must assemble");
  test.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,region).size()==itemCount,"Assembly must not spawn camouflage items");
  test.assertTrue(pulley.motion.parts.stream().anyMatch(part -> part.offset().equals(new BlockPos(0,-1,0)) && part.camouflage()!=null && part.camouflage().is(net.minecraft.world.level.block.Blocks.STONE)),"Moving cabin must cache camouflage");
  test.assertTrue(pulley.motion.floors().size()==2,"Both floors must remain detected after assembly");
  test.assertTrue(level.getBlockState(origin).isAir(),"Source blocks must transfer out of world");
  var collisionProbe=new net.minecraft.world.entity.decoration.ArmorStand(level,origin.getX()+0.5,origin.getY()+1,origin.getZ()+1.5);
  level.addFreshEntity(collisionProbe);
  collisionProbe.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(0,-2,0));
  test.assertTrue(Math.abs(collisionProbe.getY()-origin.getY())<0.01,"Assembled platform must stop a falling entity, got "+collisionProbe.getY());
  collisionProbe.discard();
  var wallProbe=new net.minecraft.world.entity.decoration.ArmorStand(level,origin.getX()+0.5,origin.getY(),origin.getZ()+1.5);
  level.addFreshEntity(wallProbe);wallProbe.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(0,0,1.5));
  test.assertTrue(wallProbe.getZ()<origin.getZ()+1.8,"Cabin wall must block horizontal movement");wallProbe.discard();
  var overlapProbe=new net.minecraft.world.entity.decoration.ArmorStand(level,origin.getX()+0.5,origin.getY(),origin.getZ()+1.9);
  level.addFreshEntity(overlapProbe);
  ElevatorContraptionCollision.resolve(pulley.motion,level,pulley.motion.y,pulley.motion.y);
  test.assertTrue(overlapProbe.getBoundingBox().maxZ<=origin.getZ()+2.001,"Create collision pass must separate an entity overlapping the wall; z="+overlapProbe.getZ());
  overlapProbe.discard();
  var ceilingProbe=new net.minecraft.world.entity.decoration.ArmorStand(level,origin.getX()+0.5,origin.getY(),origin.getZ()+1.5);
  level.addFreshEntity(ceilingProbe);double startY=ceilingProbe.getY();ceilingProbe.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(0,1,0));
  test.assertTrue(ceilingProbe.getY()<startY+0.5,"Cabin ceiling must block upward movement");ceilingProbe.discard();
  var rider=new net.minecraft.world.entity.decoration.ArmorStand(level,origin.getX()+0.5,origin.getY(),origin.getZ()+1.5);level.addFreshEntity(rider);
  var roundTripSoundProbe=new ArrivalProbe(level,origin);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(roundTripSoundProbe);
  pulley.energy().receiveEnergy(10000,false);pulley.motion.request(origin.getY()+3);
  test.runAfterDelay(15,()->{
   double before=pulley.motion.y;
   var movingWallProbe=new net.minecraft.world.entity.decoration.ArmorStand(level,origin.getX()+0.5,before,origin.getZ()+1.5);
   level.addFreshEntity(movingWallProbe);movingWallProbe.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(0,0,1.5));
   test.assertTrue(movingWallProbe.getZ()<origin.getZ()+1.8,"Moving cabin wall must block movement");movingWallProbe.discard();
   var movingCeilingProbe=new net.minecraft.world.entity.decoration.ArmorStand(level,origin.getX()+0.5,before,origin.getZ()+1.5);
   level.addFreshEntity(movingCeilingProbe);movingCeilingProbe.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(0,1,0));
   test.assertTrue(movingCeilingProbe.getY()<before+0.5,"Moving cabin ceiling must block movement");movingCeilingProbe.discard();
   pulley.consume(100000);
   test.runAfterDelay(4,()->{
    test.assertTrue(Math.abs(before-pulley.motion.y)<0.001,"Power loss must hold cabin");
    test.assertTrue(roundTripSoundProbe.count==0,"Power loss must not play the arrival chime");
    var saved=new net.minecraft.nbt.CompoundTag();pulley.motion.save(saved);pulley.motion.load(saved);
    test.assertTrue(pulley.motion.active(),"Serialized cabin must retain blocks");
    test.assertTrue(pulley.motion.parts.stream().anyMatch(part -> part.camouflage()!=null && part.camouflage().is(net.minecraft.world.level.block.Blocks.STONE)),"Saved moving cabin must retain camouflage");
    pulley.energy().receiveEnergy(10000,false);
   });
  });
  test.runAfterDelay(80,()->{
   test.assertTrue(Math.abs(pulley.motion.y-origin.getY()-3)<0.001,"Cabin must reach upper connector: "+pulley.motion.status+" y="+pulley.motion.y);
   test.assertTrue(roundTripSoundProbe.count==1,"Upper arrival must sound once");
   test.assertTrue(!pulley.motion.active(),"Cabin must auto-park at upper floor: "+pulley.motion.status);
   var clientMirror=new ElevatorCabinMotion(pulley);
   clientMirror.parts.add(new ElevatorCabinMotion.Part(BlockPos.ZERO,ElevatorRegistries.ATTACHMENT.get().defaultBlockState(),new net.minecraft.nbt.CompoundTag()));
   clientMirror.load(pulley.getUpdateTag(level.registryAccess()));
   test.assertTrue(!clientMirror.active(),"Full parking update must clear client cabin parts");
   for(var part:ElevatorDoorBlock.Part.values()) {
    boolean right=part==ElevatorDoorBlock.Part.LOWER_RIGHT || part==ElevatorDoorBlock.Part.UPPER_RIGHT;
    boolean upper=part==ElevatorDoorBlock.Part.UPPER_LEFT || part==ElevatorDoorBlock.Part.UPPER_RIGHT;
    var door=level.getBlockState(doorRoot.east(right?1:0).above((upper?1:0)+3));
    test.assertTrue(door.is(ElevatorRegistries.DOOR) && door.getValue(ElevatorDoorBlock.PART)==part && door.getValue(ElevatorDoorBlock.FACING)==Direction.NORTH && !door.getValue(ElevatorDoorBlock.OPEN) && door.getValue(ElevatorDoorBlock.FRAME)==0,"Door state must survive parking");
   }
   var parkedScan=ElevatorAssembly.scan(level,pulleyPos);
   test.assertTrue(parkedScan.attachment()!=null && parkedScan.attachment().equals(pulley.attachment()),"Periodic cabin validation must recognise parked cabin");
   var parkedStops=ElevatorFloorDiscovery.discover(level,pulleyPos,parkedScan.attachment(),parkedScan.components()).floors().stream().map(ElevatorFloorDiscovery.Floor::attachmentY).collect(java.util.stream.Collectors.toSet());
   test.assertTrue(parkedStops.equals(discovered.floors().stream().map(ElevatorFloorDiscovery.Floor::attachmentY).collect(java.util.stream.Collectors.toSet())),"Parked and virtual floors must agree");
   test.assertTrue(level.getBlockState(origin.above(3)).is(ElevatorRegistries.ATTACHMENT),"Attachment must be restored at upper floor");
   test.assertTrue(pulley.attachment().equals(origin.above(3)),"Pulley must track parked attachment");
   test.assertTrue(Math.abs(rider.getY()-origin.getY()-3)<0.2,"Standing entity must travel with floor: "+rider.getY());
   BlockPos controller=origin.west().above(3);
   var controllerState=level.getBlockState(controller);level.removeBlock(controller,false);
   test.assertTrue(!pulley.motion.assemble() && pulley.motion.status.contains("missing_controller"),"Damaged parked cabin must not assemble: "+pulley.motion.status);
   level.setBlock(controller,controllerState,3);
   var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
   player.setPos(controller.getX()+0.5,controller.getY(),controller.getZ()+0.5);
   var menu=new ElevatorControlMenu(1,player.getInventory(),controller,2);
   var request=new net.minecraft.nbt.CompoundTag();request.putInt("Y",origin.getY());menu.action("travel",request);
   test.assertTrue(pulley.motion.active(),"Request from parked controller must assemble cabin");
  });
  test.runAfterDelay(160,()->{
   test.assertTrue(Math.abs(pulley.motion.y-origin.getY())<0.001,"Cabin must return to lower connector");
   test.assertTrue(roundTripSoundProbe.count==2,"Round trip must produce one chime per arrival");
   net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(roundTripSoundProbe);
   test.assertTrue(!pulley.motion.active(),"Cabin must auto-park after returning: "+pulley.motion.status);
   test.assertTrue(level.getBlockState(origin).is(ElevatorRegistries.ATTACHMENT) && pulley.attachment().equals(origin),"Returned cabin and attachment must be restored");
   test.assertTrue(pulley.motion.assemble(),"Cabin must reassemble for break protection test");
   var fakePlayer=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
   level.setBlock(origin,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
   var blockedBreak=new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level,pulleyPos,level.getBlockState(pulleyPos),fakePlayer);
   PulleyBreakProtection.onBreak(blockedBreak);
   test.assertTrue(blockedBreak.isCanceled() && pulley.motion.active(),"Blocked restoration must cancel pulley breaking and retain cabin");
   level.removeBlock(origin,false);
   var safeBreak=new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level,pulleyPos,level.getBlockState(pulleyPos),fakePlayer);
   PulleyBreakProtection.onBreak(safeBreak);
   test.assertTrue(!safeBreak.isCanceled() && !pulley.motion.active(),"Safe pulley break must restore cabin first");
   level.destroyBlock(pulleyPos,false);
   test.assertTrue(level.getBlockState(origin).is(ElevatorRegistries.ATTACHMENT),"Attachment must restore");
   test.assertTrue(level.getBlockState(origin.below()).is(ElevatorRegistries.ELEVATOR_BLOCK),"Platform must restore");
   test.assertTrue(level.getBlockEntity(origin.below()) instanceof ElevatorBlockEntity be && be.camouflage()!=null && be.camouflage().is(net.minecraft.world.level.block.Blocks.STONE),"Restored platform must retain camouflage");
   test.succeed();
  });
 }
}
