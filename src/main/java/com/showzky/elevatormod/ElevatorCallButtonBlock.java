package com.showzky.elevatormod;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Finds a landing in the nearby shaft, choosing the closest connector by height. */
public final class ElevatorCallButtonBlock extends FacingElevatorBlock {
    public static final com.mojang.serialization.MapCodec<ElevatorCallButtonBlock> CODEC = simpleCodec(ElevatorCallButtonBlock::new);
    @Override protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.HorizontalDirectionalBlock> codec() { return CODEC; }
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    public ElevatorCallButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder) { builder.add(FACING,POWERED); }
    static boolean nearby(BlockPos button, BlockPos connector) { return ElevatorLandingLocator.nearby(button, connector); }
    private static List<ElevatorLandingLocator.Match> matches(Level level,BlockPos button) { return ElevatorLandingLocator.live(level, button); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level.isClientSide)return InteractionResult.SUCCESS;
        var candidates=matches(level,pos);
        if(candidates.size()!=1) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(candidates.isEmpty()?"message.elevatormod.call_unavailable":"message.elevatormod.call_ambiguous"),true);
            return InteractionResult.CONSUME;
        }
        var match=candidates.getFirst();var pulley=match.pulley();
        if(!pulley.motion.active()) {
            if(pulley.attachment().getY()==match.stop()) { player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.elevatormod.call_here"),true);return InteractionResult.CONSUME; }
            if(!pulley.motion.assemble()) {player.displayClientMessage(net.minecraft.network.chat.Component.literal(pulley.motion.status),true);return InteractionResult.CONSUME;}
        }
        pulley.motion.request(match.stop());
        if(pulley.motion.hasCall(match.stop())) {
            level.setBlock(pos,state.setValue(POWERED,true),3);level.scheduleTick(pos,this,10);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(pulley.energy().getEnergyStored()<ElevatorEnergyUsage.perTick(pulley.motion.speed)?"message.elevatormod.call_waiting_power":"message.elevatormod.call_received",match.connector().getY()),true);
        } else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.elevatormod.call_here"),true);
        return InteractionResult.CONSUME;
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(!state.getValue(POWERED))return;
        boolean pending=matches(level,pos).stream().anyMatch(match -> match.pulley().motion.hasCall(match.stop()));
        if(pending)level.scheduleTick(pos,this,10);
        else level.setBlock(pos,state.setValue(POWERED,false),3);
    }
}
