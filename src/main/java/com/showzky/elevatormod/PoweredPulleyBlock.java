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
        return createTickerHelper(type, ElevatorRegistries.PULLEY_ENTITY.get(), (world, pos, tickState, pulley) -> { if (world.isClientSide) {
            pulley.motion.clientTick();
            if (pulley.motion.active()) ElevatorContraptionCollision.resolve(pulley.motion, world, pulley.motion.displayPreviousY, pulley.motion.displayY);
        } else {
            double before = pulley.motion.y;
            PoweredPulleyBlockEntity.tick(world, pos, tickState, pulley);
            if (pulley.motion.active()) ElevatorContraptionCollision.resolve(pulley.motion, world, before, pulley.motion.y);
        } });
    }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (level.getBlockEntity(pos) instanceof PoweredPulleyBlockEntity pulley
                && pulley.motion.active() && !pulley.motion.disassemble()) {
            pulley.sync();
            return;
        }
        super.onBlockExploded(state, level, pos, explosion);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && !state.is(replacement.getBlock())
                && level.getBlockEntity(pos) instanceof PoweredPulleyBlockEntity pulley && pulley.motion.active()) {
            // Covers direct replacements when restoration is possible. Breaks and explosions
            // are separately intercepted before removal when restoration is not possible.
            pulley.motion.disassemble();
        }
        if (!level.isClientSide && !state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof PoweredPulleyBlockEntity pulley)
            ElevatorLightPower.extinguishParked(pulley);
        super.onRemove(state, level, pos, replacement, moved);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PoweredPulleyBlockEntity pulley) {
            if (player.isShiftKeyDown()) { ElevatorControlMenu.open(player, pos, 0); return InteractionResult.CONSUME; }
            // The cabin only becomes virtual while travelling. A stationary virtual cabin (for
            // example from older versions, which assembled on right-click) is parked back into
            // real, solid, usable blocks.
            if (pulley.motion.active()) {
                if (Math.abs(pulley.motion.target - pulley.motion.y) > 1e-6) {
                    player.displayClientMessage(Component.literal(pulley.motion.status), true);
                } else if (pulley.motion.disassemble()) {
                    player.displayClientMessage(Component.literal("Elevator parked."), false);
                } else {
                    pulley.sync();
                    player.displayClientMessage(Component.literal(pulley.motion.status), false);
                }
                return InteractionResult.CONSUME;
            }
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
