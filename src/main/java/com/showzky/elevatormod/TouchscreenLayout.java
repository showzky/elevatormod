package com.showzky.elevatormod;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Positions of a touchscreen's floor tiles and touch controls in canvas units. Shared by the
 * renderer and the server-side touch resolver; it depends only on size, mode and display
 * state (never on font metrics), so both sides compute identical rectangles.
 */
public record TouchscreenLayout(Rect body, List<Tile> tiles, List<Control> controls,
                                @Nullable Rect pager, int page, int pageCount, @Nullable Rect callButton) {
    public record Rect(float x, float y, float w, float h) {
        public float right() { return x + w; }
        public float bottom() { return y + h; }
        public boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    public enum Action { FLOOR, CALL, PAGE_UP, PAGE_DOWN }
    /** A touchable area; {@code floorIndex} indexes {@link TouchscreenDisplayState#floors()}. */
    public record Control(Action action, Rect rect, int floorIndex) {}
    /** A floor shown in the list, touchable only in Destination mode. */
    public record Tile(int floorIndex, Rect rect) {}

    public static TouchscreenLayout compute(TouchscreenSize size, TouchscreenMode mode,
                                            TouchscreenDisplayState state, int requestedPage) {
        var m = size.metrics;
        float top = m.header() + m.gap();
        float bottom = size.canvasHeight() - m.margin() - m.footer();
        Rect body = new Rect(m.margin(), top, size.canvasWidth() - 2 * m.margin(), bottom - top);
        if (!state.linked()) return new TouchscreenLayout(body, List.of(), List.of(), null, 0, 1, null);
        return switch (mode) {
            case DESTINATION -> floorList(size, body, state, requestedPage, true);
            case INFORMATION -> size == TouchscreenSize.SMALL
                    ? new TouchscreenLayout(body, List.of(), List.of(), null, 0, 1, null)
                    : floorList(size, body, state, -1, false);
            case CALL -> callLayout(size, body, state);
        };
    }

    /** @param requestedPage page to show, or -1 to show the page containing the current floor */
    private static TouchscreenLayout floorList(TouchscreenSize size, Rect body, TouchscreenDisplayState state,
                                               int requestedPage, boolean interactive) {
        var m = size.metrics;
        int count = state.floors().size();
        int perPage = m.columns() * rows(body.h(), m);
        Rect pager = null;
        Rect list = body;
        if (count > perPage) {
            pager = new Rect(body.x(), body.bottom() - m.pagerHeight(), body.w(), m.pagerHeight());
            list = new Rect(body.x(), body.y(), body.w(), body.h() - m.pagerHeight() - m.gap());
            perPage = m.columns() * rows(list.h(), m);
        }
        int pageCount = Math.max(1, (count + perPage - 1) / perPage);
        int page = requestedPage < 0 ? Math.max(0, state.currentIndex()) / perPage : requestedPage;
        page = Math.clamp(page, 0, pageCount - 1);
        float tileWidth = (list.w() - (m.columns() - 1) * m.gap()) / m.columns();
        List<Tile> tiles = new ArrayList<>();
        List<Control> controls = new ArrayList<>();
        for (int i = page * perPage; i < Math.min(count, (page + 1) * perPage); i++) {
            int slot = i - page * perPage, column = slot % m.columns(), row = slot / m.columns();
            Rect rect = new Rect(list.x() + column * (tileWidth + m.gap()), list.y() + row * (m.rowHeight() + m.gap()), tileWidth, m.rowHeight());
            tiles.add(new Tile(i, rect));
            if (interactive) controls.add(new Control(Action.FLOOR, rect, i));
        }
        if (pager != null && interactive) {
            float buttonWidth = Math.round(pager.w() * 0.3F);
            if (page > 0) controls.add(new Control(Action.PAGE_UP, new Rect(pager.x(), pager.y(), buttonWidth, pager.h()), -1));
            if (page < pageCount - 1) controls.add(new Control(Action.PAGE_DOWN, new Rect(pager.right() - buttonWidth, pager.y(), buttonWidth, pager.h()), -1));
        }
        return new TouchscreenLayout(body, List.copyOf(tiles), List.copyOf(controls), pager, page, pageCount, null);
    }

    private static TouchscreenLayout callLayout(TouchscreenSize size, Rect body, TouchscreenDisplayState state) {
        float height = switch (size) { case SMALL -> 24; case TALL -> 40; case LARGE -> 42; };
        float width = size == TouchscreenSize.LARGE ? body.w() * 0.7F : body.w();
        Rect button = new Rect(body.x() + (body.w() - width) / 2, body.y() + (body.h() - height) / 2, width, height);
        boolean callable = state.connection() == TouchscreenDisplayState.Connection.LANDING && state.callIndex() >= 0;
        List<Control> controls = callable ? List.of(new Control(Action.CALL, button, state.callIndex())) : List.of();
        return new TouchscreenLayout(body, List.of(), controls, null, 0, 1, button);
    }

    private static int rows(float height, TouchscreenSize.Metrics m) {
        return Math.max(1, (int) ((height + m.gap()) / (m.rowHeight() + m.gap())));
    }

    public @Nullable Control controlAt(double x, double y) {
        for (Control control : controls) if (control.rect().contains(x, y)) return control;
        return null;
    }
}
