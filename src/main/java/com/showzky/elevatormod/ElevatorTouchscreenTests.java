package com.showzky.elevatormod;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Touchscreen placement, linking, touch resolution, modes, cabin transport and persistence. */
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorTouchscreenTests {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @GameTest(template = "empty", batch = "touchscreen_geometry")
    public static void touchscreenHitMapping(GameTestHelper test) {
        BlockPos master = new BlockPos(100, 64, -40);
        Vec3 corner = Vec3.atLowerCornerOf(master);
        for (TouchscreenSize size : TouchscreenSize.values()) {
            for (Direction facing : HORIZONTAL) {
                Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
                for (double cx = 0.5; cx < size.canvasWidth(); cx += size.canvasWidth() / 7.0) {
                    for (double cy = 0.5; cy < size.canvasHeight(); cy += size.canvasHeight() / 9.0) {
                        Vec3 point = TouchscreenGeometry.toWorld(TouchscreenGeometry.canvasToModel(size, cx, cy), corner, facing);
                        // An oblique ray from in front and slightly to the side.
                        Vec3 eye = point.add(normal.scale(1.7)).add(0.3, 0.4, -0.2);
                        var hit = TouchscreenGeometry.intersect(size, facing, corner, eye, point.subtract(eye), 5);
                        test.assertTrue(hit != null && Math.abs(hit.canvasX() - cx) < 1e-6 && Math.abs(hit.canvasY() - cy) < 1e-6,
                                "Hit mapping failed for " + size + " " + facing + " at " + cx + "," + cy + ": " + hit);
                        Vec3 behind = point.subtract(normal.scale(1.7));
                        test.assertTrue(TouchscreenGeometry.intersect(size, facing, corner, behind, point.subtract(behind), 5) == null,
                                "Touches from behind must be rejected for " + facing);
                    }
                }
                // The housing border is not part of the touch surface.
                Vec3 border = TouchscreenGeometry.toWorld(new Vec3(1, 8, TouchscreenSize.GLASS_DEPTH_PIXELS), corner, facing);
                Vec3 eye = border.add(normal.scale(2));
                test.assertTrue(TouchscreenGeometry.intersect(size, facing, corner, eye, border.subtract(eye), 5) == null, "Border must not be touchable");
                // The bottom-left master is the viewer's left; the second column lies to the viewer's right.
                Vec3 rightEdge = TouchscreenGeometry.toWorld(new Vec3(size.width * 16 - 0.5, 1, 1), corner, facing);
                test.assertTrue(BlockPos.containing(rightEdge).equals(TouchscreenGeometry.section(master, facing, size.width - 1, 0)),
                        "Model X must run towards the screen's extra columns for " + facing);
            }
        }
        test.succeed();
    }

    @GameTest(template = "empty", batch = "touchscreen_placement")
    public static void touchscreenPlacementAndRemoval(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        FakePlayer player = player(level);
        BlockPos support = test.absolutePos(new BlockPos(4, 2, 4));
        AABB area = new AABB(support).inflate(4);
        for (var block : screenBlocks()) {
            for (Direction facing : HORIZONTAL) {
                level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
                ItemStack stack = new ItemStack(block);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var hit = new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5)), facing, support, false);
                test.assertTrue(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Placement must succeed for " + block.size() + " " + facing);
                BlockPos master = support.relative(facing);
                for (var cell : block.cells(master, facing)) {
                    var state = level.getBlockState(cell.pos());
                    test.assertTrue(state.is(block) && state.getValue(ElevatorTouchscreenBlock.FACING) == facing
                                    && state.getValue(ElevatorTouchscreenBlock.COLUMN) == cell.column() && state.getValue(ElevatorTouchscreenBlock.ROW) == cell.row(),
                            "Missing or wrong section " + cell + " for " + block.size() + " " + facing);
                    BlockEntity entity = level.getBlockEntity(cell.pos());
                    test.assertTrue((cell.column() == 0 && cell.row() == 0) == (entity instanceof ElevatorTouchscreenBlockEntity), "Only the master owns the block entity");
                }
                // Break the section furthest from the master as a survival player with a pickaxe.
                var cells = block.cells(master, facing);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                test.assertTrue(player.gameMode.destroyBlock(cells.getLast().pos()), "Section must be breakable");
                for (var cell : cells) test.assertTrue(level.getBlockState(cell.pos()).isAir(), "Breaking one section must remove the whole display");
                test.assertTrue(countItems(level, area, block.asItem()) == 1, "Breaking must drop exactly one " + block.size() + " item");
                clearItems(level, area);
            }
            // Placement is refused when any section's space is occupied.
            if (block.size() != TouchscreenSize.SMALL) {
                BlockPos master = support.relative(Direction.EAST);
                BlockPos blocker = block.cells(master, Direction.EAST).getLast().pos();
                level.setBlock(blocker, Blocks.STONE.defaultBlockState(), 3);
                ItemStack stack = new ItemStack(block);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0.5, 0, 0), Direction.EAST, support, false);
                stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                test.assertTrue(level.getBlockState(master).isAir(), "Obstructed " + block.size() + " placement must be refused");
                level.setBlock(blocker, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        test.succeed();
    }

    @GameTest(template = "empty", batch = "touchscreen_destination", timeoutTicks = 60)
    public static void touchscreenDestinationAndInformationModes(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = elevator(test, origin);
        var screen = placeScreen(level, TouchscreenSize.TALL, origin.east(3), Direction.EAST);
        refreshLinks(level, screen);
        test.assertTrue(screen.connection() == TouchscreenDisplayState.Connection.LANDING, "Screen must link like a call button, was " + screen.connection());
        test.assertTrue(screen.pulleyPos().equals(pulley.getBlockPos()) && screen.callStop() == origin.getY(), "Screen must link to the adjacent landing");
        var state = screen.displayState();
        test.assertTrue(state.floors().size() == 2 && state.floors().getFirst().stop() == origin.getY() + 3, "Floors must be listed top first: " + state.floors());
        test.assertTrue(state.currentIndex() == 1, "Current floor must be the parked landing");

        // Information mode ignores touches on the floor list.
        screen.setSettings(screen.settings().withMode(TouchscreenMode.INFORMATION));
        Vec3 upperTile = tileCenter(screen, 0);
        TouchscreenInteraction.touch(aimAt(level, upperTile, Direction.EAST), screen);
        test.assertTrue(!pulley.motion.active(), "Information mode must not request travel");

        // A touch that misses the glass does nothing.
        screen.setSettings(screen.settings().withMode(TouchscreenMode.DESTINATION));
        TouchscreenInteraction.touch(aimAt(level, upperTile.add(0, 3, 0), Direction.EAST), screen);
        test.assertTrue(!pulley.motion.active(), "A touch beside the screen must be ignored");

        TouchscreenInteraction.touch(aimAt(level, tileCenter(screen, 0), Direction.EAST), screen);
        // Without power the cabin stays parked with the trip queued (it departs once powered).
        test.assertTrue(!pulley.motion.active() && pulley.motion.hasCall(origin.getY() + 3), "Destination touch must request the upper floor");
        test.assertTrue(screen.settings().feedback() == TouchscreenSettings.Feedback.WAITING_POWER, "Unpowered request must report waiting for power");
        var moving = screen.displayState();
        test.assertTrue(moving.destinationIndex() == 0 && moving.travel() == TouchscreenDisplayState.Travel.UP, "Display must show the destination and direction");
        test.succeed();
    }

    @GameTest(template = "empty", batch = "touchscreen_call", timeoutTicks = 60)
    public static void touchscreenCallMode(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = elevator(test, origin);
        var lower = placeScreen(level, TouchscreenSize.SMALL, origin.east(3), Direction.EAST);
        var upper = placeScreen(level, TouchscreenSize.LARGE, origin.east(3).above(3), Direction.EAST);
        refreshLinks(level, lower, upper);
        test.assertTrue(upper.callStop() == origin.getY() + 3 && lower.callStop() == origin.getY(), "Each screen must serve its own landing");
        for (var screen : new ElevatorTouchscreenBlockEntity[]{lower, upper}) screen.setSettings(screen.settings().withMode(TouchscreenMode.CALL));

        TouchscreenInteraction.touch(aimAt(level, callButtonCenter(lower), Direction.EAST), lower);
        test.assertTrue(!pulley.motion.active() && lower.settings().feedback() == TouchscreenSettings.Feedback.ALREADY_HERE, "Calling to the current landing must report already here");

        TouchscreenInteraction.touch(aimAt(level, callButtonCenter(upper), Direction.EAST), upper);
        test.assertTrue(!pulley.motion.active() && pulley.motion.hasCall(origin.getY() + 3), "Call button must call the elevator to the upper landing");
        test.assertTrue(upper.displayState().callPending(), "Display must show the pending call");
        test.succeed();
    }

    @GameTest(template = "empty", batch = "touchscreen_linking", timeoutTicks = 60)
    public static void touchscreenAmbiguousLink(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        BlockPos first = test.absolutePos(new BlockPos(2, 2, 1));
        elevator(test, first);
        elevator(test, first.south(4));
        // Equidistant from both shafts' landings at the same height: ambiguous, exactly like a call button.
        var ambiguous = placeScreen(level, TouchscreenSize.SMALL, first.east(4).south(2), Direction.EAST);
        refreshLinks(level, ambiguous);
        test.assertTrue(ambiguous.connection() == TouchscreenDisplayState.Connection.AMBIGUOUS, "Equidistant shafts must be ambiguous, was " + ambiguous.connection());
        test.succeed();
    }

    @GameTest(template = "empty", batch = "touchscreen_range", timeoutTicks = 60)
    public static void touchscreenLinkRangeAndRemoval(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        // Landing connectors at relative (3, 2, 0) and (3, 5, 0); every screen stays inside this test's area.
        BlockPos origin = test.absolutePos(new BlockPos(1, 2, 0));
        elevator(test, origin);
        var outOfRange = placeScreen(level, TouchscreenSize.SMALL, test.absolutePos(new BlockPos(7, 2, 7)), Direction.EAST);
        var high = placeScreen(level, TouchscreenSize.SMALL, test.absolutePos(new BlockPos(4, 14, 0)), Direction.EAST);
        refreshLinks(level, outOfRange, high);
        test.assertTrue(outOfRange.connection() == TouchscreenDisplayState.Connection.NO_ELEVATOR, "Screen beyond 8 blocks must not link, was " + outOfRange.connection());
        test.assertTrue(high.connection() == TouchscreenDisplayState.Connection.LANDING, "Height must not limit linking, was " + high.connection());
        var linked = ElevatorLandingLocator.cached(level, high.getBlockPos());
        var live = ElevatorLandingLocator.live(level, high.getBlockPos());
        test.assertTrue(linked.size() == 1 && live.size() == 1 && linked.getFirst().connector().equals(live.getFirst().connector()),
                "Cached and live linking must select the same landing");
        // Removing the landing connectors invalidates the link on the next refresh.
        level.setBlock(origin.east(2), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(origin.east(2).above(3), Blocks.AIR.defaultBlockState(), 3);
        refreshLinks(level, high);
        test.assertTrue(high.connection() == TouchscreenDisplayState.Connection.NO_ELEVATOR, "Removed connectors must unlink the screen, was " + high.connection());
        test.succeed();
    }

    @GameTest(template = "empty", batch = "touchscreen_cabin", timeoutTicks = 400)
    public static void touchscreenInMovingCabin(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        BlockPos pulleyPos = origin.above(7);
        var pulley = elevator(test, origin);
        BlockPos master = origin.below().north();
        var screen = placeScreen(level, TouchscreenSize.TALL, master, Direction.NORTH);
        screen.setSettings(screen.settings().withMode(TouchscreenMode.INFORMATION));
        // Facing the cabin but mounted elsewhere: must stay behind.
        BlockPos unmounted = origin.below().west();
        placeScreen(level, TouchscreenSize.SMALL, unmounted, Direction.EAST);
        var scan = ElevatorAssembly.scan(level, pulleyPos);
        test.assertTrue(scan.components().contains(master) && scan.components().contains(master.above()), "A screen mounted on the cabin must join it as a whole");
        test.assertTrue(!scan.components().contains(unmounted), "A screen not mounted on the cabin must not join it");
        refreshLinks(level, screen);
        test.assertTrue(screen.connection() == TouchscreenDisplayState.Connection.CABIN, "A parked cabin screen must link to its own cabin");

        pulley.energy().receiveEnergy(100000, false);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        test.assertTrue(level.getBlockState(master).isAir() && level.getBlockState(master.above()).isAir(), "Carried screen sections must leave the world");
        BlockPos offset = master.subtract(pulley.motion.origin);
        int index = TouchscreenInteraction.partIndex(pulley, offset);
        test.assertTrue(index >= 0 && TouchscreenSettings.read(pulley.motion.parts.get(index).data()).mode() == TouchscreenMode.INFORMATION, "Carried screen must keep its settings");

        FakePlayer player = aimAt(level, Vec3.atCenterOf(master), Direction.NORTH);
        TouchscreenInteraction.configure(player, TouchscreenTarget.cabin(pulleyPos, offset), TouchscreenMode.DESTINATION);
        test.assertTrue(TouchscreenSettings.read(pulley.motion.parts.get(index).data()).mode() == TouchscreenMode.DESTINATION, "Mode must be configurable while carried");

        var state = TouchscreenDisplayState.of(TouchscreenDisplayState.Connection.CABIN, pulley, TouchscreenDisplayState.NO_STOP);
        var layout = TouchscreenLayout.compute(TouchscreenSize.TALL, TouchscreenMode.DESTINATION, state, 0);
        var tile = layout.tiles().stream().filter(t -> t.floorIndex() == 0).findFirst().orElseThrow().rect();
        Vec3 corner = TouchscreenInteraction.cabinCorner(pulley, offset, pulley.motion.y);
        Vec3 target = TouchscreenGeometry.toWorld(TouchscreenGeometry.canvasToModel(TouchscreenSize.TALL, tile.x() + tile.w() / 2, tile.y() + tile.h() / 2), corner, Direction.NORTH);
        TouchscreenInteraction.touchCabin(aimAt(level, target, Direction.NORTH), pulleyPos, offset, pulley.motion.y);
        test.assertTrue((int) pulley.motion.target == origin.getY() + 3, "Touching a carried screen must request the upper floor");

        BlockPos arrived = master.above(3);
        test.succeedWhen(() -> {
            test.assertTrue(!pulley.motion.active(), "Cabin must park at the destination");
            var restored = level.getBlockState(arrived);
            test.assertTrue(restored.is(ElevatorRegistries.TOUCHSCREEN_1X2) && restored.getValue(ElevatorTouchscreenBlock.FACING) == Direction.NORTH
                    && ElevatorTouchscreenBlock.isMaster(restored) && level.getBlockState(arrived.above()).is(ElevatorRegistries.TOUCHSCREEN_1X2), "Screen must be restored whole at the new floor");
            var entity = level.getBlockEntity(arrived) instanceof ElevatorTouchscreenBlockEntity restoredScreen ? restoredScreen : null;
            test.assertTrue(entity != null && entity.settings().mode() == TouchscreenMode.DESTINATION, "Restored screen must keep its mode");
        });
    }

    @GameTest(template = "empty", batch = "touchscreen_persistence", timeoutTicks = 60)
    public static void touchscreenPersistence(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        elevator(test, origin);
        var screen = placeScreen(level, TouchscreenSize.LARGE, origin.east(3), Direction.EAST);
        refreshLinks(level, screen);
        screen.setSettings(screen.settings().withMode(TouchscreenMode.CALL).withPage(1));
        var saved = screen.saveWithFullMetadata(level.registryAccess());
        var loaded = BlockEntity.loadStatic(screen.getBlockPos(), screen.getBlockState(), saved, level.registryAccess());
        test.assertTrue(loaded instanceof ElevatorTouchscreenBlockEntity copy && copy.settings().equals(screen.settings())
                && copy.connection() == screen.connection() && copy.pulleyPos().equals(screen.pulleyPos()) && copy.callStop() == screen.callStop(),
                "Settings and link must survive saving and loading");
        test.succeed();
    }

    // ---- fixtures ----

    private static ElevatorTouchscreenBlock[] screenBlocks() {
        return new ElevatorTouchscreenBlock[]{ElevatorRegistries.TOUCHSCREEN_1X1.get(), ElevatorRegistries.TOUCHSCREEN_1X2.get(), ElevatorRegistries.TOUCHSCREEN_2X2.get()};
    }

    /** A parked one-block cabin with landings at the attachment height and three blocks above. */
    static PoweredPulleyBlockEntity elevator(GameTestHelper test, BlockPos origin) {
        var level = test.getLevel();
        BlockPos pulleyPos = origin.above(7);
        level.setBlock(pulleyPos, ElevatorRegistries.PULLEY.get().defaultBlockState(), 3);
        level.setBlock(origin, ElevatorRegistries.ATTACHMENT.get().defaultBlockState(), 3);
        level.setBlock(origin.below(), ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(origin.west(), ElevatorRegistries.CONTROLLER.get().defaultBlockState(), 3);
        level.setBlock(origin.east(), ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING, Direction.EAST), 3);
        level.setBlock(origin.east(2), ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING, Direction.WEST), 3);
        level.setBlock(origin.east(2).above(3), ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING, Direction.WEST), 3);
        var pulley = (PoweredPulleyBlockEntity) level.getBlockEntity(pulleyPos);
        test.assertTrue(pulley.motion.assemble(), "Elevator must connect: " + pulley.motion.status);
        test.assertTrue(pulley.motion.disassemble(), "Elevator must park: " + pulley.motion.status);
        return pulley;
    }

    static ElevatorTouchscreenBlockEntity placeScreen(ServerLevel level, TouchscreenSize size, BlockPos master, Direction facing) {
        var block = switch (size) {
            case SMALL -> ElevatorRegistries.TOUCHSCREEN_1X1.get();
            case TALL -> ElevatorRegistries.TOUCHSCREEN_1X2.get();
            case LARGE -> ElevatorRegistries.TOUCHSCREEN_2X2.get();
        };
        level.setBlock(master, block.defaultBlockState().setValue(ElevatorTouchscreenBlock.FACING, facing), 3);
        block.placeSections(level, master, facing);
        return (ElevatorTouchscreenBlockEntity) level.getBlockEntity(master);
    }

    /** Refreshes every pulley's floor directory, then the given screens' links, as the periodic ticks would. */
    static void refreshLinks(ServerLevel level, ElevatorTouchscreenBlockEntity... screens) {
        for (var pulley : ElevatorCabinMotion.loaded(level)) pulley.directory().refresh(pulley);
        for (var screen : screens) screen.refreshLink();
    }

    static FakePlayer player(ServerLevel level) {
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        player.moveTo(0, level.getMinBuildHeight() + 1, 0, 0, 0);
        return player;
    }

    /** Positions the player's eye 1.5 blocks in front of a screen facing {@code facing}, looking at {@code target}. */
    static FakePlayer aimAt(ServerLevel level, Vec3 target, Direction facing) {
        FakePlayer player = player(level);
        Vec3 eye = target.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(1.5)).add(0.2, 0.1, 0.2);
        player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, 0, 0);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        return player;
    }

    private static Vec3 tileCenter(ElevatorTouchscreenBlockEntity screen, int floorIndex) {
        var layout = TouchscreenLayout.compute(screen.size(), TouchscreenMode.DESTINATION, screen.displayState(), screen.settings().page());
        var rect = layout.tiles().stream().filter(tile -> tile.floorIndex() == floorIndex).findFirst().orElseThrow().rect();
        return canvasPoint(screen, rect.x() + rect.w() / 2, rect.y() + rect.h() / 2);
    }

    private static Vec3 callButtonCenter(ElevatorTouchscreenBlockEntity screen) {
        var rect = TouchscreenLayout.compute(screen.size(), TouchscreenMode.CALL, screen.displayState(), 0).callButton();
        return canvasPoint(screen, rect.x() + rect.w() / 2, rect.y() + rect.h() / 2);
    }

    private static Vec3 canvasPoint(ElevatorTouchscreenBlockEntity screen, double x, double y) {
        return TouchscreenGeometry.toWorld(TouchscreenGeometry.canvasToModel(screen.size(), x, y), Vec3.atLowerCornerOf(screen.getBlockPos()), screen.facing());
    }

    static int countItems(ServerLevel level, AABB area, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, area, entity -> entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    static void clearItems(ServerLevel level, AABB area) {
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
    }

    private ElevatorTouchscreenTests() {}
}
