package com.nazarick.registry;

import com.nazarick.NazarickMod;
import com.nazarick.world.GreatTombPiece;
import com.nazarick.world.GreatTombStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModStructures {
    private ModStructures() {}

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, NazarickMod.MODID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, NazarickMod.MODID);

    public static final RegistryObject<StructureType<GreatTombStructure>> GREAT_TOMB =
            STRUCTURE_TYPES.register("great_tomb", () -> () -> GreatTombStructure.CODEC);

    public static final RegistryObject<StructurePieceType> TOMB_PIECE =
            PIECE_TYPES.register("tomb_piece", () -> (StructurePieceType.StructureTemplateType) GreatTombPiece::new);
}
