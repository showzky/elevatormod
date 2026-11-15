package com.showzky.elevatormod;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Registers touchscreen packets. Client handling is delegated so servers never load client classes. */
public final class TouchscreenNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(TouchscreenTouchPayload.TYPE, TouchscreenTouchPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player)
                TouchscreenInteraction.touchCabin(player, payload.pulley(), payload.offset(), payload.cabinY());
        });
        registrar.playBidirectional(TouchscreenSettingsPayload.TYPE, TouchscreenSettingsPayload.STREAM_CODEC, (payload, context) -> {
            if (context.flow() == PacketFlow.CLIENTBOUND) TouchscreenClientHooks.openSettings(payload);
            else if (context.player() instanceof ServerPlayer player)
                TouchscreenInteraction.configure(player, payload.target(), TouchscreenMode.byId(payload.mode()));
        });
    }

    private TouchscreenNetwork() {}
}
