package com.nazarick.world;

import com.nazarick.NazarickMod;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Finds the nearest Nazarick for the Eye of Nazarick. */
public final class NazarickLocator {
    private NazarickLocator() {}

    /** Structures the eye can find (the Great Tomb). */
    public static final TagKey<Structure> LOCATED_BY_EYE =
            TagKey.create(Registries.STRUCTURE, new ResourceLocation(NazarickMod.MODID, "located_by_eye_of_nazarick"));

    @Nullable
    public static BlockPos find(ServerLevel level, BlockPos from) {
        BlockPos recorded = NazarickLocationData.get(level).getEntrance();
        BlockPos generated = null;
        boolean tagHasEntries = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getTag(LOCATED_BY_EYE).map(set -> set.size() > 0).orElse(false);
        if (tagHasEntries) {
            generated = level.findNearestMapStructure(LOCATED_BY_EYE, from, 100, false);
        }
        if (recorded == null) return generated;
        if (generated == null) return recorded;
        return recorded.distSqr(from) <= generated.distSqr(from) ? recorded : generated;
    }
}
