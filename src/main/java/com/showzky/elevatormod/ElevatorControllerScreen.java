package com.showzky.elevatormod;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Elevator Controller's settings panel: system status, travel speed (shared with the Powered
 * Pulley) and door behaviour. Sound settings are reserved for a later version.
 */
public final class ElevatorControllerScreen extends AbstractContainerScreen<ElevatorControlMenu> {
    private static final int PANEL = 0xffc6c6c6, BORDER = 0xff555555, DIVIDER = 0xff8b8b8b;
    private static final int HEADING = 0xff333333, TEXT = 0xff3a3a3a;
    private static final int STATUS_BACKGROUND = 0xff2e3831, STATUS_TEXT = 0xffa8d39d, STATUS_FAULT = 0xffe08a7a;
    private static final int LOCKED_BACKGROUND = 0xffa9a9a9, LOCKED_TEXT = 0xff6b6b6b;
    private static final int LEFT = 12, RIGHT = 204, TOGGLE_WIDTH = 40;

    private ElevatorSpeedSlider speedSlider;
    private Button autoOpen, autoClose;
    private CloseDelaySlider closeDelay;

    public ElevatorControllerScreen(ElevatorControlMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 216;
        imageHeight = 258;
    }

    private void send(String action, CompoundTag data) {
        PacketDistributor.sendToServer(new ElevatorControlPayload(menu.containerId, action, data));
    }

    private void sendValue(String action, boolean value) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Value", value);
        send(action, tag);
    }

    @Override protected void init() {
        super.init();
        speedSlider = addRenderableWidget(new ElevatorSpeedSlider(leftPos + LEFT, topPos + 98, RIGHT - LEFT, 20, speed -> {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("Speed", speed);
            send("speed", tag);
        }));
        autoOpen = addRenderableWidget(Button.builder(Component.literal("ON"), b -> sendValue("autoOpen", !menu.state.getBoolean("AutoOpen")))
                .bounds(leftPos + RIGHT - TOGGLE_WIDTH, topPos + 142, TOGGLE_WIDTH, 16).build());
        autoClose = addRenderableWidget(Button.builder(Component.literal("ON"), b -> sendValue("autoClose", !menu.state.getBoolean("AutoClose")))
                .bounds(leftPos + RIGHT - TOGGLE_WIDTH, topPos + 162, TOGGLE_WIDTH, 16).build());
        closeDelay = addRenderableWidget(new CloseDelaySlider(leftPos + LEFT, topPos + 184, RIGHT - LEFT, 20));
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose())
                .bounds(leftPos + imageWidth - 24, topPos + 6, 18, 18).build());
    }

    @Override protected void containerTick() {
        if (menu.state.contains("Speed")) speedSlider.showServerSpeed(menu.state.getDouble("Speed"));
        autoOpen.setMessage(Component.literal(menu.state.getBoolean("AutoOpen") ? "ON" : "OFF"));
        autoClose.setMessage(Component.literal(menu.state.getBoolean("AutoClose") ? "ON" : "OFF"));
        boolean linked = menu.state.contains("AutoOpen");
        autoOpen.active = autoClose.active = closeDelay.active = speedSlider.active = linked;
        if (menu.state.contains("CloseDelay")) closeDelay.showServerDelay(menu.state.getInt("CloseDelay"));
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        g.fill(x + LEFT, y + 26, x + RIGHT, y + 27, DIVIDER);

        // System status
        g.fill(x + LEFT, y + 32, x + RIGHT, y + 80, STATUS_BACKGROUND);
        boolean connected = menu.state.getBoolean("Connected");
        String status = menu.state.getString("Status");
        text(g, "SYSTEM STATUS", LEFT + 6, 37, STATUS_TEXT);
        text(g, connected ? "Pulley connected" : "Pulley not connected", LEFT + 6, 49, connected ? STATUS_TEXT : STATUS_FAULT);
        int floors = menu.state.getInt("FloorCount");
        text(g, floors == 1 ? "1 floor detected" : floors + " floors detected", LEFT + 6, 59, STATUS_TEXT);
        text(g, status.isEmpty() ? "-" : status, LEFT + 6, 69, isFault(status) ? STATUS_FAULT : STATUS_TEXT);

        text(g, "Movement Settings", LEFT, 86, HEADING);
        g.fill(x + LEFT, y + 124, x + RIGHT, y + 125, DIVIDER);

        text(g, "Door Settings", LEFT, 130, HEADING);
        text(g, "Auto Open", LEFT, 146, TEXT);
        text(g, "Auto Close", LEFT, 166, TEXT);
        g.fill(x + LEFT, y + 212, x + RIGHT, y + 213, DIVIDER);

        // Reserved for a later version.
        g.fill(x + LEFT, y + 218, x + RIGHT, y + 248, LOCKED_BACKGROUND);
        text(g, "Sound Settings", LEFT + 6, 223, LOCKED_TEXT);
        text(g, "Coming soon...", LEFT + 6, 236, LOCKED_TEXT);
        drawLock(g, x + RIGHT - 14, y + 223);
    }

    private static boolean isFault(String status) {
        String text = status.toLowerCase(Locale.ROOT);
        return text.startsWith("waiting") || text.contains("blocked") || text.contains("obstruct")
                || text.contains("unavailable") || text.contains("failed");
    }

    /** A small padlock drawn from rectangles, since the default font has no lock glyph. */
    private static void drawLock(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y, x + 6, y + 1, LOCKED_TEXT);
        g.fill(x + 1, y, x + 2, y + 4, LOCKED_TEXT);
        g.fill(x + 5, y, x + 6, y + 4, LOCKED_TEXT);
        g.fill(x, y + 4, x + 7, y + 9, LOCKED_TEXT);
        g.fill(x + 3, y + 5, x + 4, y + 7, LOCKED_BACKGROUND);
    }

    private void text(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, font.plainSubstrByWidth(text, RIGHT - x - 4), leftPos + x, topPos + y, color, false);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, LEFT, 10, HEADING, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Auto Close Delay in whole seconds; sends only when the value changes. */
    private final class CloseDelaySlider extends AbstractSliderButton {
        private static final int MIN = ElevatorDoorSettings.MIN_DELAY, MAX = ElevatorDoorSettings.MAX_DELAY;
        private int sent = -1, shownServer = -1;
        private boolean dragging;

        CloseDelaySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), fraction(ElevatorDoorSettings.DEFAULT_DELAY));
            updateMessage();
        }

        private static double fraction(int seconds) { return (seconds - MIN) / (double) (MAX - MIN); }
        private int seconds() { return (int) Math.round(MIN + value * (MAX - MIN)); }

        @Override protected void updateMessage() {
            int seconds = seconds();
            setMessage(Component.literal("Auto Close Delay: " + seconds + (seconds == 1 ? " second" : " seconds")));
        }

        @Override protected void applyValue() {
            int seconds = seconds();
            if (seconds == sent) return;
            sent = seconds;
            CompoundTag tag = new CompoundTag();
            tag.putInt("Value", seconds);
            send("closeDelay", tag);
        }

        @Override public void onClick(double mouseX, double mouseY) { dragging = true; super.onClick(mouseX, mouseY); }

        @Override public void onRelease(double mouseX, double mouseY) {
            dragging = false;
            value = fraction(seconds());
            updateMessage();
            super.onRelease(mouseX, mouseY);
        }

        void showServerDelay(int seconds) {
            if (dragging || seconds == shownServer) return;
            shownServer = sent = seconds;
            value = Math.clamp(fraction(seconds), 0, 1);
            updateMessage();
        }
    }
}
