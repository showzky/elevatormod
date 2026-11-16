package com.showzky.elevatormod;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/** Uses the camouflage block model when block-entity model data is available. */
public final class CamouflageBakedModel extends BakedModelWrapper<BakedModel> {
    public CamouflageBakedModel(BakedModel original) { super(original); }
    private BakedModel model(BlockState state) { return Minecraft.getInstance().getBlockRenderer().getBlockModel(state); }
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random, ModelData data, @Nullable RenderType renderType) {
        BlockState disguise=data.get(ElevatorBlockEntity.CAMOUFLAGE);
        return disguise==null ? originalModel.getQuads(state,side,random,data,renderType) : model(disguise).getQuads(disguise,side,random,ModelData.EMPTY,renderType);
    }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        BlockState disguise=data.get(ElevatorBlockEntity.CAMOUFLAGE);
        return disguise==null ? originalModel.getRenderTypes(state,random,data) : model(disguise).getRenderTypes(disguise,random,ModelData.EMPTY);
    }
    @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        BlockState disguise=data.get(ElevatorBlockEntity.CAMOUFLAGE);
        return disguise==null ? originalModel.useAmbientOcclusion(state,data,renderType) : model(disguise).useAmbientOcclusion(disguise,ModelData.EMPTY,renderType);
    }
    @Override public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState disguise=data.get(ElevatorBlockEntity.CAMOUFLAGE);
        return disguise==null ? originalModel.getParticleIcon(data) : model(disguise).getParticleIcon(ModelData.EMPTY);
    }
}
