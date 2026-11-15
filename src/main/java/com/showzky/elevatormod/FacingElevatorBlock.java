package com.showzky.elevatormod;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A horizontal-facing component using the orientation of its supplied model. */
public class FacingElevatorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FacingElevatorBlock> CODEC = simpleCodec(FacingElevatorBlock::new);
    public FacingElevatorBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        double minX, minY, minZ, maxX, maxY, maxZ;
        if (state.is(ElevatorRegistries.CONTROLLER)) { minX = 3; minY = 2; minZ = 13; maxX = 13; maxY = 14; maxZ = 16; }
        else if (state.is(ElevatorRegistries.CALL_BUTTON)) { minX = 5; minY = 4; minZ = 14; maxX = 11; maxY = 12; maxZ = 16; }
        else if (state.is(ElevatorRegistries.DOOR)) { minX = 0; minY = 0; minZ = 7; maxX = 16; maxY = 16; maxZ = 9; }
        else return Block.box(0, 0, 0, 16, 16, 16);
        return switch (state.getValue(FACING)) {
            case EAST -> Block.box(16 - maxZ, minY, minX, 16 - minZ, maxY, maxX);
            case SOUTH -> Block.box(16 - maxX, minY, 16 - maxZ, 16 - minX, maxY, 16 - minZ);
            case WEST -> Block.box(minZ, minY, 16 - maxX, maxZ, maxY, 16 - minX);
            default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!state.is(ElevatorRegistries.CONTROLLER) && !state.is(ElevatorRegistries.FLOOR_CONNECTOR)) return net.minecraft.world.InteractionResult.PASS;
        if (!level.isClientSide) {
            if (state.is(ElevatorRegistries.CONTROLLER)) ElevatorControlMenu.open(player, pos, 2);
            else ElevatorControlMenu.open(player, pos, 1);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
}

