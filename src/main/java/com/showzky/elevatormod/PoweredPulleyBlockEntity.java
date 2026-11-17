package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public class PoweredPulleyBlockEntity extends BlockEntity {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    // Storage capacity is provisional; no energy is consumed until movement is designed.
    private final EnergyStorage energy = new EnergyStorage(100_000, 10_000, 0) {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            int received = super.receiveEnergy(amount, simulate);
            if (!simulate && received > 0) setChanged();
            return received;
        }
    };
    private @Nullable BlockPos attachment;
    public final ElevatorCabinMotion motion = new ElevatorCabinMotion(this);
    private final ElevatorFloorDirectory directory = new ElevatorFloorDirectory();
    private CompoundTag structureSnapshot = new CompoundTag();
    /** Set when loaded from disk: the parked cabin is checked against the snapshot once. */
    private boolean verifyAfterLoad;
    /** The cabin positions in the snapshot; the periodic check recaptures only when they differ. */
    private java.util.Set<BlockPos> snapshotPositions = java.util.Set.of();
    public PoweredPulleyBlockEntity(BlockPos pos, BlockState state) { super(ElevatorRegistries.PULLEY_ENTITY.get(), pos, state); }
    public void reportFloors(net.minecraft.world.entity.player.Player player) {
        if (level == null || level.isClientSide || attachment == null) return;
        if (motion.active()) {
            var floors = motion.floors();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.elevatormod.floor_count", floors.size()), false);
            int index = 1;
            for (var floor : floors) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.elevatormod.floor_entry", index++, floor.pos().getY(), floor.y()), false);
            return;
        }
        ElevatorAssembly.Result assembly = ElevatorAssembly.scan(level, worldPosition);
        if (assembly.attachment() == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.elevatormod.floor_invalid"), false);
            return;
        }
        ElevatorFloorDiscovery.Result result = ElevatorFloorDiscovery.discover(level, worldPosition, attachment, assembly.components());
        if (result.cabinConnectors() == 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.elevatormod.no_cabin_connector"), false);
            return;
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.elevatormod.floor_count", result.floors().size()), false);
        int index = 1;
        for (ElevatorFloorDiscovery.Floor floor : result.floors()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.elevatormod.floor_entry", index++, floor.connector().getY(), floor.attachmentY()), false);
        }
        if (result.unloadedColumns()) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.elevatormod.floor_unloaded"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.elevatormod.floor_test_only"), false);
    }
    public void consume(int amount) { energy.deserializeNBT(level.registryAccess(), net.minecraft.nbt.IntTag.valueOf(Math.max(0, energy.getEnergyStored() - amount))); setChanged(); }
    public void sync() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    @Override public void onLoad() { super.onLoad(); ElevatorCabinMotion.register(this); }
    public void arrived(BlockPos pos) { attachment = pos; refreshSnapshot(); sync(); }
    public IEnergyStorage energy() { return energy; }
    public @Nullable BlockPos attachment() { return attachment; }
    public ElevatorFloorDirectory directory() { return directory; }
    public void connect(ElevatorAssembly.Result result) {
        if (level == null || level.isClientSide || result.attachment() == null) return;
        captureSnapshot(result);
        attachment = result.attachment().immutable();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    /**
     * Records the parked cabin as it is now, so a later repair never brings back blocks a player
     * removed on purpose. A cabin that no longer scans as whole loses its snapshot.
     */
    private void refreshSnapshot() {
        if (level == null || level.isClientSide || verifyAfterLoad || attachment == null || motion.active()) return;
        if (level.getServer() == null || !level.getServer().isSameThread()) return;
        ElevatorAssembly.Result result = ElevatorAssembly.scan(level, worldPosition);
        if ("unloaded".equals(result.error())) return;
        if (attachment.equals(result.attachment())) captureSnapshot(result);
        else clearSnapshot();
    }

    private void captureSnapshot(ElevatorAssembly.Result result) {
        structureSnapshot = ElevatorStructureSnapshot.capture(level, result.attachment(), result.components(), level.registryAccess());
        snapshotPositions = result.components();
    }

    private void clearSnapshot() {
        structureSnapshot = new CompoundTag();
        snapshotPositions = java.util.Set.of();
    }

    /**
     * Chunks are saved one at a time, so a cabin across a chunk border can load with part of it
     * missing. Right after loading, missing blocks are put back from the snapshot before the
     * structure check could disconnect the elevator. Returns false while still waiting.
     */
    private boolean verifyCabinAfterLoad() {
        if (attachment == null || motion.active() || structureSnapshot.isEmpty()) return true;
        var repair = ElevatorStructureSnapshot.repairMissing(level, attachment, structureSnapshot);
        if (repair == ElevatorStructureSnapshot.Repair.NOT_LOADED) return false;
        if (repair == ElevatorStructureSnapshot.Repair.REPAIRED) {
            LOGGER.info("Restored missing elevator cabin blocks at {} from the pulley at {}", attachment, worldPosition);
            motion.status = "Cabin repaired";
            sync();
        }
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PoweredPulleyBlockEntity pulley) {
        if (pulley.verifyAfterLoad) {
            if (!pulley.verifyCabinAfterLoad()) return;
            pulley.verifyAfterLoad = false;
        }
        // Staggered so many pulleys do not all rescan their landings on the same tick.
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), ElevatorFloorDirectory.REFRESH_INTERVAL) == 0
                && pulley.directory.refresh(pulley)) pulley.sync();
        if (level.getGameTime() % 20 == 0) ElevatorLightPower.tick(pulley);
        if (pulley.motion.active()) { pulley.motion.tick(); return; }
        // A pending call is usually waiting for doors to shut; check often so it departs promptly.
        if (level.getGameTime() % (pulley.motion.hasQueuedCalls() ? 4 : 20) == 0) pulley.motion.startQueuedCall();
        if (pulley.motion.active()) return;
        if (pulley.attachment != null) pulley.motion.parkedDoorTick();
        if (pulley.attachment == null || level.getGameTime() % 40 != 0) return;
        ElevatorAssembly.Result result = ElevatorAssembly.scan(level, pos);
        // An unloaded neighbouring chunk is not evidence that the cabin was dismantled.
        if ("unloaded".equals(result.error())) return;
        if (!pulley.attachment.equals(result.attachment())) {
            pulley.attachment = null;
            pulley.clearSnapshot();
            pulley.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        } else if (!result.components().equals(pulley.snapshotPositions)) {
            // A block was added or removed: record it now, so a removed block is never restored.
            pulley.captureSnapshot(result);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        refreshSnapshot();
        motion.save(tag);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.put("StructureSnapshot", structureSnapshot.copy());
        if (attachment != null) tag.putLong("Attachment", attachment.asLong());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        motion.load(tag);
        if (level != null) ElevatorCabinMotion.register(this);
        energy.deserializeNBT(registries, net.minecraft.nbt.IntTag.valueOf(tag.getInt("Energy")));
        structureSnapshot = tag.getCompound("StructureSnapshot").copy();
        attachment = tag.contains("Attachment") ? BlockPos.of(tag.getLong("Attachment")) : null;
        directory.read(tag);
        verifyAfterLoad = true;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag();
        motion.save(tag);
        tag.putInt("Energy", energy.getEnergyStored());
        if (attachment != null) tag.putLong("Attachment", attachment.asLong());
        directory.write(tag);
        return tag; }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
