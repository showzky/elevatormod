package com.showzky.elevatormod;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A single item places a coordinated two-wide, two-high sliding door. All four cells stay real
 * blocks in every state; the panels are drawn once per doorway by the master's renderer. The
 * selection shape always covers the closed doorway so an open door can still be aimed at, used
 * and broken, while the collision shape follows the panels and is empty when fully open.
 */
public class ElevatorDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ElevatorDoorBlock> CODEC = simpleCodec(ElevatorDoorBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final IntegerProperty FRAME = IntegerProperty.create("frame", 0, 8);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public enum Part implements StringRepresentable {
        LOWER_LEFT(false, false), LOWER_RIGHT(true, false), UPPER_LEFT(false, true), UPPER_RIGHT(true, true);
        final boolean right, upper;
        Part(boolean right, boolean upper) { this.right = right; this.upper = upper; }
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public ElevatorDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false).setValue(FRAME, 0).setValue(PART, Part.LOWER_LEFT));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos anchor = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (Part part : Part.values()) {
            BlockPos pos = position(anchor, facing, part);
            if (pos.getY() >= level.getMaxBuildHeight() || !level.hasChunkAt(pos)) return null;
            if (part != Part.LOWER_LEFT && !level.getBlockState(pos).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        for (Part part : Part.values()) if (part != Part.LOWER_LEFT)
            level.setBlock(position(pos, state.getValue(FACING), part), state.setValue(PART, part), 3);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, FRAME, PART);
    }
    private static BlockPos position(BlockPos anchor, Direction facing, Part part) {
        return anchor.relative(facing.getClockWise(), part.right ? 1 : 0).above(part.upper ? 1 : 0);
    }
    private static BlockPos anchor(BlockPos pos, BlockState state) {
        Part part = state.getValue(PART);
        return pos.relative(state.getValue(FACING).getClockWise(), part.right ? -1 : 0).below(part.upper ? 1 : 0);
    }
    private boolean belongs(BlockState state, Direction facing, Part part) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos root = anchor(pos, state);
            if (!level.hasChunkAt(root)) return InteractionResult.CONSUME;
            BlockState rootState = level.getBlockState(root);
            if (!belongs(rootState, state.getValue(FACING), Part.LOWER_LEFT)) return InteractionResult.CONSUME;
            boolean open = !rootState.getValue(OPEN);
            for (Part part : Part.values()) {
                BlockPos member = position(root, state.getValue(FACING), part);
                if (!level.hasChunkAt(member) || !belongs(level.getBlockState(member), state.getValue(FACING), part)) return InteractionResult.CONSUME;
            }
            for (Part part : Part.values()) {
                BlockPos member = position(root, state.getValue(FACING), part);
                level.setBlock(member, level.getBlockState(member).setValue(OPEN, open), 3);
            }
            level.scheduleTick(root, this, 1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != Part.LOWER_LEFT) return;
        Direction facing = state.getValue(FACING);
        for (Part part : Part.values()) {
            BlockPos member = position(pos, facing, part);
            if (!level.hasChunkAt(member)) { level.scheduleTick(pos, this, 20); return; }
            if (!belongs(level.getBlockState(member), facing, part)) return;
        }
        int frame = state.getValue(FRAME), target = state.getValue(OPEN) ? 8 : 0;
        if (frame == target) return;
        if (target == 0) {
            AABB doorway = new AABB(pos).minmax(new AABB(position(pos, facing, Part.UPPER_RIGHT))).deflate(0.05);
            if (!level.getEntities((net.minecraft.world.entity.Entity) null, doorway, e -> e.isAlive() && !e.isSpectator()).isEmpty()) {
                level.scheduleTick(pos, this, 2); return;
            }
        }
        int next = frame + Integer.signum(target - frame);
        for (Part part : Part.values()) {
            BlockPos member = position(pos, facing, part);
            level.setBlock(member, level.getBlockState(member).setValue(FRAME, next), 3);
        }
        if (next != target) level.scheduleTick(pos, this, ElevatorDoorMotion.stepDelay(next, target));
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock())) {
            BlockPos root = anchor(pos, state);
            Direction facing = state.getValue(FACING);
            for (Part part : Part.values()) {
                BlockPos member = position(root, facing, part);
                if (!member.equals(pos) && level.hasChunkAt(member) && belongs(level.getBlockState(member), facing, part))
                    level.removeBlock(member, false);
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
    /** Selection and targeting: the closed panel plane through this cell, in every state. */
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return panel(state.getValue(FACING), 0, 16);
    }

    /** Physical collision: only the part of this cell the panel still covers; empty when fully open. */
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        float offset = ElevatorDoorMotion.panelOffset(state.getValue(FRAME) / (float) ElevatorDoorMotion.FRAMES);
        if (offset >= ElevatorDoorMotion.TRAVEL_PIXELS - 1e-3F) return Shapes.empty();
        boolean right = state.getValue(PART).right;
        // The left panel slides towards local -X, the right panel towards +X.
        return right ? panel(state.getValue(FACING), offset, 16) : panel(state.getValue(FACING), 0, 16 - offset);
    }

    /** Box spanning local X {@code min..max} through the door plane (local Z 7..9), rotated to the facing. */
    private static VoxelShape panel(Direction facing, double min, double max) {
        if (max - min < 1e-3) return Shapes.empty();
        return switch (facing) {
            case EAST -> Block.box(7, 0, min, 9, 16, max);
            case SOUTH -> Block.box(16 - max, 0, 7, 16 - min, 16, 9);
            case WEST -> Block.box(7, 0, 16 - max, 9, 16, 16 - min);
            default -> Block.box(min, 0, 7, max, 16, 9);
        };
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    public static boolean isMaster(BlockState state) { return state.getValue(PART) == Part.LOWER_LEFT; }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new ElevatorDoorBlockEntity(pos, state) : null;
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != ElevatorRegistries.DOOR_ENTITY.get()) return null;
        return (tickerLevel, pos, tickerState, entity) -> ((ElevatorDoorBlockEntity) entity).clientTick(tickerState);
    }

    /**
     * Doors placed by older versions have no block entity, so nothing would draw their panels.
     * Looking it up on the client's main thread creates it; random display ticks reach every
     * nearby door within moments.
     */
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (isMaster(state)) level.getBlockEntity(pos);
    }
}
