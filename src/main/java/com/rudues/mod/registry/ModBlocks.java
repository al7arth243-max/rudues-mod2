package com.rudues.mod.registry;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.block.EmbeddedSwordBlock;
import com.rudues.mod.block.TrialPortalBlock;
import com.rudues.mod.block.CastlePortalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RuduesMod.MODID);

    /** the surface of an opened crying-obsidian portal: step in to enter the Infernal Trial */
    public static final RegistryObject<Block> TRIAL_PORTAL = BLOCKS.register("trial_portal",
            () -> new TrialPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollission()
                    .lightLevel(state -> 15)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)));

    /** the surface of an opened calcite ring: step in to go to the Feathered Hold (and, there, home) */
    public static final RegistryObject<Block> CASTLE_PORTAL = BLOCKS.register("castle_portal",
            () -> new CastlePortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .noCollission()
                    .lightLevel(state -> 13)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)));

    /** the sword embedded in the centre of the arena: use it to return to the overworld */
    public static final RegistryObject<Block> EMBEDDED_SWORD = BLOCKS.register("embedded_sword",
            () -> new EmbeddedSwordBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .noOcclusion()
                    .lightLevel(state -> 9)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)));
}
