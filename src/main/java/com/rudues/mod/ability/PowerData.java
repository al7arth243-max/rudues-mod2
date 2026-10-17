package com.rudues.mod.ability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** Stores the "has Fire Fiend power" flag in the player's persistent data (survives death/relog). */
public class PowerData {
    private static final String KEY = "rudues_fire_fiend_power";
    /** Strength V (amplifier 4) */
    private static final int STRENGTH_AMP = 4;
    /**
     * Base Resistance. Amplifier 2 = Resistance III (60% reduction). Resistance V (amplifier 4) would already be
     * 100% immunity, which would make Stand Ground pointless.
     */
    private static final int RESISTANCE_AMP = 2;

    public static boolean has(Player player) {
        CompoundTag root = player.getPersistentData();
        return root.contains(Player.PERSISTED_NBT_TAG, 10)
                && root.getCompound(Player.PERSISTED_NBT_TAG).getBoolean(KEY);
    }

    public static void grant(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(KEY, true);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Keeps the permanent buffs active. Temporary stronger buffs (Stand Ground) are left alone. */
    public static void applyEffects(Player player) {
        // Remove the permanent Speed buff that version 1.0 used to give
        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed != null && speed.getDuration() == -1) {
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
        }
        ensure(player, MobEffects.DAMAGE_BOOST, STRENGTH_AMP, false);
        ensure(player, MobEffects.DAMAGE_RESISTANCE, RESISTANCE_AMP, true);
    }

    private static void ensure(Player player, MobEffect effect, int amp, boolean allowTempHigher) {
        MobEffectInstance cur = player.getEffect(effect);
        if (cur != null) {
            if (cur.getAmplifier() == amp && cur.getDuration() == -1) {
                return;
            }
            if (allowTempHigher && cur.getAmplifier() > amp && cur.getDuration() > 0) {
                return;
            }
            player.removeEffect(effect);
        }
        player.addEffect(new MobEffectInstance(effect, -1, amp, false, false, true));
    }
}
