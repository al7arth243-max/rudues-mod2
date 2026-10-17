package com.rudues.mod.ability;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public enum AbilityType {
    // ---- base moves (id, cooldown ticks, gauge gained, demon-form move?)
    RAGE_BLAST("rage_blast", 60, 5, false),
    OBLITERATOR("obliterator", 40, 5, false),
    FIEND_SUMMON("fiend_summon", 200, 7, false),
    VIOLENT_GALE("violent_gale", 50, 4, false),
    STAND_GROUND("stand_ground", 300, 5, false),
    // ---- Hero of Hell (demon form) moves; they do not fill the gauge
    CONCENTRATED_BEAM("concentrated_beam", 300, 0, true),
    OBLITERATOR_MAXIMUM("obliterator_maximum", 100, 0, true),
    HELLFIRE("hellfire", 200, 0, true),
    SLASH_COMBO("slash_combo", 80, 0, true),
    METEOR_IMPACT("meteor_impact", 260, 0, true),
    TREE_OF_SIN("tree_of_sin", 300, 0, true);

    public final String id;
    public final int cooldown;
    public final int gauge;
    public final boolean hero;

    AbilityType(String id, int cooldown, int gauge, boolean hero) {
        this.id = id;
        this.cooldown = cooldown;
        this.gauge = gauge;
        this.hero = hero;
    }

    public Component displayName() {
        return Component.translatable("ability.rudues." + id);
    }

    public Component description() {
        return Component.translatable("ability.rudues." + id + ".desc");
    }

    public static AbilityType byIndex(int i) {
        AbilityType[] v = values();
        return (i >= 0 && i < v.length) ? v[i] : null;
    }

    /** The moves available in the normal form (hero=false) or in demon form (hero=true). */
    public static List<AbilityType> forForm(boolean hero) {
        List<AbilityType> list = new ArrayList<>();
        for (AbilityType t : values()) {
            if (t.hero == hero) {
                list.add(t);
            }
        }
        return list;
    }
}
