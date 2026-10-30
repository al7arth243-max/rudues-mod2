package com.rudues.mod.entity;

import com.rudues.mod.world.CraterLayout;
import net.minecraft.world.phys.Vec3;

/**
 * Garuda's scripted stretches, shared by the server and the client (camera, gauge, where the Hero of Hell is drawn).
 * Positions are in the crater: its heart is (0, floor, 0).
 * <pre>
 * ARRIVE    the storm clears on the Hero of Hell rising from his pillar of fire and roaring; her screech cuts it short,
 *           she sweeps past his right side, turns at a distance and faces him
 * the end fight, at 5% of her health:
 * BLAST     a blast of wind from her hand tears his right arm off at the elbow
 * SNATCH    she lunges feet first and takes his chest in her talons: up, slammed down on his back and pinned while she
 *           screeches, up again, slammed again, then hurled into the sky
 * BALL      high up, his arm burns back out of the stump while she spreads her wings and gathers a great ball of wind
 *           between her hands; she drives it up at him and he comes down fist first
 * CAST      (only after a lost clash) her tornado throws him back into the sky, and BALL again
 * STRAIN1   the first clash: his push sets the ball alight
 * WIN1      he drives the fireball down onto her - it bursts, she is thrown down       (LOSE1: it bursts on him; again)
 * GRAB      he strides in, takes her by the throat and lifts her
 * PUNCH     burning hooks
 * KICK      her feet in his chest; he lets go, but catches both her arms before she is away
 * STRAIN2   the second clash: he burns her as she thrashes                        (LOSE2: she kicks free; again)
 * HELL      one great hellfire blast: she is flung away, armless and burnt
 * </pre>
 */
public final class GarudaScene {
    private GarudaScene() {
    }

    public static final int NONE = -1, ARRIVE = 0, CAST = 1, BALL = 2, STRAIN1 = 3, WIN1 = 4, LOSE1 = 5, GRAB = 6, PUNCH = 7, KICK = 8,
            STRAIN2 = 9, LOSE2 = 10, HELL = 11, DOWN = 12, BLAST = 13, SNATCH = 14;

    public static int length(int stage) {
        return switch (stage) {
            case ARRIVE -> ARRIVE_END;
            case BLAST -> 64;
            case SNATCH -> GarudaMoveData.SNATCH_END;
            case CAST -> GarudaMoveData.TORNADO_CAST_END;
            case BALL -> GarudaMoveData.WINDBALL_FORM_END + 8;
            case STRAIN1, STRAIN2 -> STRAIN_MAX;
            case WIN1 -> 56;
            case LOSE1 -> 50;
            case GRAB -> 28;
            case PUNCH -> 60;
            case KICK -> 28;
            case LOSE2 -> 34;
            case HELL -> 90;
            default -> 40;
        };
    }

    // ---- the arrival
    public static final int SCREECH = 72, FLY_START = 74, FLY_END = 104, FACE_END = 152, ARRIVE_END = 154;
    /** the hero's spot as he arrives, facing north (yaw 180) */
    public static Vec3 heroArrive() {
        return floor(CraterLayout.SPAWN_X + 0.5, CraterLayout.SPAWN_Z + 0.5);
    }

    public static Vec3 flyFrom() {
        return heroArrive().add(4.8, 3.6, 32);
    }

    public static Vec3 flyTo() {
        return heroArrive().add(3.8, 3.2, -24);
    }

    public static Vec3 faceAt() {
        return heroArrive().add(0, 2.6, -24);
    }

    // ---- the end fight
    public static final int STRAIN_MAX = 160, PROMPT_DELAY = 10;
    public static final float START_BALANCE = 0.5F, PRESS = 0.032F;

    public static float decay(int t) {
        return 0.006F + 0.000035F * t;
    }

    /** where the end fight is played: the hero stands south of the heart facing north, she north of it facing south */
    public static Vec3 heroSpot() {
        return floor(0.5, 9.5);
    }

    public static Vec3 herSpot() {
        return floor(0.5, -9.5);
    }

    public static final float HERO_YAW = 180.0F, HER_YAW = 0.0F;
    public static final double SKY = 22.0;

    /** the ball's place over her raised hands, and up at the hero's fist */
    public static Vec3 ballLow() {
        return herSpot().add(0, 10.5, 0.4);
    }

    public static Vec3 ballHigh() {
        return heroSpot().add(0, SKY - 2.6, -1.2);
    }

    /** the ball's centre in the first clash: nearer her as he wins */
    public static Vec3 ballStrain(float balance) {
        double s = 0.25 + 0.55 * (1.0 - Math.max(0, Math.min(1, balance)));
        return ballLow().lerp(ballHigh(), s);
    }

    /** his feet while his fist is down on top of the ball (he faces north: his fist is a little ahead of him) */
    public static Vec3 onBall(Vec3 ball) {
        return ball.add(0, 2.2, 2.0);
    }

    /** a per-tick track (GarudaMoveData), sampled between ticks */
    public static float track(float[] a, float t) {
        if (t <= 0) {
            return a[0];
        }
        int i = (int) Math.floor(t);
        if (i >= a.length - 1) {
            return a[a.length - 1];
        }
        float f = t - i;
        return a[i] + (a[i + 1] - a[i]) * f;
    }

    /** where she is through the snatch (her talons on his chest from the grab to the throw) */
    public static Vec3 herSnatch(float t) {
        return heroSpot().add(track(GarudaMoveData.SNATCH_HER_X, t), track(GarudaMoveData.SNATCH_HER_Y, t), track(GarudaMoveData.SNATCH_HER_Z, t));
    }

    /** the stump of his right arm through the snatch */
    public static Vec3 stumpSnatch(float t) {
        return heroSpot().add(track(GarudaMoveData.SNATCH_STUMP_X, t), track(GarudaMoveData.SNATCH_STUMP_Y, t), track(GarudaMoveData.SNATCH_STUMP_Z, t));
    }

    /** where the wind blast leaves her hand, and where it takes his arm */
    public static Vec3 blastHand() {
        return heroSpot().add(GarudaMoveData.WIND_BLAST_HAND_X, GarudaMoveData.WIND_BLAST_HAND_Y, GarudaMoveData.WIND_BLAST_HAND_Z);
    }

    public static Vec3 blastStump() {
        return heroSpot().add(GarudaMoveData.WIND_BLAST_STUMP_X, GarudaMoveData.WIND_BLAST_STUMP_Y, GarudaMoveData.WIND_BLAST_STUMP_Z);
    }

    /** the stump (then the new arm) while he hangs in the sky in BALL, from where he is drawn */
    public static Vec3 regrowArm(Vec3 hero, float t) {
        return hero.add(track(GarudaMoveData.REGROW_X, t), track(GarudaMoveData.REGROW_Y, t), track(GarudaMoveData.REGROW_Z, t));
    }

    public static Vec3 floor(double x, double z) {
        return new Vec3(x, CraterLayout.surface((int) Math.floor(x), (int) Math.floor(z)) + 1, z);
    }

    public static double ease(double k) {
        k = Math.max(0, Math.min(1, k));
        return k * k * (3 - 2 * k);
    }

    /**
     * Where the Hero of Hell is drawn during the end fight (his player stands at heroSpot throughout), by stage and
     * tick; null when he should simply be where his player is.
     */
    public static Vec3 heroAt(int stage, float t, float balance) {
        Vec3 h = heroSpot(), g = herSpot();
        Vec3 grab = g.add(0, 0, 4.4);
        switch (stage) {
            case BLAST -> {
                return h;
            }
            case SNATCH -> {
                return h.add(track(GarudaMoveData.SNATCH_HERO_X, t), track(GarudaMoveData.SNATCH_HERO_Y, t), track(GarudaMoveData.SNATCH_HERO_Z, t));
            }
            case CAST -> {
                int c = GarudaMoveData.TORNADO_CAST_CAST;
                if (t < c) {
                    return h;
                }
                return h.add(0, SKY * ease((t - c) / (double) (length(CAST) - c)), 0);
            }
            case BALL -> {
                // hanging in the sky, then down at the ball fist first as she lets it go
                double k = (t - (GarudaMoveData.WINDBALL_FORM_SHOOT - 4)) / 12.0;
                Vec3 sky = h.add(0, SKY + 0.35 * Math.sin(Math.min(t, GarudaMoveData.WINDBALL_FORM_SHOOT - 4) * 0.12), 0);
                return sky.lerp(onBall(ballStrain(START_BALANCE)), ease(k));
            }
            case STRAIN1 -> {
                return onBall(ballStrain(balance));
            }
            case WIN1 -> {
                return onBall(ballStrain(1.0F)).lerp(h, ease(t / 20.0));
            }
            case LOSE1 -> {
                double k = Math.min(1, t / 20.0);
                return onBall(ballStrain(0.0F)).lerp(h, k * k).add(0, 2.0 * Math.sin(Math.PI * k), 0);
            }
            case GRAB -> {
                return h.lerp(grab, ease(t / 11.0));
            }
            case PUNCH -> {
                return grab;
            }
            case KICK -> {
                double back = t < 10 ? ease(t / 8.0) : 1 - ease((t - 10) / 10.0);
                return grab.add(0, 0, 3.0 * back);
            }
            case STRAIN2, HELL -> {
                return grab;
            }
            case LOSE2 -> {
                return grab.add(0, 0, 3.0 * ease(t / 8.0));
            }
            default -> {
                return null;
            }
        }
    }
}
