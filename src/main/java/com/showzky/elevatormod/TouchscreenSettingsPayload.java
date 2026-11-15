package com.showzky.elevatormod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open the settings screen for a touchscreen with its current mode.
 * Client to server: apply the selected mode (validated by {@link TouchscreenInteraction#configure}).
 */
public record TouchscreenSettingsPayload(TouchscreenTarget target, int mode) implements CustomPacketPayload {
    public static final Type<TouchscreenSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "touchscreen_settings"));
    public static final StreamCodec<ByteBuf, TouchscreenSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            TouchscreenTarget.STREAM_CODEC, TouchscreenSettingsPayload::target,
            ByteBufCodecs.VAR_INT, TouchscreenSettingsPayload::mode,
            TouchscreenSettingsPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
