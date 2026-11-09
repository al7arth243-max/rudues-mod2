package com.nazarick.world;

import com.nazarick.NazarickMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Where each template of the Great Tomb sits, relative to S = the centre of the mausoleum at surface level.
 * Order matters: later pieces overwrite earlier ones where they overlap (the shaft cuts through the surface foundation).
 */
public final class TombLayout {
    private TombLayout() {}

    /** Depth of the tomb floor (antechamber, throne room, quarters) below the surface. */
    public static final int DEPTH = 70;

    public record Placement(String template, int dx, int dy, int dz) {}

    public static final Placement[] PIECES = {
            new Placement("tomb_surface", -30, -6, -30),
            new Placement("tomb_shaft", -4, -DEPTH, -4),
            new Placement("tomb_antechamber", -10, -DEPTH, 5),
            new Placement("tomb_corridor", 11, -DEPTH, 6),
            new Placement("throne_room", -31, -DEPTH, 27),
            new Placement("kings_quarters", 36, -DEPTH, 12),
    };

    public static ResourceLocation id(String template) {
        return new ResourceLocation(NazarickMod.MODID, template);
    }

    /** Worldgen: add every piece to the structure. */
    public static void addPieces(StructurePiecesBuilder builder, StructureTemplateManager templates, BlockPos s) {
        for (Placement p : PIECES) {
            builder.addPiece(new GreatTombPiece(templates, id(p.template()), s.offset(p.dx(), p.dy(), p.dz())));
        }
    }

    /** Command: place the whole tomb immediately with S at the given position. */
    public static void placeAll(ServerLevel level, BlockPos s) {
        StructureTemplateManager templates = level.getStructureManager();
        for (Placement p : PIECES) {
            BlockPos origin = s.offset(p.dx(), p.dy(), p.dz());
            templates.get(id(p.template())).ifPresent(t ->
                    t.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS));
            record(level, id(p.template()).toString(), origin);
        }
    }

    /** Remember where the important rooms are (for the Ring and the Eye). */
    public static void record(ServerLevel level, String template, BlockPos origin) {
        NazarickLocationData data = NazarickLocationData.get(level);
        switch (template) {
            case "nazarick:tomb_surface" -> data.setEntrance(origin.offset(30, 7, 2));
            case "nazarick:throne_room" -> {
                data.setThroneOrigin(origin);
                if (!data.hasEntrance()) data.setEntrance(origin.offset(31, 1, 0));
            }
            case "nazarick:kings_quarters" -> {
                data.setQuartersOrigin(origin);
                if (!data.hasEntrance()) data.setEntrance(origin.offset(17, 1, 0));
            }
            default -> { }
        }
    }
}
