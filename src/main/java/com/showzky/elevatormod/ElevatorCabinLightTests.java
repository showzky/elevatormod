package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A rider standing in a two-block-high cabin under a ceiling light must travel and park normally. */
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorCabinLightTests {
    @GameTest(template = "empty", batch = "cabin_light_rider", timeoutTicks = 400)
    public static void riderUnderCeilingLightTravelsAndParks(GameTestHelper test) { run(test, true); }

    @GameTest(template = "empty", batch = "cabin_rider", timeoutTicks = 400)
    public static void riderWithoutLightTravelsAndParks(GameTestHelper test) { run(test, false); }

    private static void run(GameTestHelper test, boolean light) {
        var level = test.getLevel();
        BlockPos origin = test.absolutePos(new BlockPos(3, 6, 3));
        BlockPos pulleyPos = origin.above(7);
        var block = ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState();
        level.setBlock(pulleyPos, ElevatorRegistries.PULLEY.get().defaultBlockState(), 3);
        level.setBlock(origin, ElevatorRegistries.ATTACHMENT.get().defaultBlockState(), 3);
        level.setBlock(origin.below(), block, 3);                      // ceiling
        for (int down = 1; down <= 4; down++) level.setBlock(origin.below(down).south(), block, 3); // back wall
        level.setBlock(origin.below(4), block, 3);                     // floor; interior is below(2) and below(3)
        if (light) level.setBlock(origin.below(2), ElevatorRegistries.LIGHT.get().defaultBlockState(), 3);
        level.setBlock(origin.west(), ElevatorRegistries.CONTROLLER.get().defaultBlockState(), 3);
        level.setBlock(origin.east(), ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING, Direction.EAST), 3);
        level.setBlock(origin.east(2), ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING, Direction.WEST), 3);
        level.setBlock(origin.east(2).above(3), ElevatorRegistries.FLOOR_CONNECTOR.get().defaultBlockState().setValue(FacingElevatorBlock.FACING, Direction.WEST), 3);
        var pulley = (PoweredPulleyBlockEntity) level.getBlockEntity(pulleyPos);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        test.assertTrue(pulley.motion.disassemble(), "Cabin must park: " + pulley.motion.status);
        pulley.energy().receiveEnergy(100000, false);

        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos floor = origin.below(4);
        player.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 0);
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble with a rider: " + pulley.motion.status);
        pulley.motion.request(origin.getY() + 3);
        double startY = player.getY();
        test.succeedWhen(() -> test.assertTrue(!pulley.motion.active() && pulley.attachment().getY() == origin.getY() + 3
                        && player.getY() == startY, // players are carried by their own client, never repositioned by the server
                "Cabin must arrive and park; status=" + pulley.motion.status + " y=" + pulley.motion.y
                        + " riderFeet=" + (player.getY() - (floor.getY() + 1)) + " riderHead=" + (player.getBoundingBox().maxY - (floor.getY() + 1))));
    }

    private ElevatorCabinLightTests() {}
}
