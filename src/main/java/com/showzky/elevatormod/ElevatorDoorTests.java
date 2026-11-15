package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Door animation timing, independent selection/collision shapes and whole-doorway removal. */
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorDoorTests {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @GameTest(template = "empty", batch = "door_motion")
    public static void doorAnimationFollowsSuppliedKeyframes(GameTestHelper test) {
        test.assertTrue(ElevatorDoorMotion.panelOffset(0) == 0 && ElevatorDoorMotion.panelOffset(1) == 16, "Panels must travel 16 pixels");
        test.assertTrue(Math.abs(ElevatorDoorMotion.panelOffset(0.2F) - 1.6F) < 1e-4 && Math.abs(ElevatorDoorMotion.panelOffset(0.8F) - 14.4F) < 1e-4,
                "Keyframes at 0.15 s and 0.6 s must match the supplied clip");
        float previous = -1;
        for (int i = 0; i <= 100; i++) {
            float offset = ElevatorDoorMotion.panelOffset(i / 100F);
            test.assertTrue(offset >= previous, "Panel travel must be monotonic");
            test.assertTrue(i == 0 || offset - previous < 0.35F, "Panel travel must not jump between samples");
            previous = offset;
        }
        int total = 0;
        for (int frame = 0; frame < ElevatorDoorMotion.FRAMES; frame++) total += ElevatorDoorMotion.stepDelay(frame, ElevatorDoorMotion.FRAMES);
        test.assertTrue(total == ElevatorDoorMotion.TRAVEL_TICKS, "Server frames must span the 0.75 s clip, was " + total + " ticks");
        test.succeed();
    }

    @GameTest(template = "empty", batch = "door_open", timeoutTicks = 100)
    public static void openDoorIsTargetableWithoutCollision(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        BlockPos[] roots = {test.absolutePos(new BlockPos(1, 2, 1)), test.absolutePos(new BlockPos(5, 2, 1)),
                test.absolutePos(new BlockPos(1, 2, 5)), test.absolutePos(new BlockPos(5, 2, 5))};
        for (int i = 0; i < HORIZONTAL.length; i++) {
            placeDoor(level, roots[i], HORIZONTAL[i]);
            for (BlockPos cell : cells(roots[i], HORIZONTAL[i])) {
                var state = level.getBlockState(cell);
                test.assertTrue(!state.getCollisionShape(level, cell).isEmpty(), "Closed door must collide");
            }
            var root = level.getBlockState(roots[i]);
            root.useWithoutItem(level, ElevatorTouchscreenTests.player(level), new BlockHitResult(Vec3.atCenterOf(roots[i]), HORIZONTAL[i], roots[i], false));
        }
        test.runAfterDelay(ElevatorDoorMotion.TRAVEL_TICKS + 5, () -> {
            var player = ElevatorTouchscreenTests.player(level);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            for (int i = 0; i < HORIZONTAL.length; i++) {
                Direction facing = HORIZONTAL[i];
                for (BlockPos cell : cells(roots[i], facing)) {
                    var state = level.getBlockState(cell);
                    test.assertTrue(state.getValue(ElevatorDoorBlock.OPEN) && state.getValue(ElevatorDoorBlock.FRAME) == ElevatorDoorMotion.FRAMES, "Door must finish opening within the clip");
                    test.assertTrue(state.getCollisionShape(level, cell).isEmpty(), "Open doorway must not collide");
                    test.assertTrue(!state.getShape(level, cell).isEmpty(), "Open doorway must keep its selection shape");
                    // Aim through the empty doorway from in front, at both the lower and upper halves.
                    Vec3 center = Vec3.atCenterOf(cell);
                    Vec3 eye = center.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(1.2));
                    var outline = level.clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
                    test.assertTrue(outline.getType() == HitResult.Type.BLOCK && outline.getBlockPos().equals(cell), "Open doorway cell " + cell + " must be targetable, got " + outline.getType() + " at " + outline.getBlockPos() + " " + outline.getLocation() + " shape " + state.getShape(level, cell).bounds() + " from " + eye);
                    var physical = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                    test.assertTrue(physical.getType() == HitResult.Type.MISS, "Open doorway must have no physical barrier");
                }
                // Break the upper-right section of the open door as a survival player.
                AABB area = new AABB(roots[i]).inflate(3);
                ElevatorTouchscreenTests.clearItems(level, area);
                BlockPos upperRight = roots[i].relative(facing.getClockWise()).above();
                test.assertTrue(player.gameMode.destroyBlock(upperRight), "Open door must be breakable");
                for (BlockPos cell : cells(roots[i], facing)) test.assertTrue(level.getBlockState(cell).isAir(), "Breaking one section must remove the doorway");
                test.assertTrue(ElevatorTouchscreenTests.countItems(level, area, ElevatorRegistries.DOOR.get().asItem()) == 1, "Breaking must drop exactly one door");
                ElevatorTouchscreenTests.clearItems(level, area);
            }
            test.succeed();
        });
    }

    @GameTest(template = "empty", batch = "door_close", timeoutTicks = 120)
    public static void closingDoorRestoresCollision(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        BlockPos root = test.absolutePos(new BlockPos(3, 2, 3));
        placeDoor(level, root, Direction.EAST);
        var hit = new BlockHitResult(Vec3.atCenterOf(root), Direction.EAST, root, false);
        level.getBlockState(root).useWithoutItem(level, ElevatorTouchscreenTests.player(level), hit);
        test.runAfterDelay(ElevatorDoorMotion.TRAVEL_TICKS + 5, () -> {
            test.assertTrue(level.getBlockState(root).getCollisionShape(level, root).isEmpty(), "Door must be open");
            // Use the open door through its empty doorway to close it.
            level.getBlockState(root.above()).useWithoutItem(level, ElevatorTouchscreenTests.player(level), new BlockHitResult(Vec3.atCenterOf(root.above()), Direction.EAST, root.above(), false));
            test.runAfterDelay(4, () -> test.assertTrue(!level.getBlockState(root).getCollisionShape(level, root).isEmpty(), "Collision must return while closing"));
            test.runAfterDelay(ElevatorDoorMotion.TRAVEL_TICKS + 5, () -> {
                for (BlockPos cell : cells(root, Direction.EAST)) {
                    var state = level.getBlockState(cell);
                    test.assertTrue(state.getValue(ElevatorDoorBlock.FRAME) == 0 && !state.getValue(ElevatorDoorBlock.OPEN), "Door must close fully");
                    test.assertTrue(state.getCollisionShape(level, cell).bounds().equals(state.getShape(level, cell).bounds()), "Closed collision must match the panel plane");
                }
                test.succeed();
            });
        });
    }

    static void placeDoor(ServerLevel level, BlockPos root, Direction facing) {
        for (var part : ElevatorDoorBlock.Part.values()) {
            BlockPos pos = root.relative(facing.getClockWise(), part.right ? 1 : 0).above(part.upper ? 1 : 0);
            level.setBlock(pos, ElevatorRegistries.DOOR.get().defaultBlockState().setValue(ElevatorDoorBlock.FACING, facing).setValue(ElevatorDoorBlock.PART, part), 3);
        }
    }

    static BlockPos[] cells(BlockPos root, Direction facing) {
        BlockPos right = root.relative(facing.getClockWise());
        return new BlockPos[]{root, right, root.above(), right.above()};
    }

    private ElevatorDoorTests() {}
}
