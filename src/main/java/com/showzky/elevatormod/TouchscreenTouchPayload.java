package com.showzky.elevatormod;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the player pressed use while aiming at a touchscreen carried by a moving
 * cabin. Carries no touch coordinates; the server recomputes the hit from the player's view.
 * {@code cabinY} is the cabin height the client rendered and is only accepted near the server's.
 */
public record TouchscreenTouchPayload(BlockPos pulley, BlockPos offset, double cabinY) implements CustomPacketPayload {
    public static final Type<TouchscreenTouchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "touchscreen_touch"));
    public static final StreamCodec<ByteBuf, TouchscreenTouchPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TouchscreenTouchPayload::pulley,
            BlockPos.STREAM_CODEC, TouchscreenTouchPayload::offset,
            ByteBufCodecs.DOUBLE, TouchscreenTouchPayload::cabinY,
            TouchscreenTouchPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
