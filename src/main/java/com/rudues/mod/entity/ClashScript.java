package com.rudues.mod.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The end fight's clashes as a script both sides run: where the Infernal Hero and the player stand at any moment,
 * and which clip each plays. Positions are measured from the contact point O along the axis u (unit, horizontal,
 * from the Hero toward the player), so the server and the client always agree to the pixel and the two fists meet.
 * <pre>
 *   stage 0  punch clash      win: the Hero is shoved back ~6 blocks
 *   stage 1  ground-and-pound the player rushes in and uppercuts it onto its back, mounts it and pounds its guard;
 *                             win: a two-fisted slam, then it is hauled up overhead and thrown ~16 blocks, rising for...
 *                             lose: it kicks the player off over its head
 *   stage 2  beam clash       win: the beams overwhelm it - it falls
 * phases:  CHARGE (both run in / rear back) -> STRAIN (locked, the button-mash) -> RESOLVE (one breaks through) -> next
 * </pre>
 */
public final class ClashScript {
    private ClashScript() {
    }

    public static final int STAGE_PUNCH1 = 0, STAGE_POUND = 1, STAGE_BEAM = 2;
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
        return stage == STAGE_BEAM ? ClashData.BEAM_CLASH_END : stage == STAGE_POUND ? ClashData.POUND_CHARGE_END : ClashData.CLASH_CHARGE_CONTACT;
    }

    public static int strainTicks(int stage) {
        return stage == STAGE_BEAM ? STRAIN_TICKS_BEAM : STRAIN_TICKS_PUNCH;
    }

    public static int resolveTicks(int stage, int result) {
        if (stage == STAGE_POUND) {
            return result == RESULT_WIN ? ClashData.POUND_WIN_END : ClashData.POUND_LOSE_END;
        }
        if (result == RESULT_WIN) {
            return stage == STAGE_PUNCH1 ? ClashData.CLASH_LOSE_END : ClashData.HERO_FALL_END;
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
        if (stage == STAGE_POUND) {
            // the ground-and-pound is baked as signed positions along u from the Hero's start; at() wants the Hero's negated
            float a = poundAlong(heroSide, phase, result, t);
            return heroSide ? -a : a;
        }
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
                return reach - sample(ClashData.CLASH_LOSE_TRAVEL, t);
            }
            default:
                return reach;
        }
    }

    private static float poundAlong(boolean heroSide, int phase, int result, float t) {
        switch (phase) {
            case PH_CHARGE:
                return sample(heroSide ? ClashData.POUND_CHARGE_HERO_ALONG : ClashData.POUND_CHARGE_PLAYER_ALONG, t);
            case PH_RESOLVE:
                if (result == RESULT_WIN) {
                    return sample(heroSide ? ClashData.POUND_WIN_HERO_ALONG : ClashData.POUND_WIN_PLAYER_ALONG, t);
                }
                return sample(heroSide ? ClashData.POUND_LOSE_HERO_ALONG : ClashData.POUND_LOSE_PLAYER_ALONG, t);
            default:
                return heroSide ? ClashData.POUND_HERO_REST : ClashData.POUND_MOUNT;
        }
    }

    /** how high off the ground a fighter is: the pounce onto the Hero, it being hauled overhead and thrown, the kick-off */
    public static float height(boolean heroSide, int stage, int phase, int result, float t) {
        if (stage != STAGE_POUND) {
            return 0.0F;
        }
        switch (phase) {
            case PH_CHARGE:
                return sample(heroSide ? ClashData.POUND_CHARGE_HERO_H : ClashData.POUND_CHARGE_PLAYER_H, t);
            case PH_RESOLVE:
                if (result == RESULT_WIN) {
                    return sample(heroSide ? ClashData.POUND_WIN_HERO_H : ClashData.POUND_WIN_PLAYER_H, t);
                }
                return sample(heroSide ? ClashData.POUND_LOSE_HERO_H : ClashData.POUND_LOSE_PLAYER_H, t);
            default:
                return 0.0F;
        }
    }

    /** a point on the axis at a signed distance from O (+ toward the player's side), h above the ground */
    public static Vec3 along(Vec3 origin, Vec3 u, float a, float h) {
        return origin.add(u.scale(a)).add(0, h, 0);
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
                if (stage == STAGE_POUND) {
                    return new String[]{"pound_fall", "pound_rush"};
                }
                return stage == STAGE_BEAM ? new String[]{"beam_clash", "beam_clash"} : new String[]{"clash_charge", "clash_charge"};
            case PH_STRAIN:
                if (stage == STAGE_POUND) {
                    return new String[]{"pound_block", "pound_punch"};
                }
                return stage == STAGE_BEAM ? new String[]{"beam_strain", "beam_strain"} : new String[]{"clash_strain", "clash_strain"};
            case PH_RESOLVE:
                if (stage == STAGE_POUND) {
                    return result == RESULT_WIN ? new String[]{"pound_thrown", "pound_finish"} : new String[]{"pound_kick", "pound_kicked"};
                }
                if (result == RESULT_WIN) {
                    return stage == STAGE_BEAM ? new String[]{"hero_fall", "beam_win"} : new String[]{"clash_lose", "clash_win"};
                }
                return stage == STAGE_BEAM ? new String[]{"beam_win", "clash_lose"} : new String[]{"clash_win", "clash_lose"};
            default:
                return new String[]{null, null};
        }
    }
}
