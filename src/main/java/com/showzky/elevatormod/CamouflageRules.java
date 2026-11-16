package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Restricts camouflage to ordinary full-cube block models: opaque ones, plus the glass family. */
public final class CamouflageRules {
    public static final TagKey<Block> BLACKLIST = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "camouflage_blacklist"));
    /** See-through blocks allowed as camouflage; defaults to the common glass tag, extendable by modpacks. */
    public static final TagKey<Block> TRANSPARENT = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "camouflage_transparent"));
    public static boolean isAllowed(Level level, BlockPos pos, BlockState state) {
        return !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(ElevatorMod.MOD_ID) && !state.is(BLACKLIST)
                && !state.hasBlockEntity() && state.getRenderShape() == RenderShape.MODEL
                && (state.isSolidRender(level, pos) || state.is(TRANSPARENT)) && state.isCollisionShapeFullBlock(level, pos);
    }
    private CamouflageRules() {}
}
