package com.nazarick.registry;

import com.nazarick.NazarickMod;
import com.nazarick.block.ThroneBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, NazarickMod.MODID);

    /** The Throne of Nazarick's seat. Unbreakable in survival. */
    public static final RegistryObject<Block> THRONE = BLOCKS.register("throne",
            () -> new ThroneBlock(BlockBehaviour.Properties.copy(Blocks.CRIMSON_STAIRS)
                    .strength(-1.0F, 3600000.0F).noLootTable()));
}
