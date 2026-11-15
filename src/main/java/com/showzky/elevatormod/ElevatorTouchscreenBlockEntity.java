package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Master-cell state of a touchscreen: settings and the elevator link. The link is resolved on
 * the server with the Call Button rules once per second and persisted; live elevator state is
 * read from the linked pulley, which is synchronized to every client tracking it.
 */
public final class ElevatorTouchscreenBlockEntity extends BlockEntity {
    private TouchscreenSettings settings = TouchscreenSettings.DEFAULT;
    private TouchscreenDisplayState.Connection connection = TouchscreenDisplayState.Connection.SEARCHING;
    private @Nullable BlockPos pulley;
    private int callStop = TouchscreenDisplayState.NO_STOP;

    public ElevatorTouchscreenBlockEntity(BlockPos pos, BlockState state) { super(ElevatorRegistries.TOUCHSCREEN_ENTITY.get(), pos, state); }

    public TouchscreenSettings settings() { return settings; }
    public TouchscreenDisplayState.Connection connection() { return connection; }
    public @Nullable BlockPos pulleyPos() { return pulley; }
    public int callStop() { return callStop; }
    public TouchscreenSize size() {
        return getBlockState().getBlock() instanceof ElevatorTouchscreenBlock block ? block.size() : TouchscreenSize.SMALL;
    }
    public Direction facing() { return getBlockState().getValue(HorizontalDirectionalBlock.FACING); }

    public void setSettings(TouchscreenSettings next) {
        if (next.equals(settings)) return;
        settings = next;
        changed();
    }

    public @Nullable PoweredPulleyBlockEntity linkedPulley() {
        if (level == null || pulley == null || !level.hasChunkAt(pulley)) return null;
        return level.getBlockEntity(pulley) instanceof PoweredPulleyBlockEntity found ? found : null;
    }

    public TouchscreenDisplayState displayState() {
        return TouchscreenDisplayState.of(connection, linkedPulley(), callStop);
    }

    static void serverTick(Level level, BlockPos pos, ElevatorTouchscreenBlockEntity screen) {
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), ElevatorFloorDirectory.REFRESH_INTERVAL) == 0) screen.refreshLink();
    }

    /** Re-resolves which elevator and landing this screen serves. */
    public void refreshLink() {
        if (level == null || level.isClientSide) return;
        var pulleys = ElevatorCabinMotion.loaded(level);
        // Wait until every pulley has refreshed its directory after loading, so a restart
        // does not briefly replace a persisted link with "no elevator".
        if (pulleys.stream().anyMatch(candidate -> !candidate.directory().ready())) return;
        for (var candidate : pulleys) {
            if (!candidate.motion.active() && candidate.directory().parkedCabinContains(worldPosition)) {
                link(TouchscreenDisplayState.Connection.CABIN, candidate.getBlockPos(), TouchscreenDisplayState.NO_STOP);
                return;
            }
        }
        var matches = ElevatorLandingLocator.cached(level, worldPosition);
        if (matches.size() == 1) link(TouchscreenDisplayState.Connection.LANDING, matches.getFirst().pulley().getBlockPos(), matches.getFirst().stop());
        else link(matches.isEmpty() ? TouchscreenDisplayState.Connection.NO_ELEVATOR : TouchscreenDisplayState.Connection.AMBIGUOUS, null, TouchscreenDisplayState.NO_STOP);
    }

    private void link(TouchscreenDisplayState.Connection nextConnection, @Nullable BlockPos nextPulley, int nextStop) {
        if (nextConnection == connection && java.util.Objects.equals(nextPulley, pulley) && nextStop == callStop) return;
        connection = nextConnection;
        pulley = nextPulley == null ? null : nextPulley.immutable();
        callStop = nextStop;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        settings.write(tag);
        tag.putString("Connection", connection.name());
        if (pulley != null) tag.putLong("Pulley", pulley.asLong());
        tag.putInt("CallStop", callStop);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        settings = TouchscreenSettings.read(tag);
        connection = TouchscreenDisplayState.Connection.SEARCHING;
        for (var value : TouchscreenDisplayState.Connection.values()) if (value.name().equals(tag.getString("Connection"))) connection = value;
        pulley = tag.contains("Pulley") ? BlockPos.of(tag.getLong("Pulley")) : null;
        callStop = tag.contains("CallStop") ? tag.getInt("CallStop") : TouchscreenDisplayState.NO_STOP;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
