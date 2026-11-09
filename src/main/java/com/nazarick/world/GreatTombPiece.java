package com.nazarick.world;

import com.nazarick.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** One template of the Great Tomb, placed unrotated at a fixed offset. */
public class GreatTombPiece extends TemplateStructurePiece {

    public GreatTombPiece(StructureTemplateManager templates, ResourceLocation template, BlockPos origin) {
        super(ModStructures.TOMB_PIECE.get(), 0, templates, template, template.toString(), settings(), origin);
    }

    public GreatTombPiece(StructureTemplateManager templates, CompoundTag tag) {
        super(ModStructures.TOMB_PIECE.get(), tag, templates, id -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings();
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pivot);
        ServerLevel serverLevel = level.getLevel();
        String name = this.templateName;
        BlockPos origin = this.templatePosition;
        // Record on the main thread: world generation runs on worker threads.
        serverLevel.getServer().execute(() -> TombLayout.record(serverLevel, name, origin));
    }
}
