package com.showzky.elevatormod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

/**
 * A pulley's periodically refreshed list of served landings and, while parked, the cabin's
 * block positions. Callers and displays read this instead of scanning the full world height.
 * The floor list is synchronized to clients with the pulley's update packet.
 */
public final class ElevatorFloorDirectory {
    public static final int REFRESH_INTERVAL = 20;
    public record Floor(BlockPos connector, int stop, String name) {}

    private List<Floor> floors = List.of();
    private Set<BlockPos> parkedCabin = Set.of();
    private boolean ready;

    public List<Floor> floors() { return floors; }
    public boolean parkedCabinContains(BlockPos pos) { return parkedCabin.contains(pos); }
    /** False until the first server refresh after loading. */
    public boolean ready() { return ready; }

    /** Rescans the served landings. Returns true when synchronized data changed. */
    public boolean refresh(PoweredPulleyBlockEntity pulley) {
        Level level = pulley.getLevel();
        if (level == null || level.isClientSide) return false;
        List<Floor> next = new ArrayList<>();
        Set<BlockPos> cabin = Set.of();
        if (pulley.attachment() != null) {
            if (pulley.motion.active()) {
                for (var floor : pulley.motion.floors()) next.add(new Floor(floor.pos(), floor.y(), floor.name()));
            } else {
                var scan = ElevatorAssembly.scan(level, pulley.getBlockPos());
                // An unloaded neighbouring chunk is not evidence that the landings changed.
                if ("unloaded".equals(scan.error())) return false;
                if (scan.attachment() != null && scan.attachment().equals(pulley.attachment())) {
                    cabin = scan.components();
                    for (var floor : ElevatorFloorDiscovery.discover(level, pulley.getBlockPos(), scan.attachment(), scan.components()).floors())
                        next.add(new Floor(floor.connector(), floor.attachmentY(), floorName(level, floor.connector())));
                }
            }
        }
        parkedCabin = cabin;
        ready = true;
        if (next.equals(floors)) return false;
        floors = List.copyOf(next);
        return true;
    }

    static String floorName(Level level, BlockPos connector) {
        String name = level.getBlockEntity(connector) instanceof FloorConnectorBlockEntity be ? be.name : "";
        return name.isBlank() ? "Floor Y=" + connector.getY() : name;
    }

    public void write(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Floor floor : floors) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Connector", floor.connector().asLong());
            entry.putInt("Stop", floor.stop());
            entry.putString("Name", floor.name());
            list.add(entry);
        }
        tag.put("DirectoryFloors", list);
    }

    public void read(CompoundTag tag) {
        if (!tag.contains("DirectoryFloors", Tag.TAG_LIST)) return;
        List<Floor> next = new ArrayList<>();
        for (Tag element : tag.getList("DirectoryFloors", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            next.add(new Floor(BlockPos.of(entry.getLong("Connector")), entry.getInt("Stop"), entry.getString("Name")));
        }
        floors = List.copyOf(next);
    }
}
