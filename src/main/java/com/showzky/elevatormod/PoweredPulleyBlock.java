package com.showzky.elevatormod;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class PoweredPulleyBlock extends BaseEntityBlock {
    public static final MapCodec<PoweredPulleyBlock> CODEC = simpleCodec(PoweredPulleyBlock::new);
    public PoweredPulleyBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PoweredPulleyBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ElevatorRegistries.PULLEY_ENTITY.get(), PoweredPulleyBlockEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PoweredPulleyBlockEntity pulley) {
            if (pulley.attachment() != null) {
                pulley.reportFloors(player);
                player.displayClientMessage(Component.translatable("message.elevatormod.already_connected"), false);
                player.displayClientMessage(Component.translatable("message.elevatormod.next_steps"), false);
            }
            else {
                ElevatorAssembly.Result result = ElevatorAssembly.scan(level, pos);
                if (result.attachment() != null) {
                    pulley.connect(result);
                    pulley.reportFloors(player);
                    player.displayClientMessage(Component.translatable("message.elevatormod.connected", result.blockCount()), false);
                    player.displayClientMessage(Component.translatable("message.elevatormod.next_steps"), false);
                }
                else if (result.obstruction() != null) {
                    BlockPos obstruction = result.obstruction();
                    player.displayClientMessage(Component.translatable("message.elevatormod.obstructed",
                            level.getBlockState(obstruction).getBlock().getName(),
                            obstruction.getX(), obstruction.getY(), obstruction.getZ()), false);
                    player.displayClientMessage(Component.translatable("message.elevatormod.attachment_hint"), false);
                } else player.displayClientMessage(Component.translatable("message.elevatormod." + result.error()), false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
