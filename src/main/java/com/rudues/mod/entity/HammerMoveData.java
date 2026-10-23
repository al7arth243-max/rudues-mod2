package com.rudues.mod.entity;

/**
 * Timings for the Infernal Shadow's "Hammer and Anvil" (generated from
 * animations/entity/infernal_shadow.animation.json - do not edit by hand). Ticks from the start of the move.
 */
public final class HammerMoveData {
    private HammerMoveData() {
    }

    /** last tick of the move (animation length 3.10 s) */
    public static final int END_TICK = 62;
    /** it leaps; vanishes a moment later */
    public static final int LEAP_TICK = 6;
    /** teleported under the target: the red circle appears */
    public static final int TELEPORT_TICK = 8;
    /** it re-forms in the air above the circle */
    public static final int APPEAR_TICK = 11;
    /** the circle stops following the target: the last chance to get out */
    public static final int LOCK_TICK = 23;
    /** it drops */
    public static final int DIVE_TICK = 32;
    /** the fist hits the floor (2 s after the circle appears) */
    public static final int IMPACT_TICK = 38;
    /** it pushes itself back up */
    public static final int RISE_TICK = 47;
    /** how high above the circle it hovers (blocks; drawn by the animation's root offset) */
    public static final float HOVER_HEIGHT = 6.000F;
    /** where the fist lands, from the Shadow's feet (forward, right; blocks) - the circle is centred there */
    public static final float FIST_F = 0.925F;
    public static final float FIST_R = -0.321F;
    /** radius of the red circle: full damage inside */
    public static final float RADIUS = 3.0F;
    /** outer ring: lighter damage and knockback */
    public static final float OUTER_RADIUS = 4.5F;
}
