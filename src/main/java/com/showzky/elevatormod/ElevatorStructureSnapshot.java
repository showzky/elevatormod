package com.showzky.elevatormod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A copy of a parked cabin's blocks, kept by its pulley. Minecraft saves chunks separately, so a
 * cabin that crosses a chunk border can come back from disk with one side missing; the snapshot
 * lets the pulley put those blocks back. Capturing never changes the world.
 */
public final class ElevatorStructureSnapshot {
    public enum Repair { NOTHING_MISSING, REPAIRED, NOT_LOADED, NOT_REPAIRABLE }

    public static CompoundTag capture(Level level, BlockPos origin, Collection<BlockPos> positions,
                                      HolderLookup.Provider registries) {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putInt("FormatVersion", 1);
        snapshot.putLong("Origin", origin.asLong());
        ListTag blocks = new ListTag();
        for (BlockPos pos : positions) {
            if (!level.hasChunkAt(pos)) throw new IllegalStateException("Cannot snapshot an unloaded cabin");
            CompoundTag entry = new CompoundTag();
            entry.putLong("RelativePosition", pos.subtract(origin).asLong());
            entry.put("BlockState", NbtUtils.writeBlockState(level.getBlockState(pos)));
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) entry.put("BlockEntity", blockEntity.saveWithFullMetadata(registries));
            blocks.add(entry);
        }
        snapshot.put("Blocks", blocks);
        return snapshot;
    }

    /**
     * Puts back cabin blocks that are missing (air) where the snapshot says they belong. Nothing
     * is changed when any position holds a different block, since the player may have rebuilt
     * there, or when the snapshot was taken at another floor.
     */
    public static Repair repairMissing(Level level, BlockPos attachment, CompoundTag snapshot) {
        if (!snapshot.contains("Blocks") || snapshot.getLong("Origin") != attachment.asLong()) return Repair.NOT_REPAIRABLE;
        record Missing(BlockPos pos, BlockState state, CompoundTag data) {}
        List<Missing> missing = new ArrayList<>();
        for (Tag tag : snapshot.getList("Blocks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            BlockPos pos = attachment.offset(BlockPos.of(entry.getLong("RelativePosition")));
            if (!level.hasChunkAt(pos)) return Repair.NOT_LOADED;
            BlockState expected = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("BlockState"));
            BlockState actual = level.getBlockState(pos);
            if (actual.is(expected.getBlock())) continue;
            if (!actual.isAir()) return Repair.NOT_REPAIRABLE;
            missing.add(new Missing(pos, expected, entry.getCompound("BlockEntity")));
        }
        if (missing.isEmpty()) return Repair.NOTHING_MISSING;
        for (Missing block : missing) {
            level.setBlock(block.pos, block.state, 3);
            var blockEntity = level.getBlockEntity(block.pos);
            if (blockEntity == null || block.data.isEmpty()) continue;
            CompoundTag data = block.data.copy();
            data.putInt("x", block.pos.getX());
            data.putInt("y", block.pos.getY());
            data.putInt("z", block.pos.getZ());
            blockEntity.loadWithComponents(data, level.registryAccess());
            blockEntity.setChanged();
        }
        return Repair.REPAIRED;
    }

    private ElevatorStructureSnapshot() {}
}
