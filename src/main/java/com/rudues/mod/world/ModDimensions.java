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

    /** the Feathered Knight's castle (data/rudues/dimension/feathered_hold.json) */
    public static final ResourceKey<Level> CASTLE =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(RuduesMod.MODID, "feathered_hold"));

    /** the Eye of the Storm, where Garuda is fought (data/rudues/dimension/storm_crater.json) */
    public static final ResourceKey<Level> CRATER =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(RuduesMod.MODID, "storm_crater"));

    private ModDimensions() {
    }
}
