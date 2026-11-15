package com.showzky.elevatormod;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

/**
 * Per-screen state shared by every viewer: operating mode, the shown floor page and the last
 * request result. Stored in the block entity, and in cabin part data while the cabin moves.
 */
public record TouchscreenSettings(TouchscreenMode mode, int page, Feedback feedback, long feedbackUntil) {
    public static final TouchscreenSettings DEFAULT = new TouchscreenSettings(TouchscreenMode.DESTINATION, 0, Feedback.NONE, 0);
    /** How long a request result stays on the display, in ticks. */
    public static final int FEEDBACK_TICKS = 60;

    /** Result of the last touch, shown briefly in place of the status line. */
    public enum Feedback {
        NONE, TRAVELLING, QUEUED, CALLED, ALREADY_HERE, WAITING_POWER, UNAVAILABLE, BLOCKED;
        public String translationKey() { return "touchscreen.elevatormod.feedback." + name().toLowerCase(Locale.ROOT); }
        static Feedback byName(String name) {
            for (Feedback feedback : values()) if (feedback.name().equals(name)) return feedback;
            return NONE;
        }
    }

    public TouchscreenSettings withMode(TouchscreenMode next) { return new TouchscreenSettings(next, 0, Feedback.NONE, 0); }
    public TouchscreenSettings withPage(int next) { return new TouchscreenSettings(mode, Math.max(0, next), feedback, feedbackUntil); }
    public TouchscreenSettings withFeedback(Feedback next, long gameTime) { return new TouchscreenSettings(mode, page, next, gameTime + FEEDBACK_TICKS); }
    public Feedback activeFeedback(long gameTime) { return gameTime < feedbackUntil ? feedback : Feedback.NONE; }

    public static TouchscreenSettings read(CompoundTag tag) {
        return new TouchscreenSettings(TouchscreenMode.byName(tag.getString("Mode")), Math.max(0, tag.getInt("Page")),
                Feedback.byName(tag.getString("Feedback")), tag.getLong("FeedbackUntil"));
    }

    public void write(CompoundTag tag) {
        tag.putString("Mode", mode.name());
        tag.putInt("Page", page);
        tag.putString("Feedback", feedback.name());
        tag.putLong("FeedbackUntil", feedbackUntil);
    }
}
