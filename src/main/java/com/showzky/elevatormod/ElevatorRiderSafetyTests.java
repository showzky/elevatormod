package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Riders never build up fall distance on a cabin, and the cabin's bounds cover all of its parts. */
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorRiderSafetyTests {
    /** Movement of one airborne packet at 18 m/s. */
    private static final double FAST_DESCENT_STEP = -0.9;
    private static final int AIRBORNE_PACKETS = 20;

    @GameTest(template = "empty", batch = "rider_fast_descent")
    @SuppressWarnings("removal")
    public static void fastDescentCausesNoFallDamage(GameTestHelper test) {
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = ElevatorTouchscreenTests.elevator(test, origin);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        var player = rider(test, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5);
        float health = player.getHealth();
        descend(player);
        test.assertTrue(player.fallDistance <= -FAST_DESCENT_STEP + 1e-6, "Riding must not build up fall distance, was " + player.fallDistance);
        player.doCheckFallDamage(0, 0, 0, true);
        test.assertTrue(player.getHealth() == health, "Riding a fast descent must not cause fall damage");
        test.succeed();
    }

    @GameTest(template = "empty", batch = "rider_parked_grace")
    @SuppressWarnings("removal")
    public static void arrivalGraceCausesNoFallDamage(GameTestHelper test) {
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = ElevatorTouchscreenTests.elevator(test, origin);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        test.assertTrue(pulley.motion.disassemble(), "Cabin must park: " + pulley.motion.status);
        var player = rider(test, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5);
        descend(player);
        test.assertTrue(player.fallDistance <= -FAST_DESCENT_STEP + 1e-6, "A just-parked cabin must still protect riders, was " + player.fallDistance);
        test.succeed();
    }

    @GameTest(template = "empty", batch = "rider_real_fall")
    @SuppressWarnings("removal")
    public static void fallsAwayFromCabinStillCount(GameTestHelper test) {
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = ElevatorTouchscreenTests.elevator(test, origin);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        var player = rider(test, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 6.5);
        descend(player);
        test.assertTrue(player.fallDistance > 10, "Falls away from the cabin must still count, was " + player.fallDistance);
        test.succeed();
    }

    @GameTest(template = "empty", batch = "rider_park_feet")
    public static void lateRiderPositionDoesNotBlockParking(GameTestHelper test) {
        var level = test.getLevel();
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = ElevatorTouchscreenTests.elevator(test, origin);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        // An armour stand inside the top of the roof part still blocks parking.
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, origin.getX() + 0.5, origin.getY() + 0.8, origin.getZ() + 0.5);
        level.addFreshEntity(stand);
        test.assertFalse(pulley.motion.disassemble(), "A non-player inside a part must block parking");
        test.assertTrue(pulley.motion.status.contains("blocked by entity"), "Blocked parking must say why: " + pulley.motion.status);
        stand.discard();
        // A rider reported slightly inside the floor (a moment behind the cabin) must not.
        rider(test, origin.getX() + 0.5, origin.getY() + 0.8, origin.getZ() + 0.5);
        test.assertTrue(pulley.motion.disassemble(), "A rider's late position must not block parking: " + pulley.motion.status);
        test.succeed();
    }

    @GameTest(template = "empty", batch = "rider_bounds")
    public static void boundsCoverEveryPart(GameTestHelper test) {
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = ElevatorTouchscreenTests.elevator(test, origin);
        test.assertTrue(pulley.motion.cabinBounds(origin.getY()) == null, "A parked cabin has no moving bounds");
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        var motion = pulley.motion;
        double height = origin.getY() + 2.5;
        AABB bounds = motion.cabinBounds(height);
        for (var part : motion.parts) {
            var box = new AABB(motion.origin.getX() + part.offset().getX(), height + part.offset().getY(), motion.origin.getZ() + part.offset().getZ(),
                    motion.origin.getX() + part.offset().getX() + 1, height + part.offset().getY() + 1, motion.origin.getZ() + part.offset().getZ() + 1);
            test.assertTrue(bounds.contains(box.getCenter()) && bounds.minY <= box.minY && bounds.maxY >= box.maxY, "Bounds must cover part " + part.offset());
        }
        AABB slow = motion.riderArea(0);
        motion.velocity = ElevatorEnergyUsage.MAX_SPEED;
        AABB fast = motion.riderArea(0);
        test.assertTrue(fast.maxY - fast.minY > slow.maxY - slow.minY, "The rider area must grow with speed");
        test.succeed();
    }

    @SuppressWarnings("removal")
    private static ServerPlayer rider(GameTestHelper test, double x, double y, double z) {
        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(x, y, z, 0, 0);
        player.resetFallDistance();
        return player;
    }

    private static void descend(ServerPlayer player) {
        for (int packet = 0; packet < AIRBORNE_PACKETS; packet++) player.doCheckFallDamage(0, FAST_DESCENT_STEP, 0, false);
    }

    private ElevatorRiderSafetyTests() {}
}
