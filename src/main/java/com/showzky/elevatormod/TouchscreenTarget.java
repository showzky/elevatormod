package com.showzky.elevatormod;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies a touchscreen: either a stationary master block, or a screen carried by a moving
 * cabin (the pulley position plus the master part's offset from the cabin origin).
 */
public record TouchscreenTarget(@Nullable BlockPos pulley, BlockPos pos) {
    public static final StreamCodec<ByteBuf, TouchscreenTarget> STREAM_CODEC = StreamCodec.of(
            (buffer, target) -> {
                buffer.writeBoolean(target.pulley != null);
                if (target.pulley != null) BlockPos.STREAM_CODEC.encode(buffer, target.pulley);
                BlockPos.STREAM_CODEC.encode(buffer, target.pos);
            },
            buffer -> {
                BlockPos pulley = buffer.readBoolean() ? BlockPos.STREAM_CODEC.decode(buffer) : null;
                return new TouchscreenTarget(pulley, BlockPos.STREAM_CODEC.decode(buffer));
            });

    public static TouchscreenTarget stationary(BlockPos master) { return new TouchscreenTarget(null, master.immutable()); }
    public static TouchscreenTarget cabin(BlockPos pulley, BlockPos offset) { return new TouchscreenTarget(pulley.immutable(), offset.immutable()); }
    public boolean inCabin() { return pulley != null; }
}
