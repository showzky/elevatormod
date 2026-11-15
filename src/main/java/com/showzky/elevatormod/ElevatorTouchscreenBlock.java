package com.showzky.elevatormod;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A thin wall-mounted elevator display. Larger sizes are multiblocks: each occupied cell is a
 * real block identified by its column and row, and every cell routes to the bottom-left master,
 * which owns the block entity, settings and rendering.
 */
public final class ElevatorTouchscreenBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 1);
    public static final MapCodec<ElevatorTouchscreenBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TouchscreenSize.CODEC.fieldOf("size").forGetter(ElevatorTouchscreenBlock::size),
            propertiesCodec()).apply(instance, ElevatorTouchscreenBlock::new));
    private static final float DEPTH = TouchscreenSize.GLASS_DEPTH_PIXELS;
    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 16 - DEPTH, 16, 16, 16);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, DEPTH);
    private static final VoxelShape EAST_SHAPE = Block.box(0, 0, 0, DEPTH, 16, 16);
    private static final VoxelShape WEST_SHAPE = Block.box(16 - DEPTH, 0, 0, 16, 16, 16);

    /** One occupied cell of a screen. */
    public record Cell(BlockPos pos, int column, int row) {}

    private final TouchscreenSize size;

    public ElevatorTouchscreenBlock(TouchscreenSize size, Properties properties) {
        super(properties);
        this.size = size;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 0).setValue(ROW, 0));
    }

    public TouchscreenSize size() { return size; }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, COLUMN, ROW); }

    public static boolean isMaster(BlockState state) {
        return state.getBlock() instanceof ElevatorTouchscreenBlock && state.getValue(COLUMN) == 0 && state.getValue(ROW) == 0;
    }

    public static BlockPos master(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getCounterClockWise(), -state.getValue(COLUMN)).below(state.getValue(ROW));
    }

    public List<Cell> cells(BlockPos master, Direction facing) {
        List<Cell> cells = new ArrayList<>(size.width * size.height);
        for (int row = 0; row < size.height; row++)
            for (int column = 0; column < size.width; column++)
                cells.add(new Cell(TouchscreenGeometry.section(master, facing, column, row), column, row));
        return cells;
    }

    private boolean belongs(BlockState state, Direction facing, Cell cell) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(COLUMN) == cell.column() && state.getValue(ROW) == cell.row();
    }

    /** All cells of the complete screen containing {@code pos}, or an empty list if it is incomplete or unloaded. */
    public List<BlockPos> intactCells(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        List<BlockPos> positions = new ArrayList<>();
        for (Cell cell : cells(master(pos, state), facing)) {
            if (!level.hasChunkAt(cell.pos()) || !belongs(level.getBlockState(cell.pos()), facing, cell)) return List.of();
            positions.add(cell.pos());
        }
        return positions;
    }

    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        for (Cell cell : cells(master, facing)) {
            BlockPos pos = cell.pos();
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
            if (!pos.equals(master) && !level.getBlockState(pos).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        placeSections(level, pos, state.getValue(FACING));
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ElevatorTouchscreenBlockEntity screen) screen.refreshLink();
    }

    /** Places every non-master section next to an existing master. */
    public void placeSections(Level level, BlockPos master, Direction facing) {
        BlockState base = defaultBlockState().setValue(FACING, facing);
        for (Cell cell : cells(master, facing))
            if (cell.column() != 0 || cell.row() != 0)
                level.setBlock(cell.pos(), base.setValue(COLUMN, cell.column()).setValue(ROW, cell.row()), 3);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock())) {
            // Removing any section removes the whole display; only the broken section drops an item.
            Direction facing = state.getValue(FACING);
            for (Cell cell : cells(master(pos, state), facing))
                if (!cell.pos().equals(pos) && level.hasChunkAt(cell.pos()) && belongs(level.getBlockState(cell.pos()), facing, cell))
                    level.removeBlock(cell.pos(), false);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos master = master(pos, state);
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(master) instanceof ElevatorTouchscreenBlockEntity screen) {
            if (player.isSecondaryUseActive()) TouchscreenInteraction.openSettings(serverPlayer, TouchscreenTarget.stationary(master), screen.settings().mode());
            else TouchscreenInteraction.touch(serverPlayer, screen);
        }
        return InteractionResult.CONSUME;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new ElevatorTouchscreenBlockEntity(pos, state) : null;
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ElevatorRegistries.TOUCHSCREEN_ENTITY.get()) return null;
        return (tickerLevel, pos, tickerState, entity) -> ElevatorTouchscreenBlockEntity.serverTick(tickerLevel, pos, (ElevatorTouchscreenBlockEntity) entity);
    }
}
