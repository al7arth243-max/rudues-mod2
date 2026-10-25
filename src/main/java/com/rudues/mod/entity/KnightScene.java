package com.rudues.mod.entity;

import com.rudues.mod.ability.SemiPrime;
import com.rudues.mod.world.CastleLayout;
import net.minecraft.world.phys.Vec3;

/**
 * Benedikta's Semi Prime scene at 20% health, stage by stage. Shared by the server (she and the player are moved
 * through it) and the client (the camera and the clash gauge). The player stands at the arena's heart facing south;
 * she starts eight blocks south of them, facing north.
 * <pre>
 *  TRANSFORM  she hunches, the wings burst out of her back, the pale skin and the green glow come
 *  ASCEND     talons the size of an iron golem tear a chunk of the roof up, and she climbs into the sky with it
 *  PLAYER     the player turns Semi Prime (skipped if they already are)
 *  DIVE       she dives at them with the rubble; they wind back a burning fist
 *  STRAIN     the clash: mash to drive the uppercut through her push
 *  WIN        the rubble shatters, she is flung up and back, lands, gets up    -- or --
 *  LOSE       the rubble smashes down on the player; she lets go and beats back
 *  RETURN     she flies back to the middle of the fight, healed in full: phase two
 * </pre>
 */
public final class KnightScene {
    private KnightScene() {
    }

    public static final int NONE = -1, TRANSFORM = 0, ASCEND = 1, PLAYER = 2, DIVE = 3, STRAIN = 4, WIN = 5, LOSE = 6, RETURN = 7;
    /** her defeat: struck down, struggling up, into the sky, and the storm that takes the castle */
    public static final int D_FALL = 10, D_RISE = 11, D_ASCEND = 12, D_STORM = 13;
    /** the storm: when the white is complete, and when you are carried into the crater */
    public static final int STORM_LEN = 100, STORM_WHITE = 88, STORM_CARRY = 96;

    public static int length(int stage) {
        return switch (stage) {
            case TRANSFORM -> KnightMoveData.SEMI_TRANSFORM_END;
            case ASCEND -> KnightMoveData.SEMI_ASCEND_END;
            case PLAYER -> SemiPrime.TRANSFORM_TICKS + 6;
            case DIVE -> KnightMoveData.RUBBLE_DIVE_END;
            case STRAIN -> STRAIN_MAX;
            case WIN -> 50;
            case LOSE -> 50;
            case D_FALL -> KnightMoveData.DEFEAT_FALL_END;
            case D_RISE -> KnightMoveData.DEFEAT_RISE_END;
            case D_ASCEND -> KnightMoveData.DEFEAT_SCREECH_END;
            case D_STORM -> STORM_LEN;
            default -> 40;
        };
    }

    /** the clash: the longest it can last, the button's push, her push (rising) */
    public static final int STRAIN_MAX = 160, PROMPT_DELAY = 10;
    public static final float START_BALANCE = 0.55F, PRESS = 0.034F;

    public static float decay(int t) {
        return 0.0065F + 0.00003F * t;
    }

    /** the player's spot, facing south (yaw 0) */
    public static final double OX = CastleLayout.ARENA_CX + 0.5, OY = CastleLayout.ROOF, OZ = CastleLayout.ARENA_CZ + 0.5;
    public static final float PLAYER_YAW = 0.0F, KNIGHT_YAW = 180.0F;

    public static Vec3 origin() {
        return new Vec3(OX, OY, OZ);
    }

    /** where she transforms, and where she hangs in the sky */
    public static Vec3 start() {
        return origin().add(0, 0, 8);
    }

    public static Vec3 high() {
        return origin().add(0, 13, 8);
    }

    /** the player's fist at full stretch (player facing +z: forward is +z, right is -x) */
    public static Vec3 contact() {
        float[] f = KnightMoveData.PLAYER_STRAIN_FIST;
        return origin().add(-f[1], f[2], f[0]);
    }

    /** the rubble's centre while it is held against the fist */
    public static Vec3 rubbleStrain() {
        return contact().add(0, 1.35, 0.85);
    }

    /** her feet while she bears down: the rock held between her talons (she faces -z, her right is +x) */
    public static Vec3 knightStrain() {
        float[] g = KnightMoveData.RUBBLE_STRAIN_GRIP;
        Vec3 grip = rubbleStrain();
        return grip.add(-g[1], -g[2], g[0]);
    }

    public static Vec3 winLanding() {
        return origin().add(0, 0, 13);
    }

    public static Vec3 loseBack() {
        return origin().add(0, 2.5, 9);
    }

    public static Vec3 home() {
        return origin().add(0, 1.4, 9);
    }

    public static double ease(double k) {
        k = Math.max(0, Math.min(1, k));
        return k * k * (3 - 2 * k);
    }
}
