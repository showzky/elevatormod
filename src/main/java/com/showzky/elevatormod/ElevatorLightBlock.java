package com.showzky.elevatormod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A thin ceiling fixture; the pulley updates its powered state. */
public final class ElevatorLightBlock extends Block {
    public static final com.mojang.serialization.MapCodec<ElevatorLightBlock> CODEC = simpleCodec(ElevatorLightBlock::new);
    @Override protected com.mojang.serialization.MapCodec<? extends Block> codec(){return CODEC;}
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    public ElevatorLightBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(LIT,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(LIT);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(0,13,0,16,16,16);}
}
