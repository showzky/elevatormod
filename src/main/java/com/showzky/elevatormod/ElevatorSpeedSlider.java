package com.showzky.elevatormod;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * The elevator's travel speed control, shared by the Powered Pulley and Elevator Controller
 * screens. Drags continuously, snaps to 0.5 blocks/s steps and only reports a new speed when the
 * step changes. Server updates are shown unless the player is dragging.
 */
public final class ElevatorSpeedSlider extends AbstractSliderButton {
    private static final double MIN = ElevatorEnergyUsage.MIN_SPEED, MAX = ElevatorEnergyUsage.MAX_SPEED;
    private final DoubleConsumer onChange;
    private double sentSpeed = Double.NaN;
    private double shownServerSpeed = Double.NaN;
    private boolean dragging;

    public ElevatorSpeedSlider(int x, int y, int width, int height, DoubleConsumer onChange) {
        super(x, y, width, height, Component.empty(), fraction(ElevatorEnergyUsage.DEFAULT_SPEED));
        this.onChange = onChange;
        updateMessage();
    }

    private static double fraction(double speed) { return (speed - MIN) / (MAX - MIN); }

    /** The selected speed in blocks per tick. */
    public double speed() { return ElevatorEnergyUsage.sanitizeSpeed(MIN + value * (MAX - MIN)); }

    @Override protected void updateMessage() {
        setMessage(Component.literal(String.format(Locale.ROOT, "Speed: %.1f blocks/s", ElevatorEnergyUsage.metresPerSecond(speed()))));
    }

    @Override protected void applyValue() {
        double speed = speed();
        if (speed == sentSpeed) return;
        sentSpeed = speed;
        onChange.accept(speed);
    }

    @Override public void onClick(double mouseX, double mouseY) { dragging = true; super.onClick(mouseX, mouseY); }

    @Override public void onRelease(double mouseX, double mouseY) {
        dragging = false;
        value = fraction(speed());
        updateMessage();
        super.onRelease(mouseX, mouseY);
    }

    /** Shows the saved speed when it first arrives or when someone else changes it. */
    public void showServerSpeed(double speed) {
        if (dragging || speed == shownServerSpeed) return;
        shownServerSpeed = speed;
        sentSpeed = speed;
        value = Math.clamp(fraction(speed), 0, 1);
        updateMessage();
    }
}
