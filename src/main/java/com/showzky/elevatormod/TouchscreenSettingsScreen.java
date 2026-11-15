package com.showzky.elevatormod;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.neoforged.neoforge.network.PacketDistributor;

/** Compact mode selector opened with shift-right-click; the touchscreen itself is never a GUI. */
public final class TouchscreenSettingsScreen extends Screen {
    private static final int BUTTON_WIDTH = 150, BUTTON_HEIGHT = 20, SPACING = 24;
    private final TouchscreenTarget target;
    private TouchscreenMode selected;
    private final Map<TouchscreenMode, Button> buttons = new EnumMap<>(TouchscreenMode.class);

    public TouchscreenSettingsScreen(TouchscreenTarget target, TouchscreenMode mode) {
        super(Component.translatable("touchscreen.elevatormod.settings.title"));
        this.target = target;
        this.selected = mode;
    }

    @Override protected void init() {
        int x = (width - BUTTON_WIDTH) / 2, y = height / 2 - 40;
        for (TouchscreenMode mode : TouchscreenMode.values()) {
            buttons.put(mode, addRenderableWidget(Button.builder(Component.translatable(mode.translationKey()), button -> select(mode))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()));
            y += SPACING;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(x, y + 8, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        refreshButtons();
    }

    private void select(TouchscreenMode mode) {
        selected = mode;
        PacketDistributor.sendToServer(new TouchscreenSettingsPayload(target, mode.ordinal()));
        refreshButtons();
    }

    private void refreshButtons() {
        buttons.forEach((mode, button) -> button.active = mode != selected);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = height / 2 - 40;
        graphics.drawCenteredString(font, title, width / 2, top - 30, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable(selected.translationKey() + ".description"), width / 2, top - 16, 0xA0A0A0);
    }

    @Override public boolean isPauseScreen() { return false; }
}
