package com.showzky.elevatormod;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Pulley-owned cabin data and vertical motion; world blocks are transferred only once. */
public final class ElevatorCabinMotion {
    private static final Map<Level, Set<PoweredPulleyBlockEntity>> LOADED = new WeakHashMap<>();
    public record Part(BlockPos offset, BlockState state, CompoundTag data, BlockState camouflage) {
        public Part(BlockPos offset, BlockState state, CompoundTag data) {
            this(offset, state, data, state.is(ElevatorRegistries.ELEVATOR_BLOCK) ? ElevatorBlockEntity.readCamouflage(data) : null);
        }
    }
    public record Floor(BlockPos pos, int y, String name) {}
    private final PoweredPulleyBlockEntity owner;
    public final List<Part> parts = new ArrayList<>();
    public BlockPos origin = BlockPos.ZERO;
    public double y, previousY, target, velocity;
    public double displayPreviousY, displayY;
    private boolean displayInitialized;
    // Set false for development sessions that need the cabin to remain virtual at stops.
    public static final boolean AUTO_PARK_ENABLED = true;
    private boolean parkWhenStopped;
    private long nextParkAttempt;
    private final java.util.ArrayDeque<Integer> callQueue = new java.util.ArrayDeque<>();

    /** Advances client display coordinates once per client tick. */
    public void clientTick() {
        if (!displayInitialized || Math.abs(y - displayY) > 4) {
            displayPreviousY = displayY = y;
            displayInitialized = true;
        } else {
            displayPreviousY = displayY;
            displayY = y;
        }
    }
    public double speed = ElevatorEnergyUsage.DEFAULT_SPEED;
    public String status = "Ready";
    public ElevatorCabinMotion(PoweredPulleyBlockEntity owner) { this.owner = owner; }
    public static void register(PoweredPulleyBlockEntity pulley) { LOADED.computeIfAbsent(pulley.getLevel(), l -> Collections.newSetFromMap(new WeakHashMap<>())).add(pulley); }
    public static List<PoweredPulleyBlockEntity> loaded(Level level) { return LOADED.getOrDefault(level, Set.of()).stream().filter(p -> !p.isRemoved() && level.hasChunkAt(p.getBlockPos())).toList(); }
    public boolean active() { return !parts.isEmpty(); }
    public List<VoxelShape> shapes(double atY) {
        List<VoxelShape> shapes = new ArrayList<>();
        for (Part part : parts) shapes.add(part.state.getCollisionShape(owner.getLevel(), origin.offset(part.offset)).move(origin.getX()+part.offset.getX(),atY+part.offset.getY(),origin.getZ()+part.offset.getZ()));
        return shapes;
    }
    public static List<VoxelShape> collisions(Level level, AABB box) {
        List<VoxelShape> result = new ArrayList<>();
        for (var p : loaded(level)) if (p.motion.active()) for (var shape : p.motion.shapes(p.motion.y))
            if (!shape.isEmpty() && shape.bounds().intersects(box)) result.add(shape);
        return result;
    }
    public boolean assemble() {
        Level level = owner.getLevel();
        if (level == null || level.isClientSide || active()) return false;
        var scan = ElevatorAssembly.scan(level, owner.getBlockPos());
        if (scan.attachment() == null) { status = "Assembly failed: " + scan.error(); return false; }
        for (BlockPos pos : scan.components()) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ElevatorRegistries.DOOR) && state.getValue(ElevatorDoorBlock.FRAME) != 0) { status="Close the cabin doors first"; return false; }
        }
        register(owner);
        owner.connect(scan); origin = scan.attachment(); y=previousY=target=origin.getY();
        for (BlockPos pos : scan.components()) {
            var be = level.getBlockEntity(pos);
            parts.add(new Part(pos.subtract(origin), level.getBlockState(pos), be == null ? new CompoundTag() : be.saveWithFullMetadata(level.registryAccess())));
        }
        owner.setChanged();
        for (BlockPos pos : scan.components()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3 | 16);
        status="Ready"; owner.sync(); return true;
    }
    public List<Floor> floors() {
        Level level = owner.getLevel();
        List<ElevatorFloorDiscovery.CabinConnector> connectors = new ArrayList<>();
        for (Part part : parts) if (part.state().is(ElevatorRegistries.FLOOR_CONNECTOR))
            connectors.add(new ElevatorFloorDiscovery.CabinConnector(part.offset(), part.state().getValue(FacingElevatorBlock.FACING)));
        int minY = parts.stream().mapToInt(part -> part.offset().getY()).min().orElse(0);
        int maxY = parts.stream().mapToInt(part -> part.offset().getY()).max().orElse(0);
        var result = ElevatorFloorDiscovery.discover(level, owner.getBlockPos(), origin, connectors, minY, maxY, Set.of());
        List<Floor> floors = new ArrayList<>();
        for (var floor : result.floors()) {
            var blockEntity = level.getBlockEntity(floor.connector());
            String name = blockEntity instanceof FloorConnectorBlockEntity connector ? connector.name : "";
            floors.add(new Floor(floor.connector(), floor.attachmentY(), name.isBlank() ? "Floor Y=" + floor.connector().getY() : name));
        }
        return floors;
    }
    public void request(int stop) {
        if(floors().stream().noneMatch(f -> f.y==stop)) { status="Destination no longer available"; owner.sync(); return; }
        if (Math.abs(target-y)>1e-6) {
            if ((int)target!=stop && !callQueue.contains(stop)) callQueue.addLast(stop);
            owner.sync();return;
        }
        target=stop; parkWhenStopped=AUTO_PARK_ENABLED; nextParkAttempt=0; status="Travelling"; owner.sync();
    }
    public boolean hasCall(int stop) {
        return callQueue.contains(stop) || (active() && (int)target==stop && Math.abs(target-y)>1e-6);
    }
    /** Continue queued calls after the cabin has parked, validating each landing again. */
    public void startQueuedCall() {
        if (active() || callQueue.isEmpty()) return;
        Level level=owner.getLevel();
        var scan=ElevatorAssembly.scan(level,owner.getBlockPos());
        if(scan.attachment()==null) { status="Queued call waiting: cabin unavailable"; return; }
        int stop=callQueue.peekFirst();
        var result=ElevatorFloorDiscovery.discover(level,owner.getBlockPos(),scan.attachment(),scan.components());
        if(result.floors().stream().noneMatch(f -> f.attachmentY()==stop)) {callQueue.removeFirst();status="Queued floor unavailable";owner.sync();return;}
        if(stop==scan.attachment().getY()) {callQueue.removeFirst();owner.sync();return;}
        if(!assemble()) return;
        callQueue.removeFirst();request(stop);
    }
    public void tick() {
        Level level=owner.getLevel(); previousY=y;
        double remaining=target-y;
        if(Math.abs(remaining)<1e-6) {
            y=target; velocity=0;
            if (parkWhenStopped && level.getGameTime() >= nextParkAttempt) {
                if (!disassemble()) { nextParkAttempt=level.getGameTime()+20; owner.sync(); }
            } else if (!parkWhenStopped && !status.equals("Ready")) { status="Ready"; owner.sync(); }
            return;
        }
        if(owner.energy().getEnergyStored()<ElevatorEnergyUsage.perTick(speed)) { velocity=0; status="Waiting for FE"; owner.sync(); return; }
        double desired=Math.copySign(Math.min(speed,Math.sqrt(2*0.005*Math.abs(remaining))),remaining);
        velocity += Math.clamp(desired-velocity,-0.005,0.005);
        double step=Math.copySign(Math.min(Math.abs(velocity),Math.abs(remaining)),remaining);
        List<VoxelShape> old=shapes(y), next=shapes(y+step);
        for(int i=0;i<next.size();i++) {
            if(next.get(i).isEmpty()) continue;
            AABB swept=next.get(i).bounds().minmax(old.get(i).bounds()).deflate(1e-7);
            for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(swept.minX,swept.minY,swept.minZ),BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ))) {
                if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)) { velocity=0;status="Travel area unavailable";owner.sync();return; }
            }
            if(!level.noBlockCollision(null, swept)) { velocity=0;status="Obstructed near " + swept;owner.sync();return; }
        }
        Set<Entity> riders=new HashSet<>();
        for(VoxelShape shape:old) if(!shape.isEmpty()) {
            AABB box=shape.bounds();
            // Players move under their own client's control; each client carries its own player
            // (ElevatorClientPassengerCarry). Moving them here causes corrections and fall-through.
            for(Entity e:level.getEntities((Entity)null,box.inflate(0.05,0.15,0.05),e -> !e.isSpectator() && !(e instanceof net.minecraft.world.entity.player.Player))) {
                if(Math.abs(e.getBoundingBox().minY-box.maxY)<0.15 && e.getDeltaMovement().y<=0.1) riders.add(e);
            }
        }
        // Carry supported entities using ordinary movement, after exposing the next cabin position.
        y+=step;
        for(Entity e:riders) ElevatorPassengerCollision.carry(e, step);
        owner.consume(ElevatorEnergyUsage.perTick(speed));
        if (Math.abs(target-y)<1e-6) {
            status="Ready";
            level.playSound(null,origin.getX()+0.5,y+0.5,origin.getZ()+0.5,
                    ElevatorSoundEvents.ARRIVAL.get(),net.minecraft.sounds.SoundSource.BLOCKS,1.0F,1.0F);
        } else status=step>0?"Going up":"Going down";
        owner.sync();
    }
    public boolean disassemble() {
        Level level=owner.getLevel();
        if(Math.abs(velocity)>1e-6 || Math.abs(y-Math.rint(y))>1e-6) { status="Stop at a floor first";return false; }
        BlockPos at=new BlockPos(origin.getX(),(int)Math.rint(y),origin.getZ());
        for(Part p:parts) if(!level.hasChunkAt(at.offset(p.offset)) || !level.getBlockState(at.offset(p.offset)).isAir()) {status="Restoration blocked";return false;}
        for (Part p : parts) {
            BlockPos pos=at.offset(p.offset);
            VoxelShape shape=p.state.getCollisionShape(level,pos);
            for (AABB box : shape.toAabbs()) {
                AABB worldBox=box.move(pos).deflate(1e-7);
                if (!level.getEntities((Entity)null,worldBox,e -> e.isAlive() && !e.isSpectator()).isEmpty()) {
                    status="Restoration blocked by entity";return false;
                }
            }
        }
        for(Part p:parts) {
            BlockPos pos=at.offset(p.offset);level.setBlock(pos,p.state,3);
            var be=level.getBlockEntity(pos);
            if(be!=null&&!p.data.isEmpty()){ CompoundTag data=p.data.copy();data.putInt("x",pos.getX());data.putInt("y",pos.getY());data.putInt("z",pos.getZ());be.loadWithComponents(data,level.registryAccess());be.setChanged(); }
        }
        parts.clear(); parkWhenStopped=false; nextParkAttempt=0; status="Ready"; owner.arrived(at);return true;
    }
    public void save(CompoundTag tag) {
        tag.putIntArray("CallQueue",callQueue.stream().mapToInt(Integer::intValue).toArray());
        tag.putBoolean("ParkWhenStopped",parkWhenStopped);
        tag.putLong("CabinOrigin",origin.asLong()); tag.putDouble("CabinY",y);tag.putDouble("CabinTarget",target);tag.putDouble("CabinSpeed",speed);tag.putString("CabinStatus",status);
        ListTag list=new ListTag();for(Part p:parts){CompoundTag t=new CompoundTag();t.putLong("Offset",p.offset.asLong());t.put("State",NbtUtils.writeBlockState(p.state));t.put("Data",p.data.copy());list.add(t);}tag.put("CabinParts",list);
    }
    public void load(CompoundTag tag) {
        callQueue.clear();for(int stop:tag.getIntArray("CallQueue")) callQueue.addLast(stop);
        parkWhenStopped=tag.getBoolean("ParkWhenStopped"); nextParkAttempt=0;
        origin=BlockPos.of(tag.getLong("CabinOrigin"));previousY=y;y=tag.getDouble("CabinY");target=tag.getDouble("CabinTarget");speed=tag.contains("CabinSpeed")?ElevatorEnergyUsage.sanitizeSpeed(tag.getDouble("CabinSpeed")):ElevatorEnergyUsage.DEFAULT_SPEED;status=tag.getString("CabinStatus");if(!tag.contains("CabinParts"))return;parts.clear();
        for(Tag entry:tag.getList("CabinParts",10)){CompoundTag t=(CompoundTag)entry;parts.add(new Part(BlockPos.of(t.getLong("Offset")),NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),t.getCompound("State")),t.getCompound("Data")));}
    }
}
