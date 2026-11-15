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
                && ElevatorEnergyUsage.sanitizeSpeed(Double.NaN) == ElevatorEnergyUsage.DEFAULT_SPEED, "Speed must snap to 0.1 m/s steps within range");

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

    private ElevatorSpeedTests() {}
}
