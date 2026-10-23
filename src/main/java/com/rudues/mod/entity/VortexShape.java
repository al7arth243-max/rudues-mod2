package com.rudues.mod.entity;

import net.minecraft.util.Mth;

/**
 * The Inferno Vortex's fire tornado: timeline, shape and pull, shared by the server (damage, mobs), the client
 * (pulling the local player, particles) and the renderer, so they all agree. Ticks from the start of the move.
 */
public final class VortexShape {
    private VortexShape() {
    }

    /** the whole attack lasts 12 seconds */
    public static final int END_TICK = 240;
    /** vanishes, re-forms at the arena's centre a few ticks later */
    public static final int TELEPORT_TICK = 6;
    public static final int APPEAR_TICK = 9;
    /** the tornado erupts, reaches full size, starts dying, is gone */
    public static final int IGNITE_TICK = 26;
    public static final int FULL_TICK = 42;
    public static final int COLLAPSE_TICK = 224;
    public static final int GONE_TICK = 236;
    /** fire geysers erupt around the arena between these ticks */
    public static final int GEYSERS_FROM = 36;
    public static final int GEYSERS_TO = 196;

    public static final float HEIGHT = 13.0F;
    public static final float BASE_RADIUS = 1.9F;
    public static final float TOP_RADIUS = 5.5F;
    /** how far the pull reaches (blocks from the axis) */
    public static final double PULL_RANGE = 32.0;

    /** 0..1: how formed the tornado is */
    public static float strength(float t) {
        if (t < IGNITE_TICK || t >= GONE_TICK) {
            return 0.0F;
        }
        if (t < FULL_TICK) {
            float u = (t - IGNITE_TICK) / (FULL_TICK - IGNITE_TICK);
            return u * u * (3 - 2 * u);
        }
        if (t < COLLAPSE_TICK) {
            return 1.0F;
        }
        float u = 1.0F - (t - COLLAPSE_TICK) / (GONE_TICK - COLLAPSE_TICK);
        return u * u * (3 - 2 * u);
    }

    /** radius at a height above the base (it widens toward the top) */
    public static float radiusAt(float dy, float strength) {
        float u = Mth.clamp(dy / HEIGHT, 0.0F, 1.0F);
        return (BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * (float) Math.pow(u, 1.4)) * (0.35F + 0.65F * strength);
    }

    /** the funnel sways: horizontal offset of its centre at a height (x, z) */
    public static float swayX(float dy, float t) {
        return 0.45F * Mth.sin(dy * 0.42F + t * 0.11F) * Mth.clamp(dy / HEIGHT, 0.0F, 1.0F);
    }

    public static float swayZ(float dy, float t) {
        return 0.45F * Mth.cos(dy * 0.37F + t * 0.09F) * Mth.clamp(dy / HEIGHT, 0.0F, 1.0F);
    }

    /**
     * Horizontal pull toward the axis, as an acceleration (blocks/tick^2). Standing still it drags you in at about
     * 2.6 blocks/s; walking away still gets you out slowly and sprinting easily.
     */
    public static double pull(double distance, boolean onGround, float strength) {
        if (distance > PULL_RANGE || distance < 0.4 || strength <= 0.0F) {
            return 0.0;
        }
        double drift = distance <= 10.0 ? 0.13 : 0.13 - 0.07 * (distance - 10.0) / (PULL_RANGE - 10.0);
        // terminal drift = accel / (1 - friction): ground friction 0.546, air 0.91
        return drift * (onGround ? 0.454 : 0.09) * strength;
    }

    /** is a point (relative to the base centre) inside the burning funnel? */
    public static boolean inside(double dx, double dy, double dz, float t, double margin) {
        float s = strength(t);
        if (s <= 0.0F || dy < -0.5 || dy > HEIGHT * s) {
            return false;
        }
        float h = (float) Math.max(dy, 0.0);
        double x = dx - swayX(h, t), z = dz - swayZ(h, t);
        return Math.sqrt(x * x + z * z) <= radiusAt(h, s) + margin;
    }
}
