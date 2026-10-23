package com.rudues.mod.world;

import com.rudues.mod.RuduesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** The Infernal Trial dimension (defined by data/rudues/dimension/infernal_trial.json). */
public final class ModDimensions {
    public static final ResourceKey<Level> TRIAL =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(RuduesMod.MODID, "infernal_trial"));

    private ModDimensions() {
    }
}
