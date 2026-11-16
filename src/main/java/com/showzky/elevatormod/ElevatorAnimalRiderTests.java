package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Animals inside a cabin ride along at top speed, arrive on the floor unharmed, and do not stop parking. */
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorAnimalRiderTests {
    private static final int TRAVEL = 3;

    @GameTest(template = "empty", batch = "animal_up", timeoutTicks = 200)
    public static void cowRidesUpAtTopSpeed(GameTestHelper test) {
        ride(test, test.absolutePos(new BlockPos(3, 5, 3)), TRAVEL);
    }

    @GameTest(template = "empty", batch = "animal_down", timeoutTicks = 200)
    public static void cowRidesDownAtTopSpeed(GameTestHelper test) {
        ride(test, test.absolutePos(new BlockPos(3, 8, 3)), -TRAVEL);
    }

    private static void ride(GameTestHelper test, BlockPos attachment, int travel) {
        var level = test.getLevel();
        var pulley = enclosedCabin(test, attachment, travel);
        BlockPos floor = attachment.below(3);
        Cow cow = EntityType.COW.create(level);
        cow.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 0);
        level.addFreshEntity(cow);
        float health = cow.getHealth();

        pulley.motion.speed = ElevatorEnergyUsage.MAX_SPEED;
        pulley.energy().receiveEnergy(100000, false);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble with a cow inside: " + pulley.motion.status);
        int stop = attachment.getY() + travel;
        pulley.motion.request(stop);
        test.succeedWhen(() -> {
            test.assertTrue(!pulley.motion.active() && pulley.attachment() != null && pulley.attachment().getY() == stop,
                    "Cabin must arrive and park with the cow inside; status=" + pulley.motion.status + " y=" + pulley.motion.y);
            double floorTop = floor.getY() + travel + 1;
            test.assertTrue(cow.isAlive() && Math.abs(cow.getY() - floorTop) < 0.05,
                    "Cow must arrive standing on the cabin floor, feet " + (cow.getY() - floorTop) + " from it");
            test.assertTrue(cow.getHealth() == health, "Cow must not be hurt by the ride, health " + cow.getHealth() + "/" + health);
        });
    }

    /**
     * A cabin with a 3x3 floor and roof around a single 1x2 interior cell the cow cannot leave,
     * the rope attachment in the roof's centre, and landings at the start and destination.
     */
    private static PoweredPulleyBlockEntity enclosedCabin(GameTestHelper test, BlockPos attachment, int travel) {
        var level = test.getLevel();
        var block = ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState();
        BlockPos floor = attachment.below(3);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            level.setBlock(floor.offset(dx, 0, dz), block, 3);
            level.setBlock(attachment.offset(dx, 0, dz), block, 3);
            boolean interior = dx == 0 && dz == 0;
            for (int dy = 1; dy <= 2; dy++)
                level.setBlock(floor.offset(dx, dy, dz), interior ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : block, 3);
        }
        level.setBlock(attachment, ElevatorRegistries.ATTACHMENT.get().defaultBlockState(), 3);
        level.setBlock(attachment.offset(-1, 0, -1), ElevatorRegistries.CONTROLLER.get().defaultBlockState(), 3);
        BlockPos cabinConnector = floor.east(2);
        level.setBlock(cabinConnector, ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING, Direction.EAST), 3);
        var landing = ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING, Direction.WEST);
        level.setBlock(cabinConnector.east(), landing, 3);
        level.setBlock(cabinConnector.east().above(travel), landing, 3);
        BlockPos pulleyPos = attachment.above(Math.max(travel, 0) + 5);
        level.setBlock(pulleyPos, ElevatorRegistries.PULLEY.get().defaultBlockState(), 3);
        return (PoweredPulleyBlockEntity) level.getBlockEntity(pulleyPos);
    }

    private ElevatorAnimalRiderTests() {}
}
