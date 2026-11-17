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

    /** A small flying mob hovering where the cabin floor lands is lifted aside instead of blocking parking. */
    @GameTest(template = "empty", batch = "flying_mob_in_floor", timeoutTicks = 300)
    public static void hoveringMobDoesNotBlockParking(GameTestHelper test) {
        var level = test.getLevel();
        var cabin = ElevatorDoorAutomationTests.DoorCabin.build(test);
        int travel = ElevatorDoorAutomationTests.TRAVEL;
        BlockPos floorAtStop = cabin.base().east(1).above(travel);
        var bat = net.minecraft.world.entity.EntityType.BAT.create(level);
        bat.setNoAi(true);
        bat.setNoGravity(true);
        bat.moveTo(floorAtStop.getX() + 0.5, floorAtStop.getY() + 0.2, floorAtStop.getZ() + 0.5, 0, 0);
        level.addFreshEntity(bat);
        test.assertTrue(ElevatorTravelRequest.request(cabin.pulley(), cabin.stop(travel)) == ElevatorTravelRequest.Result.ACCEPTED,
                "Trip must start: " + cabin.pulley().motion.status);
        test.succeedWhen(() -> {
            var motion = cabin.pulley().motion;
            test.assertTrue(!motion.active() && cabin.pulley().attachment().getY() == cabin.stop(travel),
                    "The cabin must park despite the mob; status=" + motion.status);
            test.assertTrue(bat.isAlive() && bat.getY() >= floorAtStop.getY() + 1 - 1e-6,
                    "The mob must be lifted onto the floor, y=" + bat.getY());
        });
    }

    /** A cow standing on the roof is carried up with it at top speed, like one on the floor. */
    @GameTest(template = "empty", batch = "animal_roof_up", timeoutTicks = 200)
    public static void cowOnRoofRidesUpAtTopSpeed(GameTestHelper test) {
        var level = test.getLevel();
        BlockPos attachment = test.absolutePos(new BlockPos(3, 5, 3));
        var pulley = enclosedCabin(test, attachment, TRAVEL);
        Cow cow = EntityType.COW.create(level);
        cow.moveTo(attachment.getX() + 0.5, attachment.getY() + 1, attachment.getZ() + 0.5, 0, 0);
        level.addFreshEntity(cow);
        float health = cow.getHealth();
        pulley.motion.speed = ElevatorEnergyUsage.MAX_SPEED;
        pulley.energy().receiveEnergy(100000, false);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        int stop = attachment.getY() + TRAVEL;
        pulley.motion.request(stop);
        test.onEachTick(() -> {
            if (!pulley.motion.active()) return;
            double roofTop = pulley.motion.y + 1;
            test.assertTrue(cow.getY() >= roofTop - 1e-6, "The cow must never sink into the rising roof, " + (cow.getY() - roofTop) + " from its top");
        });
        test.succeedWhen(() -> {
            test.assertTrue(!pulley.motion.active() && pulley.attachment() != null && pulley.attachment().getY() == stop, "Cabin must park; status=" + pulley.motion.status);
            test.assertTrue(cow.isAlive() && Math.abs(cow.getY() - (stop + 1)) < 0.05 && cow.getHealth() == health,
                    "The cow must arrive on the roof unharmed, y offset " + (cow.getY() - (stop + 1)) + " health " + cow.getHealth());
        });
    }

    /** A hovering mob under the roof of a descending cabin is pushed down ahead of it, not trapped inside it. */
    @GameTest(template = "empty", batch = "animal_hover_down", timeoutTicks = 200)
    public static void hoveringMobIsPushedByDescendingRoof(GameTestHelper test) {
        var level = test.getLevel();
        BlockPos attachment = test.absolutePos(new BlockPos(3, 8, 3));
        var pulley = enclosedCabin(test, attachment, -TRAVEL);
        var bat = EntityType.BAT.create(level);
        bat.setNoAi(true);
        bat.setNoGravity(true);
        // Flying, not hanging: a resting bat snaps itself to the block above every tick.
        bat.setResting(false);
        bat.moveTo(attachment.getX() + 0.5, attachment.getY() - 0.95, attachment.getZ() + 0.5, 0, 0);
        level.addFreshEntity(bat);
        float health = bat.getHealth();
        pulley.motion.speed = ElevatorEnergyUsage.MAX_SPEED;
        pulley.energy().receiveEnergy(100000, false);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        int stop = attachment.getY() - TRAVEL;
        pulley.motion.request(stop);
        test.onEachTick(() -> {
            if (!pulley.motion.active()) return;
            double roofBottom = pulley.motion.y;
            test.assertTrue(bat.getBoundingBox().maxY <= roofBottom + 1e-6, "The mob must stay below the descending roof, " + (bat.getBoundingBox().maxY - roofBottom) + " inside it");
        });
        test.succeedWhen(() -> {
            test.assertTrue(!pulley.motion.active() && pulley.attachment() != null && pulley.attachment().getY() == stop, "Cabin must park; status=" + pulley.motion.status);
            test.assertTrue(bat.isAlive() && bat.getHealth() == health, "The mob must not be hurt, health " + bat.getHealth());
        });
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
