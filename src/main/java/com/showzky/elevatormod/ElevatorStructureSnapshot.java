package com.showzky.elevatormod;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;

/** Captures source data without removing or replacing any world blocks. */
public final class ElevatorStructureSnapshot {
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

    private ElevatorStructureSnapshot() {}
}
