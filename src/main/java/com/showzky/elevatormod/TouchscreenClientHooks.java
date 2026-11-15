package com.showzky.elevatormod;

import net.minecraft.client.Minecraft;

/** Client-only entry points called from packet handlers. */
public final class TouchscreenClientHooks {
    public static void openSettings(TouchscreenSettingsPayload payload) {
        Minecraft.getInstance().setScreen(new TouchscreenSettingsScreen(payload.target(), TouchscreenMode.byId(payload.mode())));
    }

    private TouchscreenClientHooks() {}
}
