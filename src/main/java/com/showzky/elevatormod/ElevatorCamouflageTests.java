package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Glass camouflage: which blocks are accepted, and how see-through camouflage treats sight and light. */
@GameTestHolder(ElevatorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorCamouflageTests {
    @GameTest(template = "empty", batch = "camouflage_rules")
    public static void glassFamilyIsAllowed(GameTestHelper test) {
        var level = test.getLevel();
        BlockPos pos = test.absolutePos(new BlockPos(1, 2, 1));
        for (var allowed : new BlockState[] {Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
                Blocks.RED_STAINED_GLASS.defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState()})
            test.assertTrue(CamouflageRules.isAllowed(level, pos, allowed), allowed + " must be allowed as camouflage");
        for (var rejected : new BlockState[] {Blocks.OAK_LEAVES.defaultBlockState(), Blocks.ICE.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState()})
            test.assertFalse(CamouflageRules.isAllowed(level, pos, rejected), rejected + " must be rejected as camouflage");
        test.succeed();
    }

    @GameTest(template = "empty", batch = "camouflage_opacity")
    public static void camouflageSetsOpacity(GameTestHelper test) {
        var level = test.getLevel();
        BlockPos pos = test.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(), 3);
        var block = (ElevatorBlockEntity) level.getBlockEntity(pos);

        block.setCamouflage(Blocks.GLASS.defaultBlockState());
        var state = level.getBlockState(pos);
        test.assertTrue(state.getValue(ElevatorBlock.OPACITY) == CamouflageOpacity.CLEAR, "Glass camouflage must be clear");
        test.assertTrue(!state.isSolidRender(level, pos) && state.getOcclusionShape(level, pos).isEmpty(), "Clear camouflage must not hide neighbouring faces");
        test.assertTrue(state.getLightBlock(level, pos) == 0 && state.propagatesSkylightDown(level, pos), "Clear camouflage must let light through");
        test.assertTrue(level.getBlockEntity(pos) == block, "Changing opacity must keep the block entity and its camouflage");

        block.setCamouflage(Blocks.TINTED_GLASS.defaultBlockState());
        state = level.getBlockState(pos);
        test.assertTrue(state.getValue(ElevatorBlock.OPACITY) == CamouflageOpacity.TINTED && state.getLightBlock(level, pos) == 15,
                "Tinted glass camouflage must be see-through but block light");

        block.setCamouflage(null);
        state = level.getBlockState(pos);
        test.assertTrue(state.getValue(ElevatorBlock.OPACITY) == CamouflageOpacity.SOLID && state.isSolidRender(level, pos)
                && state.getLightBlock(level, pos) == 15, "Removing camouflage must restore an opaque block");
        test.succeed();
    }

    @GameTest(template = "empty", batch = "camouflage_light", timeoutTicks = 60)
    public static void lightPassesThroughGlassCamouflage(GameTestHelper test) {
        var level = test.getLevel();
        BlockPos lamp = test.absolutePos(new BlockPos(1, 2, 1));
        BlockPos glass = lamp.east(), stone = lamp.west();
        level.setBlock(lamp, Blocks.GLOWSTONE.defaultBlockState(), 3);
        level.setBlock(glass, ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(stone, ElevatorRegistries.ELEVATOR_BLOCK.get().defaultBlockState(), 3);
        ((ElevatorBlockEntity) level.getBlockEntity(glass)).setCamouflage(Blocks.GLASS.defaultBlockState());
        ((ElevatorBlockEntity) level.getBlockEntity(stone)).setCamouflage(Blocks.STONE.defaultBlockState());
        test.succeedWhen(() -> {
            test.assertTrue(level.getBrightness(LightLayer.BLOCK, glass) == 14, "Glass camouflage must be lit by the lamp, was " + level.getBrightness(LightLayer.BLOCK, glass));
            test.assertTrue(level.getBrightness(LightLayer.BLOCK, stone) == 0, "Stone camouflage must block light, was " + level.getBrightness(LightLayer.BLOCK, stone));
        });
    }

    @GameTest(template = "empty", batch = "camouflage_cabin", timeoutTicks = 60)
    public static void glassCamouflageSurvivesTravel(GameTestHelper test) {
        var level = test.getLevel();
        BlockPos origin = test.absolutePos(new BlockPos(3, 2, 3));
        var pulley = ElevatorTouchscreenTests.elevator(test, origin);
        BlockPos floor = origin.below();
        ((ElevatorBlockEntity) level.getBlockEntity(floor)).setCamouflage(Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
        test.assertTrue(pulley.motion.assemble(), "Cabin must assemble: " + pulley.motion.status);
        test.assertTrue(pulley.motion.disassemble(), "Cabin must park: " + pulley.motion.status);
        var state = level.getBlockState(floor);
        test.assertTrue(state.getValue(ElevatorBlock.OPACITY) == CamouflageOpacity.CLEAR, "Parked cabin must keep clear opacity");
        test.assertTrue(level.getBlockEntity(floor) instanceof ElevatorBlockEntity be && be.camouflage() != null
                && be.camouflage().is(Blocks.LIGHT_BLUE_STAINED_GLASS), "Parked cabin must keep its glass camouflage");
        test.succeed();
    }

    private ElevatorCamouflageTests() {}
}
