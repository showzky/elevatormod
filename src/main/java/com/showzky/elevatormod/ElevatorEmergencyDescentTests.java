package com.showzky.elevatormod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Losing power never leaves riders stuck: the cabin lowers itself to the floor below and opens up. */
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorEmergencyDescentTests {
    private static final int TRAVEL = ElevatorDoorAutomationTests.TRAVEL;

    @GameTest(template = "empty", batch = "emergency_descent", timeoutTicks = 400)
    public static void powerLossLowersCabinToFloorBelow(GameTestHelper test) {
        var level = test.getLevel();
        var cabin = ElevatorDoorAutomationTests.DoorCabin.build(test);
        // Auto Open off: the doors must open anyway after an emergency, to let riders out.
        cabin.pulley().motion.doorSettings = ElevatorDoorSettings.DEFAULT.withAutoOpen(false);
        test.assertTrue(ElevatorTravelRequest.request(cabin.pulley(), cabin.stop(TRAVEL)) == ElevatorTravelRequest.Result.ACCEPTED,
                "Trip must start: " + cabin.pulley().motion.status);
        test.runAfterDelay(8, () -> {
            var motion = cabin.pulley().motion;
            test.assertTrue(motion.active() && motion.y > cabin.stop(0), "Cabin must be on its way up when the power fails");
            cabin.pulley().consume(Integer.MAX_VALUE);
            test.succeedWhen(() -> {
                if (motion.active()) {
                    test.assertTrue(motion.emergency && motion.status.equals(ElevatorCabinMotion.EMERGENCY_STATUS), "Power loss must start an emergency descent: " + motion.status);
                    test.assertTrue(Math.abs(motion.velocity) <= ElevatorCabinMotion.EMERGENCY_SPEED + 1e-9, "The descent must be slow, velocity " + motion.velocity);
                }
                test.assertTrue(!motion.active() && cabin.pulley().attachment().getY() == cabin.stop(0),
                        "The cabin must park at the floor below; status=" + motion.status + " y=" + motion.y);
                var door = level.getBlockState(cabin.cabinDoor(0));
                test.assertTrue(door.is(ElevatorRegistries.DOOR) && door.getValue(ElevatorDoorBlock.OPEN), "Doors must open to let riders out");
                test.assertTrue(!motion.emergency && cabin.pulley().energy().getEnergyStored() == 0, "The descent must use no energy and then end");
            });
        });
    }

    @GameTest(template = "empty", batch = "emergency_no_power", timeoutTicks = 300)
    public static void parkedCabinWithoutPowerWaitsInsteadOfLeaving(GameTestHelper test) {
        var cabin = ElevatorDoorAutomationTests.DoorCabin.build(test);
        cabin.pulley().consume(Integer.MAX_VALUE);
        var result = ElevatorTravelRequest.request(cabin.pulley(), cabin.stop(TRAVEL));
        test.assertTrue(result == ElevatorTravelRequest.Result.WAITING_POWER, "A request without power must wait, was " + result);
        test.assertTrue(!cabin.pulley().motion.active() && cabin.pulley().motion.hasCall(cabin.stop(TRAVEL)),
                "The cabin must stay parked with the call queued");
        test.runAfterDelay(20, () -> {
            test.assertFalse(cabin.pulley().motion.active(), "Without power the cabin must not set off");
            cabin.pulley().energy().receiveEnergy(100000, false);
            test.succeedWhen(() -> test.assertTrue(!cabin.pulley().motion.active() && cabin.pulley().attachment().getY() == cabin.stop(TRAVEL),
                    "Once powered, the queued trip must run; status=" + cabin.pulley().motion.status));
        });
    }

    private ElevatorEmergencyDescentTests() {}
}
