package com.rudues.mod.registry;

import com.rudues.mod.RuduesMod;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The mod's advancements that are granted from code (data/rudues/advancements). */
public final class ModAdvancements {
    private ModAdvancements() {
    }

    /** completes one criterion of an advancement for a player (nothing happens if it is already done) */
    public static void award(ServerPlayer p, String path, String criterion) {
        MinecraftServer server = p.getServer();
        if (server == null) {
            return;
        }
        Advancement adv = server.getAdvancements().getAdvancement(new ResourceLocation(RuduesMod.MODID, path));
        if (adv != null) {
            p.getAdvancements().award(adv, criterion);
        }
    }

    /** the Infernal Hero is beaten */
    public static void heroDefeated(ServerPlayer p) {
        award(p, "infernal_hero", "won");
    }

    /** the power of Rage is theirs */
    public static void abilitiesGained(ServerPlayer p) {
        award(p, "abilities_of_rage", "granted");
    }
}
