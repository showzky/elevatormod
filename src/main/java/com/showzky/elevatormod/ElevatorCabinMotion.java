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
import org.jetbrains.annotations.Nullable;

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
    // The cabin is only virtual while travelling: a stopped cabin always parks back into real
    // blocks, however it was assembled. Set false for development sessions that need the cabin
    // to remain virtual at stops.
    public static final boolean AUTO_PARK_ENABLED = true;
    private long nextParkAttempt;
    private final java.util.ArrayDeque<Integer> callQueue = new java.util.ArrayDeque<>();

    /**
     * Largest cabin movement within one client tick that is still treated as travel rather than
     * a teleport or reload. Position updates can arrive bunched, several in one client tick, so
     * this must cover several ticks at top speed (1 block per tick).
     */
    public static final double CLIENT_SNAP_DISTANCE = 8;

    /** Below this the remaining client correction is applied at once, as Create does. */
    private static final double CORRECTION_SNAP = 1.0 / 128;
    /** Share of the remaining server correction applied per client tick. */
    private static final double CORRECTION_RATE = 0.25;
    /** Server-minus-client position difference still to be blended in on the client. */
    private double clientCorrection;
    /** True while the server holds the cabin mid-trip (no power, obstructed, unloaded area). */
    private boolean stalled;

    /**
     * Client-side motion, adapted from Create's linear actuators and elevator pulley: the client
     * simulates the cabin with the same speed curve as the server, one step per tick, and blends
     * server positions in gradually. Updates that arrive bunched therefore never make the cabin
     * jump several blocks under its riders. Runs at the start of each client tick, before
     * entities, so riders are carried by this tick's movement.
     */
    public void clientTick() {
        if (active() && !stalled && Math.abs(target - y) > 1e-6) y += nextStep(target - y);
        else if (stalled) velocity = 0;
        if (clientCorrection != 0) {
            double applied = Math.abs(clientCorrection) < CORRECTION_SNAP ? clientCorrection : clientCorrection * CORRECTION_RATE;
            y += applied;
            clientCorrection -= applied;
        }
        if (!displayInitialized || Math.abs(y - displayY) > CLIENT_SNAP_DISTANCE) {
            displayPreviousY = displayY = y;
            displayInitialized = true;
        } else {
            displayPreviousY = displayY;
            displayY = y;
        }
        turnDrum();
    }

    /** Drum rotation per block of rope wound or unwound (the drum's radius is about 5/16 block). */
    private static final double DRUM_DEGREES_PER_BLOCK = 180;
    /**
     * Drawn drum rotation is capped per tick. At top speed the drum would turn half a revolution
     * each tick, so a symmetric drum would look frozen or spin backwards (the wagon-wheel effect).
     */
    private static final double MAX_DRUM_DEGREES_PER_TICK = 90;
    /** Client drum angle in degrees, for interpolation between ticks. */
    public double drumPreviousAngle, drumAngle;
    private double drumTrackedY = Double.NaN;

    /**
     * Accumulates the drum angle from cabin movement rather than deriving it from the cabin's
     * height, so the drum keeps its angle when the cabin parks instead of snapping back.
     */
    private void turnDrum() {
        drumPreviousAngle = drumAngle;
        double moved = y - drumTrackedY;
        if (Math.abs(moved) <= CLIENT_SNAP_DISTANCE)
            drumAngle += Math.clamp(moved * DRUM_DEGREES_PER_BLOCK, -MAX_DRUM_DEGREES_PER_TICK, MAX_DRUM_DEGREES_PER_TICK);
        drumTrackedY = y;
        // Keep the values small; both shift together so interpolation is unaffected.
        double wrap = 360 * Math.floor(drumAngle / 360);
        drumAngle -= wrap;
        drumPreviousAngle -= wrap;
    }
    public double speed = ElevatorEnergyUsage.DEFAULT_SPEED;
    public String status = "Ready";
    public ElevatorCabinMotion(PoweredPulleyBlockEntity owner) { this.owner = owner; }
    public static void register(PoweredPulleyBlockEntity pulley) { LOADED.computeIfAbsent(pulley.getLevel(), l -> Collections.newSetFromMap(new WeakHashMap<>())).add(pulley); }
    public static List<PoweredPulleyBlockEntity> loaded(Level level) { return LOADED.getOrDefault(level, Set.of()).stream().filter(p -> !p.isRemoved() && level.hasChunkAt(p.getBlockPos())).toList(); }
    public boolean active() { return !parts.isEmpty(); }

    /**
     * Updates the velocity and returns this tick's movement towards a destination, using Create's
     * elevator profile: accelerate towards the set speed, then brake at the same rate so the
     * cabin stops exactly at the landing. Shared by server movement and client simulation.
     */
    private double nextStep(double remaining) {
        double acceleration = ElevatorEnergyUsage.acceleration(speed);
        double desired = Math.copySign(Math.min(speed, Math.sqrt(2 * acceleration * Math.abs(remaining))), remaining);
        velocity += Math.clamp(desired - velocity, -acceleration, acceleration);
        return Math.copySign(Math.min(Math.abs(velocity), Math.abs(remaining)), remaining);
    }
    public List<VoxelShape> shapes(double atY) {
        List<VoxelShape> shapes = new ArrayList<>();
        for (Part part : parts) shapes.add(part.state.getCollisionShape(owner.getLevel(), origin.offset(part.offset)).move(origin.getX()+part.offset.getX(),atY+part.offset.getY(),origin.getZ()+part.offset.getZ()));
        return shapes;
    }
    /** Ticks after parking during which riders are still treated as on the cabin (hand-off to real blocks). */
    private static final int PARKED_GRACE_TICKS = 40;
    /** Vertical slack around the cabin, so players standing on its roof or floor count as riders. */
    private static final double RIDER_MARGIN = 2;
    private @Nullable AABB parkedBounds;
    private long parkedUntil;

    /** The block-aligned box enclosing every part with the cabin at the given height, or null when parked. */
    public @Nullable AABB cabinBounds(double atY) {
        if (parts.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Part part : parts) {
            BlockPos o = part.offset;
            minX = Math.min(minX, o.getX()); minY = Math.min(minY, o.getY()); minZ = Math.min(minZ, o.getZ());
            maxX = Math.max(maxX, o.getX()); maxY = Math.max(maxY, o.getY()); maxZ = Math.max(maxZ, o.getZ());
        }
        return new AABB(origin.getX() + minX, atY + minY, origin.getZ() + minZ, origin.getX() + maxX + 1, atY + maxY + 1, origin.getZ() + maxZ + 1);
    }

    /**
     * Where a player counts as riding this cabin. Players report their own position, which lags
     * the server's cabin by their latency, so the area extends vertically by up to a second of
     * travel. It stays in place briefly after parking.
     */
    public @Nullable AABB riderArea(long gameTime) {
        AABB bounds = cabinBounds(y);
        if (bounds != null) return bounds.inflate(0, RIDER_MARGIN + Math.abs(velocity) * 20, 0);
        return gameTime < parkedUntil && parkedBounds != null ? parkedBounds.inflate(0, RIDER_MARGIN, 0) : null;
    }

    /** True for a player riding, or just arrived on, any cabin in their level. */
    public static boolean carriesPlayer(net.minecraft.world.entity.player.Player player) {
        Level level = player.level();
        AABB feet = player.getBoundingBox();
        for (var pulley : loaded(level)) {
            AABB area = pulley.motion.riderArea(level.getGameTime());
            if (area != null && area.intersects(feet)) return true;
        }
        return false;
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
        owner.connect(scan); origin = scan.attachment(); y=previousY=target=origin.getY(); arrivedAt = Long.MIN_VALUE / 2;
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
        target=stop; nextParkAttempt=0; status="Travelling"; owner.sync();
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
            if (AUTO_PARK_ENABLED && level.getGameTime() >= nextParkAttempt) {
                if (!disassemble()) {
                    // Right after arriving, a refusal is usually a rider whose reported position
                    // is a moment behind: retry every tick and show it as arriving, not a fault.
                    if (level.getGameTime() - arrivedAt < ARRIVAL_PARK_TICKS) { status="Arriving"; nextParkAttempt=level.getGameTime()+1; }
                    else nextParkAttempt=level.getGameTime()+20;
                    owner.sync();
                }
            } else if (!AUTO_PARK_ENABLED && !status.equals("Ready")) { status="Ready"; owner.sync(); }
            return;
        }
        stalled=true;
        if(owner.energy().getEnergyStored()<ElevatorEnergyUsage.perTick(speed)) { velocity=0; status="Waiting for FE"; owner.sync(); return; }
        double step=nextStep(remaining);
        List<VoxelShape> old=shapes(y), next=shapes(y+step);
        for(int i=0;i<next.size();i++) {
            if(next.get(i).isEmpty()) continue;
            AABB swept=next.get(i).bounds().minmax(old.get(i).bounds()).deflate(1e-7);
            for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(swept.minX,swept.minY,swept.minZ),BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ))) {
                if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)) { velocity=0;status="Travel area unavailable";owner.sync();return; }
            }
            if(!level.noBlockCollision(null, swept)) { velocity=0;status="Obstructed near " + swept;owner.sync();return; }
        }
        stalled=false;
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
            status="Ready"; arrivedAt=level.getGameTime();
            level.playSound(null,origin.getX()+0.5,y+0.5,origin.getZ()+0.5,
                    ElevatorSoundEvents.ARRIVAL.get(),net.minecraft.sounds.SoundSource.BLOCKS,1.0F,1.0F);
        } else status=step>0?"Going up":"Going down";
        owner.sync();
    }
    /** How long after arriving parking is retried every tick before a refusal is reported as a fault. */
    private static final int ARRIVAL_PARK_TICKS = 40;
    /**
     * How far a player's feet may sink into the top of a part and still count as standing on it.
     * Players report their own position a moment late, so on arrival the server can see a rider
     * slightly inside the floor. Their client keeps them on top, so this must not block parking.
     */
    private static final double PLAYER_FEET_TOLERANCE = 0.5;
    private long arrivedAt = Long.MIN_VALUE / 2;

    private static boolean standsOn(Entity entity, AABB part) {
        return entity instanceof net.minecraft.world.entity.player.Player
                && entity.getBoundingBox().minY >= part.maxY - PLAYER_FEET_TOLERANCE;
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
                if (!level.getEntities((Entity)null,worldBox,e -> e.isAlive() && !e.isSpectator() && !standsOn(e,worldBox)).isEmpty()) {
                    status="Restoration blocked by entity";return false;
                }
            }
        }
        for(Part p:parts) {
            BlockPos pos=at.offset(p.offset);level.setBlock(pos,p.state,3);
            var be=level.getBlockEntity(pos);
            if(be!=null&&!p.data.isEmpty()){ CompoundTag data=p.data.copy();data.putInt("x",pos.getX());data.putInt("y",pos.getY());data.putInt("z",pos.getZ());be.loadWithComponents(data,level.registryAccess());be.setChanged(); }
        }
        parkedBounds=cabinBounds(at.getY()); parkedUntil=level.getGameTime()+PARKED_GRACE_TICKS;
        parts.clear(); nextParkAttempt=0; status="Ready"; owner.arrived(at);return true;
    }
    public void save(CompoundTag tag) {
        tag.putIntArray("CallQueue",callQueue.stream().mapToInt(Integer::intValue).toArray());
        tag.putLong("CabinOrigin",origin.asLong()); tag.putDouble("CabinY",y);tag.putDouble("CabinVelocity",velocity);tag.putBoolean("CabinStalled",stalled);tag.putDouble("CabinTarget",target);tag.putDouble("CabinSpeed",speed);tag.putString("CabinStatus",status);
        ListTag list=new ListTag();for(Part p:parts){CompoundTag t=new CompoundTag();t.putLong("Offset",p.offset.asLong());t.put("State",NbtUtils.writeBlockState(p.state));t.put("Data",p.data.copy());list.add(t);}tag.put("CabinParts",list);
    }
    /**
     * The server and saved data take the position as given. A client already simulating this
     * trip keeps its own position and blends the server's in (see clientTick); a newly
     * assembled cabin or a teleport-sized difference snaps instead.
     */
    private void readPosition(CompoundTag tag) {
        double serverY = tag.getDouble("CabinY");
        stalled = tag.getBoolean("CabinStalled");
        Level level = owner.getLevel();
        boolean simulating = level != null && level.isClientSide && active() && tag.contains("CabinParts")
                && !tag.getList("CabinParts", 10).isEmpty() && Math.abs(serverY - y) <= CLIENT_SNAP_DISTANCE;
        if (simulating) {
            clientCorrection = serverY - y;
            if (stalled) velocity = 0;
        } else {
            y = serverY;
            velocity = tag.getDouble("CabinVelocity");
            clientCorrection = 0;
        }
    }
    public void load(CompoundTag tag) {
        callQueue.clear();for(int stop:tag.getIntArray("CallQueue")) callQueue.addLast(stop);
        nextParkAttempt=0;
        origin=BlockPos.of(tag.getLong("CabinOrigin"));previousY=y;readPosition(tag);target=tag.getDouble("CabinTarget");speed=tag.contains("CabinSpeed")?ElevatorEnergyUsage.sanitizeSpeed(tag.getDouble("CabinSpeed")):ElevatorEnergyUsage.DEFAULT_SPEED;status=tag.getString("CabinStatus");if(!tag.contains("CabinParts"))return;parts.clear();
        for(Tag entry:tag.getList("CabinParts",10)){CompoundTag t=(CompoundTag)entry;parts.add(new Part(BlockPos.of(t.getLong("Offset")),NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),t.getCompound("State")),t.getCompound("Data")));}
    }
}
