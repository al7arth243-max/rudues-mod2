package com.rudues.mod.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The end fight's clashes as a script both sides run: where the Infernal Hero and the player stand at any moment,
 * and which clip each plays. Positions are measured from the contact point O along the axis u (unit, horizontal,
 * from the Hero toward the player), so the server and the client always agree to the pixel and the two fists meet.
 * <pre>
 *   stage 0  punch clash      win: the Hero is shoved back ~6 blocks
 *   stage 1  punch clash      win: the Hero is launched ~16 blocks and lands in a skid, rising for...
 *   stage 2  beam clash       win: the beams overwhelm it - it falls
 * phases:  CHARGE (both run in / rear back) -> STRAIN (locked, the button-mash) -> RESOLVE (one breaks through) -> next
 * </pre>
 */
public final class ClashScript {
    private ClashScript() {
    }

    public static final int STAGE_PUNCH1 = 0, STAGE_PUNCH2 = 1, STAGE_BEAM = 2;
    public static final int PH_CHARGE = 0, PH_STRAIN = 1, PH_RESOLVE = 2, PH_WAIT = 3;
    public static final int RESULT_NONE = 0, RESULT_WIN = 1, RESULT_LOSE = 2;

    /** the QTE: how long it may last, how fast the Hero pushes back, what one press is worth */
    public static final int STRAIN_TICKS_PUNCH = 130, STRAIN_TICKS_BEAM = 150;
    /** to win: push the gauge above this line and keep it there for HOLD_TICKS (or be above it when time runs out) */
    public static final float WIN_LINE = 0.85F;
    public static final int HOLD_TICKS = 30;
    public static final float DECAY_PUNCH = 0.0065F, DECAY_BEAM = 0.0080F;
    public static final float PRESS_PUNCH = 0.045F, PRESS_BEAM = 0.040F;
    /** a pause after contact before the prompt appears and presses count */
    public static final int PROMPT_DELAY = 6;
    /** after a lost clash the player is free for this long, then the Hero comes again */
    public static final int RETRY_TICKS = 60;

    /** total run-in distance of the charge clip (blocks) */
    public static float chargeTotal() {
        return ClashData.CLASH_CHARGE_TRAVEL[ClashData.CLASH_CHARGE_END];
    }

    public static int chargeTicks(int stage) {
        return stage == STAGE_BEAM ? ClashData.BEAM_CLASH_END : ClashData.CLASH_CHARGE_CONTACT;
    }

    public static int strainTicks(int stage) {
        return stage == STAGE_BEAM ? STRAIN_TICKS_BEAM : STRAIN_TICKS_PUNCH;
    }

    public static int resolveTicks(int stage, int result) {
        if (result == RESULT_WIN) {
            return stage == STAGE_PUNCH1 ? ClashData.CLASH_LOSE_END : stage == STAGE_PUNCH2 ? ClashData.CLASH_LOSE_FAR_END : ClashData.HERO_FALL_END;
        }
        return ClashData.CLASH_LOSE_END;
    }

    private static float sample(float[] a, float t) {
        if (t <= 0) {
            return a[0];
        }
        int i = (int) t;
        if (i >= a.length - 1) {
            return a[a.length - 1];
        }
        return Mth.lerp(t - i, a[i], a[i + 1]);
    }

    /**
     * Distance of the Hero (heroSide = true) or the player from O along the axis at phase time t (ticks), and how high
     * it is off the ground (the launch in clash 2). For the beam clash the resting distances are dHero/dPlayer.
     */
    public static float dist(boolean heroSide, int stage, int phase, int result, float t, float dHero, float dPlayer) {
        if (stage == STAGE_BEAM) {
            float d = heroSide ? dHero : dPlayer;
            if (phase != PH_RESOLVE) {
                return d;
            }
            boolean winner = heroSide == (result == RESULT_LOSE);
            if (winner) {
                return d - sample(ClashData.BEAM_WIN_TRAVEL, t);
            }
            return d - sample(heroSide ? ClashData.HERO_FALL_TRAVEL : ClashData.CLASH_LOSE_TRAVEL, t);
        }
        float reach = heroSide ? ClashData.HERO_REACH : ClashData.PLAYER_REACH;
        switch (phase) {
            case PH_CHARGE:
                return reach + chargeTotal() - sample(ClashData.CLASH_CHARGE_TRAVEL, t);
            case PH_STRAIN:
                return reach;
            case PH_RESOLVE: {
                boolean winner = heroSide == (result == RESULT_LOSE);
                if (winner) {
                    return reach - sample(ClashData.CLASH_WIN_TRAVEL, t);
                }
                float[] lose = heroSide && stage == STAGE_PUNCH2 ? ClashData.CLASH_LOSE_FAR_TRAVEL : ClashData.CLASH_LOSE_TRAVEL;
                return reach - sample(lose, t);
            }
            default:
                return reach;
        }
    }

    /** the Hero's flight off the ground when clash 2 launches it */
    public static float height(boolean heroSide, int stage, int phase, int result, float t) {
        if (!heroSide || stage != STAGE_PUNCH2 || phase != PH_RESOLVE || result != RESULT_WIN) {
            return 0.0F;
        }
        float a = 2.4F, b = ClashData.CLASH_LOSE_FAR_LAND - 0.4F;
        if (t <= a || t >= b) {
            return 0.0F;
        }
        return 3.0F * Mth.sin((float) Math.PI * (t - a) / (b - a));
    }

    /** a point at distance d from O along the axis, toward the player when d > 0 on the player's side */
    public static Vec3 at(Vec3 origin, Vec3 u, boolean heroSide, float d, float h) {
        return origin.add(u.scale(heroSide ? -d : d)).add(0, h, 0);
    }

    /** yaw that faces along v (Minecraft convention) */
    public static float yawOf(Vec3 v) {
        return (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    public static Vec3 axis(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    /** the clips each side plays in a phase: {hero, player} (null = keep) */
    public static String[] clips(int stage, int phase, int result) {
        switch (phase) {
            case PH_CHARGE:
                return stage == STAGE_BEAM ? new String[]{"beam_clash", "beam_clash"} : new String[]{"clash_charge", "clash_charge"};
            case PH_STRAIN:
                return stage == STAGE_BEAM ? new String[]{"beam_strain", "beam_strain"} : new String[]{"clash_strain", "clash_strain"};
            case PH_RESOLVE:
                if (result == RESULT_WIN) {
                    return stage == STAGE_BEAM ? new String[]{"hero_fall", "beam_win"}
                            : new String[]{stage == STAGE_PUNCH2 ? "clash_lose_far" : "clash_lose", "clash_win"};
                }
                return stage == STAGE_BEAM ? new String[]{"beam_win", "clash_lose"} : new String[]{"clash_win", "clash_lose"};
            default:
                return new String[]{null, null};
        }
    }
}
