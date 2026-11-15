package com.showzky.elevatormod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Computes the visual block-light contribution from powered cabin fixtures. */
public final class ElevatorCabinLighting {
    public static List<BlockPos> poweredLights(List<ElevatorCabinMotion.Part> parts) {
        List<BlockPos> lights = new ArrayList<>();
        for (var part : parts) {
            if (part.state().is(ElevatorRegistries.LIGHT) && part.state().getValue(ElevatorLightBlock.LIT)) {
                lights.add(part.offset());
            }
        }
        return lights;
    }
    public static int blockLight(BlockPos position, List<BlockPos> lights, int ambient) {
        int result = ambient;
        for (BlockPos light : lights) {
            int distance = Math.abs(position.getX() - light.getX())
                    + Math.abs(position.getY() - light.getY())
                    + Math.abs(position.getZ() - light.getZ());
            result = Math.max(result, Math.max(0, 15 - distance));
        }
        return result;
    }
    private ElevatorCabinLighting() {}
}
