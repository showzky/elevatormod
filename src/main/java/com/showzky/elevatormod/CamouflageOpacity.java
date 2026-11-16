package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * How an Elevator Block's camouflage lets sight and light through. It is stored as a block-state
 * property so vanilla caches occlusion and light per state: the server light engine runs off the
 * main thread, where block entities (and so the camouflage itself) cannot be read.
 */
public enum CamouflageOpacity implements StringRepresentable {
    /** No camouflage or an opaque one: hides neighbouring faces and blocks light. */
    SOLID("solid"),
    /** See-through and lets light pass, like glass and stained glass. */
    CLEAR("clear"),
    /** See-through but blocks light, like tinted glass. */
    TINTED("tinted");

    private final String name;
    CamouflageOpacity(String name) { this.name = name; }
    @Override public String getSerializedName() { return name; }

    public static CamouflageOpacity of(@Nullable BlockState camouflage) {
        if (camouflage == null || camouflage.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return SOLID;
        return camouflage.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) > 0 ? TINTED : CLEAR;
    }
}
