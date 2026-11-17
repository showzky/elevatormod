package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server to client: a moving cabin's position and travel state. Clients simulate the movement
 * themselves, so this small update is sent every few ticks (and immediately when the state
 * changes) instead of the pulley's full data, which includes every cabin block, every tick.
 * The full data is still sent when the cabin assembles and when it parks.
 */
public record ElevatorMotionPayload(BlockPos pulley, double y, double velocity, double target,
                                    boolean stalled, String status, double speed, boolean emergency) implements CustomPacketPayload {
    public static final Type<ElevatorMotionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "cabin_motion"));
    public static final StreamCodec<FriendlyByteBuf, ElevatorMotionPayload> STREAM_CODEC = StreamCodec.ofMember(
            (payload, buffer) -> {
                buffer.writeBlockPos(payload.pulley);
                buffer.writeDouble(payload.y);
                buffer.writeDouble(payload.velocity);
                buffer.writeDouble(payload.target);
                buffer.writeBoolean(payload.stalled);
                buffer.writeUtf(payload.status, 128);
                buffer.writeDouble(payload.speed);
                buffer.writeBoolean(payload.emergency);
            },
            buffer -> new ElevatorMotionPayload(buffer.readBlockPos(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readBoolean(), buffer.readUtf(128), buffer.readDouble(), buffer.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> {
            if (context.player().level().getBlockEntity(payload.pulley) instanceof PoweredPulleyBlockEntity pulley)
                pulley.motion.applyServerMotion(payload);
        });
    }
}
