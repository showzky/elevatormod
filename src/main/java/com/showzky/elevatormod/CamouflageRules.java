package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Restricts camouflage to ordinary opaque, full-cube block models. */
public final class CamouflageRules {
    public static final TagKey<Block> BLACKLIST = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "camouflage_blacklist"));
    public static boolean isAllowed(Level level, BlockPos pos, BlockState state) {
        return !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(ElevatorMod.MOD_ID) && !state.is(BLACKLIST)
                && !state.hasBlockEntity() && state.getRenderShape() == RenderShape.MODEL
                && state.isSolidRender(level, pos) && state.isCollisionShapeFullBlock(level, pos);
    }
    private CamouflageRules() {}
}
