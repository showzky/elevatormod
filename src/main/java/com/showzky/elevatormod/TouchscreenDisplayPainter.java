package com.showzky.elevatormod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/**
 * Draws a touchscreen's live content directly onto its glass with native world rendering:
 * flat full-bright quads for panels and the vanilla font for text. Content is drawn in a few
 * thin layers in front of the glass so nothing z-fights, and all touch areas come from
 * {@link TouchscreenLayout}, the same layout the server uses to resolve touches.
 */
public final class TouchscreenDisplayPainter {
    /** Gap between drawing layers, in blocks. */
    private static final float LAYER = 0.004F;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int BACKGROUND = 0xFF0A0E12, HEADER = 0xFF111820, DIVIDER = 0xFF26323D;
    private static final int TEXT = 0xFFD8E1E8, LABEL = 0xFF7F8E9B, DIM = 0xFF3C4955;
    private static final int TILE = 0xFF151E26, TILE_EDGE = 0xFF2C3A47;
    private static final int AMBER = 0xFFFFB238, AMBER_DARK = 0xFF3A2808;
    private static final int BLUE = 0xFF4FB3FF, BLUE_DARK = 0xFF0E2A40;
    private static final int GREEN = 0xFF5CD67A, GREEN_DARK = 0xFF0F2E1A, RED = 0xFFFF5F52;

    private final Matrix4f matrix;
    private final MultiBufferSource buffers;
    private final Font font;
    private final TouchscreenSize size;
    private final float layerUnits;
    private final List<Runnable> text = new ArrayList<>();

    private TouchscreenDisplayPainter(Matrix4f matrix, MultiBufferSource buffers, Font font, TouchscreenSize size) {
        this.matrix = matrix; this.buffers = buffers; this.font = font; this.size = size;
        this.layerUnits = LAYER * size.unitsPerBlock;
    }

    /**
     * Paints a screen whose master cell's minimum corner is the pose origin.
     * @param gameTime used to expire request feedback
     */
    public static void paint(PoseStack pose, MultiBufferSource buffers, TouchscreenSize size, Direction facing,
                             TouchscreenSettings settings, TouchscreenDisplayState state, long gameTime) {
        pose.pushPose();
        pose.translate(0.5F, 0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5F, 0F, -0.5F);
        pose.translate(TouchscreenSize.BORDER_PIXELS / 16F, (size.height * 16 - TouchscreenSize.BORDER_PIXELS) / 16F,
                TouchscreenSize.GLASS_DEPTH_PIXELS / 16F + LAYER);
        float scale = 1F / size.unitsPerBlock;
        pose.scale(scale, -scale, scale);
        var painter = new TouchscreenDisplayPainter(pose.last().pose(), buffers, Minecraft.getInstance().font, size);
        painter.draw(settings, state, settings.activeFeedback(gameTime));
        pose.popPose();
    }

    private void draw(TouchscreenSettings settings, TouchscreenDisplayState state, TouchscreenSettings.Feedback feedback) {
        int width = size.canvasWidth(), height = size.canvasHeight();
        var m = size.metrics;
        fill(0, 0, width, height, 0, BACKGROUND);
        fill(0, 0, width, m.header(), 1, HEADER);
        fill(0, m.header(), width, m.header() + 0.75F, 2, DIVIDER);
        fill(0, 0, width, size == TouchscreenSize.LARGE ? 2 : 1.5F, 2, connectionColor(state.connection()));
        var layout = TouchscreenLayout.compute(size, settings.mode(), state, settings.page());
        header(settings.mode(), state, feedback);
        if (m.footer() > 0) footer(settings.mode(), state, feedback);
        if (!state.linked()) message(layout.body(), Component.translatable(state.connection().translationKey()), connectionColor(state.connection()));
        else switch (settings.mode()) {
            case DESTINATION -> floorList(layout, state, true);
            case INFORMATION -> {
                if (size == TouchscreenSize.SMALL) informationSummary(layout.body(), state);
                else floorList(layout, state, false);
            }
            case CALL -> callPanel(layout, state);
        }
        text.forEach(Runnable::run);
    }

    // ---- regions ----

    private void header(TouchscreenMode mode, TouchscreenDisplayState state, TouchscreenSettings.Feedback feedback) {
        int width = size.canvasWidth();
        String current = state.current() != null ? state.current().name() : state.linked() ? "--" : "";
        var destination = state.destination();
        switch (size) {
            case SMALL -> {
                travelArrows(width - 13, 3, 9, 20, state.travel());
                text(current, 4, 4.5F, TEXT, width - 20, 1);
                // One line for destination, request result or status.
                if (feedback != TouchscreenSettings.Feedback.NONE) text(tr(feedback.translationKey()), 4, 16, AMBER, width - 20, 1);
                else if (destination != null) text("> " + destination.name(), 4, 16, AMBER, width - 20, 1);
                else if (mode == TouchscreenMode.CALL && state.callPending()) text(tr("touchscreen.elevatormod.called"), 4, 16, AMBER, width - 20, 1);
                else if (state.linked()) text(tr(state.status().translationKey()), 4, 16, statusColor(state.status()), width - 20, 1);
            }
            case TALL -> {
                travelArrows(width - 15, 5, 11, 38, state.travel());
                text(tr("touchscreen.elevatormod.current"), 4, 5, LABEL, width - 22, 1);
                text(current, 4, 15, TEXT, width - 22, 1);
                text(tr("touchscreen.elevatormod.destination"), 4, 28, LABEL, width - 22, 1);
                text(destination != null ? destination.name() : "--", 4, 38, destination != null ? AMBER : DIM, width - 22, 1);
            }
            case LARGE -> {
                travelArrows(width - 24, 6, 18, 40, state.travel());
                text(tr("touchscreen.elevatormod.current_floor"), 6, 6, LABEL, width - 36, 1);
                text(current, 6, 16, TEXT, width - 36, 2);
                text(tr("touchscreen.elevatormod.destination"), 6, 37, LABEL, 64, 1);
                text(destination != null ? destination.name() : "--", 72, 37, destination != null ? AMBER : DIM, width - 108, 1);
            }
        }
    }

    private void footer(TouchscreenMode mode, TouchscreenDisplayState state, TouchscreenSettings.Feedback feedback) {
        int width = size.canvasWidth(), height = size.canvasHeight();
        float top = height - size.metrics.footer();
        fill(0, top, width, height, 1, HEADER);
        fill(0, top, width, top + 0.75F, 2, DIVIDER);
        float y = top + (size.metrics.footer() - 7) / 2F + 0.5F;
        String status; int color;
        if (feedback != TouchscreenSettings.Feedback.NONE) { status = tr(feedback.translationKey()); color = AMBER; }
        else if (!state.linked()) { status = tr("touchscreen.elevatormod.offline"); color = DIM; }
        else if (mode == TouchscreenMode.CALL && state.callPending()) { status = tr("touchscreen.elevatormod.called"); color = AMBER; }
        else { status = tr(state.status().translationKey()); color = statusColor(state.status()); }
        fill(4, y + 1.5F, 8, y + 5.5F, 2, color);
        String link = state.linked() ? tr(state.connection().translationKey()) : "";
        float linkWidth = size == TouchscreenSize.LARGE ? font.width(link) : 0;
        text(status, 11, y, color, width - 15 - linkWidth - 4, 1);
        if (linkWidth > 0) text(link, width - 4 - linkWidth, y, LABEL, linkWidth, 1);
    }

    private void floorList(TouchscreenLayout layout, TouchscreenDisplayState state, boolean interactive) {
        if (state.floors().isEmpty()) {
            message(layout.body(), Component.translatable("touchscreen.elevatormod.no_floors"), LABEL);
            return;
        }
        for (var tile : layout.tiles()) {
            var floor = state.floors().get(tile.floorIndex());
            var r = tile.rect();
            boolean current = tile.floorIndex() == state.currentIndex(), destination = tile.floorIndex() == state.destinationIndex();
            int fillColor = current ? BLUE_DARK : destination ? AMBER_DARK : interactive ? TILE : BACKGROUND;
            int accent = current ? BLUE : destination || floor.queued() ? AMBER : interactive ? TILE_EDGE : DIM;
            fill(r.x(), r.y(), r.right(), r.bottom(), 1, fillColor);
            if (interactive) frame(r, 0.75F, accent);
            else fill(r.x(), r.bottom() - 0.6F, r.right(), r.bottom(), 2, DIVIDER);
            fill(r.x(), r.y(), r.x() + 2, r.bottom(), 2, accent);
            int textColor = current ? 0xFFFFFFFF : destination ? AMBER : TEXT;
            text(floor.name(), r.x() + 5, r.y() + (r.h() - 7) / 2F, textColor, r.w() - 8, 1);
        }
        var pager = layout.pager();
        if (pager == null) return;
        String page = (layout.page() + 1) + "/" + layout.pageCount();
        text(page, pager.x() + (pager.w() - font.width(page)) / 2F, pager.y() + (pager.h() - 7) / 2F, LABEL, pager.w(), 1);
        if (!interactive) return;
        float buttonWidth = Math.round(pager.w() * 0.3F);
        pagerButton(new TouchscreenLayout.Rect(pager.x(), pager.y(), buttonWidth, pager.h()), true, layout.page() > 0);
        pagerButton(new TouchscreenLayout.Rect(pager.right() - buttonWidth, pager.y(), buttonWidth, pager.h()), false, layout.page() < layout.pageCount() - 1);
    }

    private void pagerButton(TouchscreenLayout.Rect r, boolean up, boolean enabled) {
        fill(r.x(), r.y(), r.right(), r.bottom(), 1, enabled ? TILE : BACKGROUND);
        frame(r, 0.75F, enabled ? TILE_EDGE : DIVIDER);
        float s = Math.min(r.h() - 6, 8), cx = r.x() + r.w() / 2, cy = r.y() + r.h() / 2;
        triangle(cx, cy, s, up, enabled ? TEXT : DIM);
    }

    private void callPanel(TouchscreenLayout layout, TouchscreenDisplayState state) {
        var button = layout.callButton();
        if (button == null) return;
        var body = layout.body();
        boolean callable = !layout.controls().isEmpty();
        var landing = state.callFloor();
        boolean here = landing != null && state.currentIndex() == state.callIndex() && state.travel() == TouchscreenDisplayState.Travel.IDLE;
        float labelY = body.y() + Math.max(1, (button.y() - body.y() - 18) / 2F);
        text(tr("touchscreen.elevatormod.call_to"), body.x(), labelY, LABEL, body.w(), 1);
        String landingName = state.connection() == TouchscreenDisplayState.Connection.CABIN ? tr("touchscreen.elevatormod.cabin_screen")
                : landing != null ? landing.name() : tr("touchscreen.elevatormod.no_landing");
        text(landingName, body.x(), labelY + 9, TEXT, body.w(), 1);
        int fillColor = !callable ? TILE : state.callPending() ? AMBER_DARK : GREEN_DARK;
        int edge = !callable ? TILE_EDGE : state.callPending() ? AMBER : GREEN;
        fill(button.x(), button.y(), button.right(), button.bottom(), 1, fillColor);
        frame(button, 1, edge);
        String label = tr(!callable ? "touchscreen.elevatormod.call_disabled" : state.callPending() ? "touchscreen.elevatormod.called" : "touchscreen.elevatormod.call");
        float scale = size == TouchscreenSize.LARGE ? 2 : 1;
        float labelWidth = Math.min(font.width(label) * scale, button.w() - 6);
        text(label, button.x() + (button.w() - labelWidth) / 2F, button.y() + (button.h() - 7 * scale) / 2F, !callable ? DIM : edge, button.w() - 6, scale);
        if (here) text(tr("touchscreen.elevatormod.elevator_here"), body.x(), button.bottom() + 4, GREEN, body.w(), 1);
    }

    private void informationSummary(TouchscreenLayout.Rect body, TouchscreenDisplayState state) {
        var destination = state.destination();
        text(tr("touchscreen.elevatormod.destination"), body.x(), body.y() + 2, LABEL, body.w(), 1);
        text(destination != null ? destination.name() : "--", body.x(), body.y() + 12, destination != null ? AMBER : DIM, body.w(), 1);
        text(tr("touchscreen.elevatormod.floors"), body.x(), body.y() + 26, LABEL, body.w() - 20, 1);
        String count = String.valueOf(state.floors().size());
        text(count, body.right() - font.width(count), body.y() + 26, TEXT, 20, 1);
        text(tr(state.connection().translationKey()), body.x(), body.y() + 40, GREEN, body.w(), 1);
    }

    private void message(TouchscreenLayout.Rect body, Component message, int color) {
        List<FormattedCharSequence> lines = font.split(message, (int) body.w());
        float y = body.y() + (body.h() - lines.size() * 10) / 2F;
        for (FormattedCharSequence line : lines) {
            float x = body.x() + (body.w() - font.width(line)) / 2F;
            float lineY = y;
            text.add(() -> font.drawInBatch(line, x, lineY, color, false, layer(3), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT));
            y += 10;
        }
    }

    private void travelArrows(float x, float y, float width, float height, TouchscreenDisplayState.Travel travel) {
        float s = width, cx = x + width / 2;
        triangle(cx, y + height * 0.28F, s, true, travel == TouchscreenDisplayState.Travel.UP ? AMBER : DIM);
        triangle(cx, y + height * 0.72F, s, false, travel == TouchscreenDisplayState.Travel.DOWN ? AMBER : DIM);
    }

    // ---- primitives ----

    private static String tr(String key) { return Component.translatable(key).getString(); }

    private static int connectionColor(TouchscreenDisplayState.Connection connection) {
        return switch (connection) {
            case LANDING, CABIN -> GREEN;
            case SEARCHING -> AMBER;
            default -> RED;
        };
    }

    private static int statusColor(TouchscreenDisplayState.Status status) {
        return switch (status) {
            case READY -> GREEN;
            case MOVING -> AMBER;
            case NO_POWER, FAULT -> RED;
        };
    }

    private Matrix4f layer(int layer) { return new Matrix4f(matrix).translate(0, 0, layer * layerUnits); }

    private void fill(float x0, float y0, float x1, float y1, int layer, int argb) {
        float z = layer * layerUnits;
        VertexConsumer out = buffers.getBuffer(RenderType.textBackground());
        // Same winding as font glyphs, which face the viewer under the flipped Y axis.
        out.addVertex(matrix, x0, y0, z).setColor(argb).setLight(LIGHT);
        out.addVertex(matrix, x0, y1, z).setColor(argb).setLight(LIGHT);
        out.addVertex(matrix, x1, y1, z).setColor(argb).setLight(LIGHT);
        out.addVertex(matrix, x1, y0, z).setColor(argb).setLight(LIGHT);
    }

    private void frame(TouchscreenLayout.Rect r, float thickness, int argb) {
        fill(r.x(), r.y(), r.right(), r.y() + thickness, 2, argb);
        fill(r.x(), r.bottom() - thickness, r.right(), r.bottom(), 2, argb);
        fill(r.x(), r.y(), r.x() + thickness, r.bottom(), 2, argb);
        fill(r.right() - thickness, r.y(), r.right(), r.bottom(), 2, argb);
    }

    /** An isosceles triangle of the given width centred on (cx, cy), pointing up or down. */
    private void triangle(float cx, float cy, float width, boolean up, int argb) {
        float half = width / 2, h = width * 0.6F;
        float tip = up ? cy - h / 2 : cy + h / 2, base = up ? cy + h / 2 : cy - h / 2;
        float z = 2 * layerUnits;
        VertexConsumer out = buffers.getBuffer(RenderType.textBackground());
        if (up) {
            out.addVertex(matrix, cx, tip, z).setColor(argb).setLight(LIGHT);
            out.addVertex(matrix, cx - half, base, z).setColor(argb).setLight(LIGHT);
            out.addVertex(matrix, cx + half, base, z).setColor(argb).setLight(LIGHT);
            out.addVertex(matrix, cx + half, base, z).setColor(argb).setLight(LIGHT);
        } else {
            out.addVertex(matrix, cx - half, base, z).setColor(argb).setLight(LIGHT);
            out.addVertex(matrix, cx, tip, z).setColor(argb).setLight(LIGHT);
            out.addVertex(matrix, cx, tip, z).setColor(argb).setLight(LIGHT);
            out.addVertex(matrix, cx + half, base, z).setColor(argb).setLight(LIGHT);
        }
    }

    /** Queues single-line text, abbreviated to {@code maxWidth} canvas units at the given scale. */
    private void text(String value, float x, float y, int color, float maxWidth, float scale) {
        String fitted = fit(value, (int) (maxWidth / scale));
        if (fitted.isEmpty()) return;
        text.add(() -> {
            Matrix4f at = layer(3).translate(x, y, 0).scale(scale, scale, 1);
            font.drawInBatch(fitted, 0, 0, color, false, at, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT);
        });
    }

    private String fit(String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "..";
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ellipsis))).stripTrailing() + ellipsis;
    }
}
