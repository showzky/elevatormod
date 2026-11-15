package com.showzky.elevatormod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class FloorConnectorBlock extends FacingElevatorBlock implements EntityBlock {
 public FloorConnectorBlock(Properties p){super(p);}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FloorConnectorBlockEntity(pos,state);}
}
