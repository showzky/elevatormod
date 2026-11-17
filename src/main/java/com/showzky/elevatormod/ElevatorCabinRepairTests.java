package com.showzky.elevatormod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A parked cabin that loads with blocks missing (a neighbouring chunk saved at another moment)
 * is put back from the pulley's snapshot, while blocks removed before saving stay removed.
 */
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorCabinRepairTests {

    @GameTest(template = "empty", batch = "cabin_repair", timeoutTicks = 100)
    public static void missingBlocksAreRestoredAfterLoading(GameTestHelper test) {
        var level = test.getLevel();
        var cabin = ElevatorDoorAutomationTests.DoorCabin.build(test);
        var pulley = cabin.pulley();
        var saved = pulley.saveWithoutMetadata(level.registryAccess());
        // The chunk holding these blocks came back from disk without them.
        var controller = cabin.base().north();
        var wall = cabin.base().east(2);
        level.setBlock(controller, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
        pulley.loadWithComponents(saved, level.registryAccess());
        test.succeedWhen(() -> {
            test.assertTrue(level.getBlockState(controller).is(ElevatorRegistries.CONTROLLER), "The controller must be restored");
            test.assertTrue(level.getBlockState(wall).is(ElevatorRegistries.ELEVATOR_BLOCK), "The cabin block must be restored");
            test.assertTrue(pulley.attachment() != null && "Cabin repaired".equals(pulley.motion.status),
                    "The pulley must stay connected; status=" + pulley.motion.status);
        });
    }

    @GameTest(template = "empty", batch = "cabin_repair_removed", timeoutTicks = 100)
    public static void blocksRemovedBeforeSavingStayRemoved(GameTestHelper test) {
        var level = test.getLevel();
        var cabin = ElevatorDoorAutomationTests.DoorCabin.build(test);
        var pulley = cabin.pulley();
        var wall = cabin.base().east(2);
        // A player breaks a cabin block (and keeps the item), then the world is saved and reloaded.
        level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
        var saved = pulley.saveWithoutMetadata(level.registryAccess());
        pulley.loadWithComponents(saved, level.registryAccess());
        test.runAfterDelay(10, () -> {
            test.assertTrue(level.getBlockState(wall).isAir(), "A block removed before saving must not come back");
            test.assertTrue(pulley.attachment() != null, "The rest of the cabin must stay connected");
            test.succeed();
        });
    }

    @GameTest(template = "empty", batch = "cabin_repair_rebuilt", timeoutTicks = 100)
    public static void rebuiltSpaceIsNeverOverwritten(GameTestHelper test) {
        var level = test.getLevel();
        var cabin = ElevatorDoorAutomationTests.DoorCabin.build(test);
        var pulley = cabin.pulley();
        var saved = pulley.saveWithoutMetadata(level.registryAccess());
        var wall = cabin.base().east(2);
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        pulley.loadWithComponents(saved, level.registryAccess());
        test.runAfterDelay(10, () -> {
            test.assertTrue(level.getBlockState(wall).is(Blocks.STONE), "Another block in the cabin's space must not be replaced");
            test.succeed();
        });
    }

    private ElevatorCabinRepairTests() {}
}
