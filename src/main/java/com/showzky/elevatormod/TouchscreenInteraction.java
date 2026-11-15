package com.showzky.elevatormod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side touchscreen input. The touch point is always recomputed from the player's own
 * eye position and view direction, and the touched control is resolved from the server's
 * layout and floor list, so clients can neither choose destinations nor forge coordinates.
 */
public final class TouchscreenInteraction {
    /** Maximum distance for changing a screen's mode. */
    public static final double SETTINGS_RANGE = 8;

    /** Touch on a stationary screen (including a cabin screen while parked). */
    public static void touch(ServerPlayer player, ElevatorTouchscreenBlockEntity screen) {
        Level level = screen.getLevel();
        if (level == null || player.isSpectator()) return;
        var hit = TouchscreenGeometry.intersect(screen.size(), screen.facing(), Vec3.atLowerCornerOf(screen.getBlockPos()),
                player.getEyePosition(), player.getViewVector(1), reach(player));
        if (hit == null) return;
        PoweredPulleyBlockEntity pulley = screen.linkedPulley();
        TouchscreenDisplayState state = screen.displayState();
        TouchscreenSettings settings = screen.settings();
        TouchscreenSettings next = apply(level, screen.getBlockPos(), screen.size(), settings, state, pulley, hit);
        if (next == null || next.equals(settings)) return;
        if (!screen.isRemoved()) { screen.setSettings(next); return; }
        // The request assembled this screen's own cabin; its data now lives in the moving part.
        if (pulley != null && pulley.motion.active()) {
            int index = partIndex(pulley, screen.getBlockPos().subtract(pulley.motion.origin));
            if (index >= 0) writePart(pulley, index, next);
        }
    }

    /** Touch on a screen carried by a moving cabin. {@code clientCabinY} is only accepted near the server's position. */
    public static void touchCabin(ServerPlayer player, BlockPos pulleyPos, BlockPos offset, double clientCabinY) {
        Level level = player.level();
        if (player.isSpectator() || !level.hasChunkAt(pulleyPos) || !(level.getBlockEntity(pulleyPos) instanceof PoweredPulleyBlockEntity pulley)) return;
        var motion = pulley.motion;
        int index = partIndex(pulley, offset);
        if (!motion.active() || index < 0) return;
        var part = motion.parts.get(index);
        if (!(part.state().getBlock() instanceof ElevatorTouchscreenBlock block) || !ElevatorTouchscreenBlock.isMaster(part.state())) return;
        // Clients render the cabin slightly behind the server; accept their height within a small window.
        double tolerance = Math.max(0.75, Math.abs(motion.velocity) * 10);
        double cabinY = Double.isFinite(clientCabinY) && Math.abs(clientCabinY - motion.y) <= tolerance ? clientCabinY : motion.y;
        Vec3 corner = cabinCorner(pulley, offset, cabinY);
        var facing = part.state().getValue(ElevatorTouchscreenBlock.FACING);
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1);
        var hit = TouchscreenGeometry.intersect(block.size(), facing, corner, eye, look, reach(player));
        if (hit == null) return;
        Vec3 point = eye.add(look.normalize().scale(hit.distance()));
        if (level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return;
        TouchscreenSettings settings = TouchscreenSettings.read(part.data());
        if (player.isSecondaryUseActive()) {
            openSettings(player, TouchscreenTarget.cabin(pulleyPos, offset), settings.mode());
            return;
        }
        TouchscreenDisplayState state = TouchscreenDisplayState.of(TouchscreenDisplayState.Connection.CABIN, pulley, TouchscreenDisplayState.NO_STOP);
        TouchscreenSettings next = apply(level, BlockPos.containing(point), block.size(), settings, state, pulley, hit);
        if (next != null && !next.equals(settings)) {
            int current = partIndex(pulley, offset);
            if (current >= 0) writePart(pulley, current, next);
        }
    }

    /** Applies a validated mode change from the settings screen. */
    public static void configure(ServerPlayer player, TouchscreenTarget target, TouchscreenMode mode) {
        Level level = player.level();
        if (player.isSpectator()) return;
        if (!target.inCabin()) {
            if (!level.hasChunkAt(target.pos()) || player.distanceToSqr(target.pos().getCenter()) > SETTINGS_RANGE * SETTINGS_RANGE) return;
            if (level.getBlockEntity(target.pos()) instanceof ElevatorTouchscreenBlockEntity screen) screen.setSettings(screen.settings().withMode(mode));
            return;
        }
        if (!level.hasChunkAt(target.pulley()) || !(level.getBlockEntity(target.pulley()) instanceof PoweredPulleyBlockEntity pulley) || !pulley.motion.active()) return;
        int index = partIndex(pulley, target.pos());
        if (index < 0 || !ElevatorTouchscreenBlock.isMaster(pulley.motion.parts.get(index).state())) return;
        Vec3 center = cabinCorner(pulley, target.pos(), pulley.motion.y).add(0.5, 0.5, 0.5);
        if (player.distanceToSqr(center) > SETTINGS_RANGE * SETTINGS_RANGE) return;
        writePart(pulley, index, TouchscreenSettings.read(pulley.motion.parts.get(index).data()).withMode(mode));
    }

    public static void openSettings(ServerPlayer player, TouchscreenTarget target, TouchscreenMode mode) {
        PacketDistributor.sendToPlayer(player, new TouchscreenSettingsPayload(target, mode.ordinal()));
    }

    /**
     * Resolves the touched control and performs it.
     * @return the updated settings, or null when nothing was touched
     */
    static @Nullable TouchscreenSettings apply(Level level, BlockPos soundPos, TouchscreenSize size, TouchscreenSettings settings,
                                               TouchscreenDisplayState state, @Nullable PoweredPulleyBlockEntity pulley,
                                               TouchscreenGeometry.Hit hit) {
        var layout = TouchscreenLayout.compute(size, settings.mode(), state, settings.page());
        var control = layout.controlAt(hit.canvasX(), hit.canvasY());
        if (control == null) return null;
        level.playSound(null, soundPos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.25F, 1.8F);
        return switch (control.action()) {
            case PAGE_UP -> settings.withPage(layout.page() - 1);
            case PAGE_DOWN -> settings.withPage(layout.page() + 1);
            case FLOOR -> {
                if (settings.mode() != TouchscreenMode.DESTINATION || pulley == null) yield settings;
                boolean travelling = state.travel() != TouchscreenDisplayState.Travel.IDLE;
                var result = ElevatorTravelRequest.request(pulley, state.floors().get(control.floorIndex()).stop());
                yield settings.withFeedback(result.feedback(false, travelling), level.getGameTime());
            }
            case CALL -> {
                if (settings.mode() != TouchscreenMode.CALL || state.connection() != TouchscreenDisplayState.Connection.LANDING || pulley == null) yield settings;
                var result = ElevatorTravelRequest.request(pulley, state.floors().get(control.floorIndex()).stop());
                yield settings.withFeedback(result.feedback(true, false), level.getGameTime());
            }
        };
    }

    static Vec3 cabinCorner(PoweredPulleyBlockEntity pulley, BlockPos offset, double cabinY) {
        BlockPos origin = pulley.motion.origin;
        return new Vec3(origin.getX() + offset.getX(), cabinY + offset.getY(), origin.getZ() + offset.getZ());
    }

    static int partIndex(PoweredPulleyBlockEntity pulley, BlockPos offset) {
        var parts = pulley.motion.parts;
        for (int i = 0; i < parts.size(); i++) if (parts.get(i).offset().equals(offset)) return i;
        return -1;
    }

    private static void writePart(PoweredPulleyBlockEntity pulley, int index, TouchscreenSettings settings) {
        var part = pulley.motion.parts.get(index);
        CompoundTag data = part.data().copy();
        settings.write(data);
        pulley.motion.parts.set(index, new ElevatorCabinMotion.Part(part.offset(), part.state(), data));
        pulley.sync();
    }

    private static double reach(ServerPlayer player) { return player.blockInteractionRange() + 0.5; }

    private TouchscreenInteraction() {}
}
