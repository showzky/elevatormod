package com.showzky.elevatormod;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class FloorConnectorBlockEntity extends BlockEntity {
 public String name="";
 public FloorConnectorBlockEntity(BlockPos pos,BlockState state){super(ElevatorRegistries.CONNECTOR_ENTITY.get(),pos,state);}
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putString("FloorName",name);}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);name=tag.getString("FloorName");}
}
