package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Elevator Controller door settings: auto open, timed auto close, and closing before departure. */
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorDoorAutomationTests {
    private static final int TRAVEL = 3;

    @GameTest(template = "empty", batch = "door_settings")
    public static void settingsDefaultClampAndPersist(GameTestHelper test) {
        var defaults = ElevatorDoorSettings.load(new CompoundTag());
        test.assertTrue(defaults.autoOpen() && defaults.autoClose() && defaults.closeDelaySeconds() == 5, "Defaults must be on, on, 5 s");
        test.assertTrue(new ElevatorDoorSettings(true, true, 99).closeDelaySeconds() == 30
                && new ElevatorDoorSettings(true, true, 0).closeDelaySeconds() == 1, "The delay must be clamped to 1-30 s");
        var custom = new ElevatorDoorSettings(false, true, 12);
        var tag = new CompoundTag();
        custom.save(tag);
        test.assertTrue(ElevatorDoorSettings.load(tag).equals(custom), "Settings must survive saving");
        test.succeed();
    }

    @GameTest(template = "empty", batch = "door_depart", timeoutTicks = 300)
    public static void doorsCloseBeforeDepartureAndOpenOnArrival(GameTestHelper test) {
        var level = test.getLevel();
        var cabin = DoorCabin.build(test);
        var scan = ElevatorAssembly.scan(level, cabin.pulley.getBlockPos());
        test.assertTrue(scan.attachment() != null && !scan.components().contains(cabin.landingDoor(0)),
                "A landing door in front of the cabin door must not join the cabin: " + scan.error());
        ElevatorDoorBlock.setOpen(level, cabin.cabinDoor(0), true);
        ElevatorDoorBlock.setOpen(level, cabin.landingDoor(0), true);

        var result = ElevatorTravelRequest.request(cabin.pulley, cabin.stop(TRAVEL));
        test.assertTrue(result == ElevatorTravelRequest.Result.ACCEPTED, "Request must be accepted, was " + result + ": " + cabin.pulley.motion.status);
        test.assertTrue(!cabin.pulley.motion.active() && cabin.pulley.motion.status.equals("Closing doors"),
                "With doors open the cabin must close them before leaving: " + cabin.pulley.motion.status);
        test.assertFalse(open(level, cabin.cabinDoor(0)) || open(level, cabin.landingDoor(0)), "Cabin and landing doors must be told to close");

        test.succeedWhen(() -> {
            test.assertTrue(!cabin.pulley.motion.active() && cabin.pulley.attachment().getY() == cabin.stop(TRAVEL),
                    "Cabin must arrive and park; status=" + cabin.pulley.motion.status);
            test.assertTrue(open(level, cabin.cabinDoor(TRAVEL)) && open(level, cabin.landingDoor(TRAVEL)),
                    "Auto Open must open the cabin door and the destination's landing door");
            test.assertTrue(ElevatorDoorBlock.isClosed(level.getBlockState(cabin.landingDoor(0))), "The departure landing's door must stay shut");
        });
    }

    @GameTest(template = "empty", batch = "door_auto_close", timeoutTicks = 200)
    public static void autoCloseShutsDoorsAfterDelay(GameTestHelper test) {
        var level = test.getLevel();
        var cabin = DoorCabin.build(test);
        cabin.pulley.motion.doorSettings = ElevatorDoorSettings.DEFAULT.withCloseDelay(1);
        cabin.pulley.motion.openParkedDoors();
        test.assertTrue(open(level, cabin.cabinDoor(0)) && open(level, cabin.landingDoor(0)), "Doors must open");
        test.runAfterDelay(12, () -> test.assertTrue(open(level, cabin.cabinDoor(0)), "Doors must not close before the delay"));
        test.succeedWhen(() -> test.assertTrue(ElevatorDoorBlock.isClosed(level.getBlockState(cabin.cabinDoor(0)))
                && ElevatorDoorBlock.isClosed(level.getBlockState(cabin.landingDoor(0))), "Doors must close after the delay"));
    }

    @GameTest(template = "empty", batch = "door_stay_open", timeoutTicks = 200)
    public static void withoutAutoCloseDoorsStayOpen(GameTestHelper test) {
        var level = test.getLevel();
        var cabin = DoorCabin.build(test);
        cabin.pulley.motion.doorSettings = ElevatorDoorSettings.DEFAULT.withAutoClose(false).withCloseDelay(1);
        cabin.pulley.motion.openParkedDoors();
        test.runAfterDelay(80, () -> {
            test.assertTrue(open(level, cabin.cabinDoor(0)) && open(level, cabin.landingDoor(0)), "Without Auto Close the doors must stay open");
            test.succeed();
        });
    }

    @GameTest(template = "empty", batch = "door_manual", timeoutTicks = 300)
    public static void withoutAutoOpenDoorsStayShutOnArrival(GameTestHelper test) {
        var level = test.getLevel();
        var cabin = DoorCabin.build(test);
        cabin.pulley.motion.doorSettings = ElevatorDoorSettings.DEFAULT.withAutoOpen(false);
        test.assertTrue(ElevatorTravelRequest.request(cabin.pulley, cabin.stop(TRAVEL)) == ElevatorTravelRequest.Result.ACCEPTED,
                "Request must be accepted: " + cabin.pulley.motion.status);
        test.assertTrue(cabin.pulley.motion.active(), "With doors shut the cabin must leave at once");
        test.succeedWhen(() -> {
            test.assertTrue(!cabin.pulley.motion.active() && cabin.pulley.attachment().getY() == cabin.stop(TRAVEL), "Cabin must arrive and park");
            test.assertFalse(open(level, cabin.cabinDoor(TRAVEL)) || open(level, cabin.landingDoor(TRAVEL)), "Without Auto Open the doors must stay shut");
        });
    }

    @GameTest(template = "empty", batch = "door_doorway", timeoutTicks = 300)
    public static void entityInDoorwayHoldsDeparture(GameTestHelper test) {
        var level = test.getLevel();
        var cabin = DoorCabin.build(test);
        cabin.pulley.motion.doorSettings = ElevatorDoorSettings.DEFAULT.withAutoClose(false);
        cabin.pulley.motion.openParkedDoors();
        test.runAfterDelay(30, () -> {
            BlockPos door = cabin.cabinDoor(0);
            var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, door.getX() + 0.5, door.getY(), door.getZ() + 0.5);
            level.addFreshEntity(stand);
            ElevatorTravelRequest.request(cabin.pulley, cabin.stop(TRAVEL));
            test.runAfterDelay(40, () -> {
                test.assertTrue(!cabin.pulley.motion.active() && cabin.pulley.motion.status.equals("Closing doors"),
                        "The cabin must wait while someone stands in the doorway: " + cabin.pulley.motion.status);
                stand.discard();
                test.succeedWhen(() -> test.assertTrue(cabin.pulley.attachment().getY() == cabin.stop(TRAVEL) && !cabin.pulley.motion.active(),
                        "Once the doorway is clear the cabin must leave and arrive"));
            });
        });
    }

    private static boolean open(Level level, BlockPos door) {
        var state = level.getBlockState(door);
        return state.is(ElevatorRegistries.DOOR) && state.getValue(ElevatorDoorBlock.OPEN);
    }

    /**
     * A small cabin: a three-block floor, a pillar up to the rope attachment, a two-by-two cabin
     * door facing north beside the pillar, and a matching landing door directly behind it at the
     * start and at the destination three floors up.
     */
    private record DoorCabin(PoweredPulleyBlockEntity pulley, BlockPos base) {
        static DoorCabin build(GameTestHelper test) {
            var level = test.getLevel();
            // Low enough that the pulley stays below the test area's barrier ceiling.
            BlockPos base = test.absolutePos(new BlockPos(2, 1, 4));
            var block = ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState();
            for (int x = 0; x <= 2; x++) level.setBlock(base.east(x), block, 3);
            level.setBlock(base.above(1), block, 3);
            level.setBlock(base.above(2), block, 3);
            level.setBlock(base.above(3), ElevatorRegistries.ATTACHMENT.get().defaultBlockState(), 3);
            level.setBlock(base.north(), ElevatorRegistries.CONTROLLER.get().defaultBlockState(), 3);
            placeDoor(level, base.offset(1, 1, 0));
            placeDoor(level, base.offset(1, 1, 1));
            placeDoor(level, base.offset(1, 1 + TRAVEL, 1));
            var connector = ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState();
            level.setBlock(base.offset(2, 0, -1), connector.setValue(FacingElevatorBlock.FACING, Direction.NORTH), 3);
            level.setBlock(base.offset(2, 0, -2), connector.setValue(FacingElevatorBlock.FACING, Direction.SOUTH), 3);
            level.setBlock(base.offset(2, TRAVEL, -2), connector.setValue(FacingElevatorBlock.FACING, Direction.SOUTH), 3);
            BlockPos pulleyPos = base.above(3 + TRAVEL + 5);
            level.setBlock(pulleyPos, ElevatorRegistries.PULLEY.get().defaultBlockState(), 3);
            var pulley = (PoweredPulleyBlockEntity) level.getBlockEntity(pulleyPos);
            var scan = ElevatorAssembly.scan(level, pulleyPos);
            test.assertTrue(scan.attachment() != null, "Test cabin must be valid: " + scan.error()
                    + (scan.obstruction() == null ? "" : " by " + level.getBlockState(scan.obstruction()) + " at " + scan.obstruction().subtract(base)));
            pulley.connect(scan);
            pulley.energy().receiveEnergy(100000, false);
            pulley.motion.speed = ElevatorEnergyUsage.MAX_SPEED;
            return new DoorCabin(pulley, base);
        }

        /** Places all four cells of a north-facing door; setBlock does not run placement logic. */
        private static void placeDoor(Level level, BlockPos master) {
            var door = ElevatorRegistries.DOOR.get().defaultBlockState().setValue(ElevatorDoorBlock.FACING, Direction.NORTH);
            for (var part : ElevatorDoorBlock.Part.values()) {
                BlockPos cell = master.relative(Direction.NORTH.getClockWise(), part == ElevatorDoorBlock.Part.LOWER_RIGHT || part == ElevatorDoorBlock.Part.UPPER_RIGHT ? 1 : 0)
                        .above(part == ElevatorDoorBlock.Part.UPPER_LEFT || part == ElevatorDoorBlock.Part.UPPER_RIGHT ? 1 : 0);
                level.setBlock(cell, door.setValue(ElevatorDoorBlock.PART, part), 3);
            }
        }

        int stop(int floorsUp) { return base.getY() + 3 + floorsUp; }
        BlockPos cabinDoor(int raised) { return base.offset(1, 1 + raised, 0); }
        BlockPos landingDoor(int raised) { return base.offset(1, 1 + raised, 1); }
    }

    private ElevatorDoorAutomationTests() {}
}
