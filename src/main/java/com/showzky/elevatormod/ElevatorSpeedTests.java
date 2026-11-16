package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Speed setting persistence and speed-dependent energy use. */
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorSpeedTests {
    @GameTest(template = "empty", batch = "speed_energy", timeoutTicks = 100)
    public static void speedPersistsAndScalesEnergy(GameTestHelper test) {
        test.assertTrue(ElevatorEnergyUsage.perTick(ElevatorEnergyUsage.blocksPerTick(0.5)) == 14
                && ElevatorEnergyUsage.perTick(ElevatorEnergyUsage.blocksPerTick(2)) == 20
                && ElevatorEnergyUsage.perTick(ElevatorEnergyUsage.blocksPerTick(4)) == 28, "Usage must be 14/20/28 FE/t at 0.5/2/4 m/s");
        test.assertTrue(ElevatorEnergyUsage.sanitizeSpeed(0.1234) == 0.125 && ElevatorEnergyUsage.sanitizeSpeed(9) == ElevatorEnergyUsage.MAX_SPEED
                && ElevatorEnergyUsage.sanitizeSpeed(Double.NaN) == ElevatorEnergyUsage.DEFAULT_SPEED, "Speed must snap to 0.5 m/s steps within range");
        test.assertTrue(ElevatorEnergyUsage.MAX_SPEED == 1.0 && ElevatorEnergyUsage.perTick(ElevatorEnergyUsage.MAX_SPEED) == 92,
                "Top speed must match Create's 1 block per tick and draw 92 FE/t");
        test.assertTrue(ElevatorEnergyUsage.acceleration(ElevatorEnergyUsage.MAX_SPEED) > ElevatorEnergyUsage.acceleration(ElevatorEnergyUsage.MIN_SPEED),
                "Faster settings must accelerate harder");

        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = ElevatorTouchscreenTests.elevator(test, origin);
        pulley.motion.speed = ElevatorEnergyUsage.blocksPerTick(3.5);
        var saved = pulley.saveWithFullMetadata(test.getLevel().registryAccess());
        var copy = new ElevatorCabinMotion(pulley);
        copy.load(saved);
        test.assertTrue(Math.abs(copy.speed - 0.175) < 1e-9, "Speed must survive saving, was " + copy.speed);

        pulley.energy().receiveEnergy(100000, false);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        pulley.motion.request(origin.getY() + 3);
        int before = pulley.energy().getEnergyStored();
        test.runAfterDelay(5, () -> {
            int used = before - pulley.energy().getEnergyStored();
            // Five moving ticks at 3.5 m/s cost 26 FE each.
            test.assertTrue(used == 5 * 26, "Moving must draw 26 FE/t at 3.5 m/s, drew " + used + " in 5 ticks");
            test.succeed();
        });
    }

    /** Three floors up: about 26 ticks at top speed, about 42 at the default 2 m/s. */
    private static final int TRIP_CHECK_TICKS = 34;

    @GameTest(template = "empty", batch = "speed_fast", timeoutTicks = 100)
    public static void topSpeedShortensTrips(GameTestHelper test) {
        var pulley = startThreeFloorTrip(test, ElevatorEnergyUsage.MAX_SPEED);
        int stop = (int) pulley.motion.target;
        test.runAfterDelay(TRIP_CHECK_TICKS, () -> {
            test.assertTrue(pulley.attachment() != null && pulley.attachment().getY() == stop,
                    "Top speed must finish three floors within " + TRIP_CHECK_TICKS + " ticks; y=" + pulley.motion.y);
            test.succeed();
        });
    }

    @GameTest(template = "empty", batch = "speed_default", timeoutTicks = 100)
    public static void defaultSpeedIsSlowerThanTopSpeed(GameTestHelper test) {
        var pulley = startThreeFloorTrip(test, ElevatorEnergyUsage.DEFAULT_SPEED);
        test.runAfterDelay(TRIP_CHECK_TICKS, () -> {
            test.assertTrue(pulley.motion.active() && pulley.motion.y < pulley.motion.target,
                    "Default speed must still be travelling after " + TRIP_CHECK_TICKS + " ticks; y=" + pulley.motion.y);
            test.succeed();
        });
    }

    @GameTest(template = "empty", batch = "speed_client", timeoutTicks = 100)
    public static void clientSimulationMovesOneStepPerTick(GameTestHelper test) {
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = ElevatorTouchscreenTests.elevator(test, origin);
        var client = new ElevatorCabinMotion(pulley);
        client.parts.add(new ElevatorCabinMotion.Part(BlockPos.ZERO, ElevatorRegistries.ATTACHMENT.get().defaultBlockState(), new net.minecraft.nbt.CompoundTag()));
        client.speed = ElevatorEnergyUsage.MAX_SPEED;
        client.y = 0;
        client.target = -40;
        client.clientTick();
        for (int tick = 0; tick < 200 && client.y != client.target; tick++) {
            double before = client.y;
            client.clientTick();
            test.assertTrue(Math.abs(client.y - before) <= ElevatorEnergyUsage.MAX_SPEED + 1e-9,
                    "Client cabin must move at most one speed-step per tick, moved " + Math.abs(client.y - before));
        }
        test.assertTrue(client.y == client.target, "Client simulation must stop exactly at the landing, y=" + client.y);
        test.succeed();
    }

    private static PoweredPulleyBlockEntity startThreeFloorTrip(GameTestHelper test, double speed) {
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = ElevatorTouchscreenTests.elevator(test, origin);
        pulley.motion.speed = speed;
        pulley.energy().receiveEnergy(100000, false);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        pulley.motion.request(origin.getY() + 3);
        return pulley;
    }

    private ElevatorSpeedTests() {}
}
