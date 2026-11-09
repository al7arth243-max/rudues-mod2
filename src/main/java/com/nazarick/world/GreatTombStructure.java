package com.nazarick.world;

import com.mojang.serialization.Codec;
import com.nazarick.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Great Tomb of Nazarick. One per world (concentric-ring placement with count 1, a few hundred blocks from spawn).
 * A walled graveyard and mausoleum on the surface; a spiral stair leads 70 blocks down to the Hall of Lemegeton,
 * the Throne Room and the King's Quarters.
 */
public class GreatTombStructure extends Structure {
    public static final Codec<GreatTombStructure> CODEC = simpleCodec(GreatTombStructure::new);

    public GreatTombStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        // Shift the surface north of the start chunk so the whole tomb stays within 8 chunks of it.
        int sx = chunk.getMiddleBlockX();
        int sz = chunk.getMiddleBlockZ() - 60;
        int surface = context.chunkGenerator().getFirstFreeHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        int minY = context.heightAccessor().getMinBuildHeight() + 2;
        if (surface - TombLayout.DEPTH < minY) {
            surface = minY + TombLayout.DEPTH;
        }
        BlockPos s = new BlockPos(sx, surface, sz);
        return Optional.of(new GenerationStub(s, builder -> TombLayout.addPieces(builder, context.structureTemplateManager(), s)));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.GREAT_TOMB.get();
    }
}
