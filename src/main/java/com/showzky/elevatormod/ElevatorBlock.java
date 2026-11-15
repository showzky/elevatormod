package com.showzky.elevatormod;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class ElevatorBlock extends Block implements EntityBlock {
    public static final MapCodec<ElevatorBlock> CODEC = simpleCodec(ElevatorBlock::new);
    public ElevatorBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ElevatorBlockEntity(pos,state); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !(stack.getItem() instanceof BlockItem item)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
        if (!(level.getBlockEntity(pos) instanceof ElevatorBlockEntity be)) return ItemInteractionResult.FAIL;
        BlockState appearance = item.getBlock().getStateForPlacement(new BlockPlaceContext(player, hand, stack, hit));
        if (appearance == null) appearance = item.getBlock().defaultBlockState();
        if (!CamouflageRules.isAllowed(level, pos, appearance)) {
            player.displayClientMessage(Component.translatable("message.elevatormod.invalid_camouflage"),true);return ItemInteractionResult.CONSUME;
        }
        if (be.camouflage()!=null && appearance.is(be.camouflage().getBlock())) return ItemInteractionResult.CONSUME;
        BlockState old=be.camouflage();
        if (!player.getAbilities().instabuild) { stack.shrink(1); give(player, old); }
        be.setCamouflage(appearance);return ItemInteractionResult.CONSUME;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ElevatorBlockEntity be && be.camouflage()!=null) {
            BlockState old=be.camouflage();be.setCamouflage(null);if(!player.getAbilities().instabuild) give(player,old);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
    private static void give(Player player, @Nullable BlockState state) {
        if (state==null) return; ItemStack stack=new ItemStack(state.getBlock());
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack,false);
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops=new ArrayList<>(super.getDrops(state,params));
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ElevatorBlockEntity be && be.camouflage()!=null) {
            ItemStack item=new ItemStack(be.camouflage().getBlock());if(!item.isEmpty()) drops.add(item);
        }
        return drops;
    }
    @Override public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        if(level.getBlockEntity(pos) instanceof ElevatorBlockEntity be && be.camouflage()!=null) return be.camouflage().getSoundType(level,pos,entity);
        return super.getSoundType(state,level,pos,entity);
    }
}
