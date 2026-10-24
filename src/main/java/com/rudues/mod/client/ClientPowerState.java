package com.rudues.mod.client;

import com.rudues.mod.ability.AbilityType;
import com.rudues.mod.ability.HeroConstants;

/** Client-side mirror of the local player's power state (synced from the server) + the selected ability. */
public class ClientPowerState {
    public static boolean hasPowers = false;
    /** 0-100, fills as base moves are used */
    public static int gauge = 0;
    /** remaining ticks of demon form (0 = not a demon), counted down locally and re-synced by the server */
    public static int heroTicks = 0;
    /** remaining ticks of the Semi Prime form (0 = not in it), counted down locally and re-synced by the server */
    public static int semiTicks = 0;

    private static AbilityType selectedNormal = AbilityType.RAGE_BLAST;
    private static AbilityType selectedHero = AbilityType.CONCENTRATED_BEAM;

    public static boolean isHero() {
        return heroTicks > 0;
    }

    public static AbilityType getSelected() {
        return isHero() ? selectedHero : selectedNormal;
    }

    public static void setSelected(AbilityType type) {
        if (type.hero) {
            selectedHero = type;
        } else {
            selectedNormal = type;
        }
    }

    public static boolean isSemiPrime() {
        return semiTicks > 0;
    }

    public static float fraction() {
        if (isHero()) {
            return heroTicks / (float) HeroConstants.DEMON_TICKS;
        }
        if (isSemiPrime()) {
            return semiTicks / (float) com.rudues.mod.ability.SemiPrime.DURATION;
        }
        return gauge / (float) HeroConstants.GAUGE_MAX;
    }
}
