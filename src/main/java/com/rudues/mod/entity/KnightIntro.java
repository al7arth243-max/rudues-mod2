package com.rudues.mod.entity;

import com.rudues.mod.world.CastleLayout;

/**
 * The Feathered Knight's entrance, tick by tick from the moment a player steps onto the keep roof. Shared by the
 * server (her clip, the player's walk) and the client (the camera).
 * <pre>
 *   0 .. 12    fade from black; the player is set down on the roof's north side at tick 6
 *  12 .. 102   the player walks south to the middle of the arena (side-on shot, panning to find her on the wall)
 *  40          her clip starts: sat on the south parapet, back to the arena, looking at the moon
 * 110 .. 140   she looks over her right shoulder at them (close profile shot)
 * 152 .. 180   she swings round, pushes off the wall and drops into the foreground (low shot), rises to guard
 * 204          the fight
 * </pre>
 */
public final class KnightIntro {
    private KnightIntro() {
    }

    public static final int PLACE = 6;
    public static final int FADE = 14;
    public static final int WALK_START = 12, WALK_END = 102;
    /** her intro clip starts here; before it she loops the "sit" clip */
    public static final int CLIP_AT = 40;
    public static final int LOOK = CLIP_AT + KnightMoveData.INTRO_LOOK;
    public static final int SWING = CLIP_AT + KnightMoveData.INTRO_SWING;
    public static final int PUSH = CLIP_AT + KnightMoveData.INTRO_PUSH;
    public static final int LAND = CLIP_AT + KnightMoveData.INTRO_LAND;
    public static final int CLIP_END = CLIP_AT + KnightMoveData.INTRO_END;
    public static final int RELEASE = CLIP_END + 4;
    /** camera cuts */
    public static final int SHOT_B = 104, SHOT_C = 166;

    /** where she lands (and stands, invisible-footed, while drawn sat on the wall three blocks behind) */
    public static final double LAND_X = CastleLayout.ARENA_CX + 3.5, LAND_Z = CastleLayout.KZ1 - 2.5;
    public static final double Y = CastleLayout.ROOF;
    /** she faces north, into the arena */
    public static final float KNIGHT_YAW = 180.0F;
    /** the player's walk: down the arena's north-south line, ending at its heart */
    public static final double WALK_X = CastleLayout.ARENA_CX + 0.5;
    public static final double WALK_Z0 = CastleLayout.ARENA_CZ - 9.5, WALK_Z1 = CastleLayout.ARENA_CZ + 0.5;
    public static final float PLAYER_YAW = 0.0F;

    /** 0..1 along the walk, eased in and out */
    public static double walk(double t) {
        double k = Math.max(0.0, Math.min(1.0, (t - WALK_START) / (WALK_END - WALK_START)));
        // a gentle start and stop around a steady middle
        double a = 0.12;
        if (k < a) {
            return k * k / (2 * a * (1 - a));
        }
        if (k > 1 - a) {
            double r = 1 - k;
            return 1 - r * r / (2 * a * (1 - a));
        }
        return (k - a / 2) / (1 - a);
    }

    public static double walkZ(double t) {
        return WALK_Z0 + (WALK_Z1 - WALK_Z0) * walk(t);
    }

    private static float track(float[] a, double clipTick) {
        int i = (int) Math.floor(clipTick);
        if (i < 0) {
            return a[0];
        }
        if (i >= a.length - 1) {
            return a[a.length - 1];
        }
        float f = (float) (clipTick - i);
        return a[i] + (a[i + 1] - a[i]) * f;
    }

    /** her head during the intro, in the world (the clip is drawn in the frame of her standing spot) */
    public static double[] head(double t) {
        return point(t, KnightMoveData.INTRO_HEAD_F, KnightMoveData.INTRO_HEAD_R, KnightMoveData.INTRO_HEAD_U);
    }

    public static double[] hips(double t) {
        return point(t, KnightMoveData.INTRO_HIPS_F, KnightMoveData.INTRO_HIPS_R, KnightMoveData.INTRO_HIPS_U);
    }

    private static double[] point(double t, float[] fa, float[] ra, float[] ua) {
        double c = t - CLIP_AT;
        float f = track(fa, c), r = track(ra, c), u = track(ua, c);
        // facing north (yaw 180): forward is -z, her right is +x
        return new double[]{LAND_X + r, Y + u, LAND_Z - f};
    }
}
