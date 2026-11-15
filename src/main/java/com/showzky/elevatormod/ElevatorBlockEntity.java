package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

public final class ElevatorBlockEntity extends BlockEntity {
    public static final ModelProperty<BlockState> CAMOUFLAGE = new ModelProperty<>();
    public static final String TAG = "Camouflage";
    private @Nullable BlockState camouflage;
    public ElevatorBlockEntity(BlockPos pos, BlockState state) { super(ElevatorRegistries.ELEVATOR_ENTITY.get(), pos, state); }
    public @Nullable BlockState camouflage() { return camouflage; }
    public void setCamouflage(@Nullable BlockState state) {
        camouflage = state; setChanged();
        if (level != null) { if (level.isClientSide) requestModelDataUpdate(); level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    }
    public static @Nullable BlockState readCamouflage(CompoundTag tag) {
        return tag.contains(TAG, 10) ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(TAG)) : null;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); if (camouflage != null) tag.put(TAG, NbtUtils.writeBlockState(camouflage));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BlockState previous=camouflage;
        camouflage=readCamouflage(tag);
        if (level != null && level.isClientSide && !java.util.Objects.equals(previous,camouflage)) {
            // Model data and the chunk mesh are separate caches. Invalidate both after
            // applying the block-entity packet, not only when its block update arrives.
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        }
    }
    @Override public ModelData getModelData() { return camouflage == null ? ModelData.EMPTY : ModelData.of(CAMOUFLAGE, camouflage); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag=saveWithoutMetadata(registries);
        // NeoForge ignores empty block-entity packets; keep removals non-empty so
        // clients receive and apply the absence of the camouflage state.
        tag.putBoolean("HasCamouflage",camouflage!=null);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
