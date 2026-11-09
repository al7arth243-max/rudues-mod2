package com.nazarick.registry;

import com.nazarick.NazarickMod;
import com.nazarick.item.EyeOfNazarickItem;
import com.nazarick.item.RingOfAinzOoalGownItem;
import com.nazarick.item.StaffOfAinzItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, NazarickMod.MODID);

    public static final RegistryObject<Item> STAFF_OF_AINZ_OOAL_GOWN = ITEMS.register("staff_of_ainz_ooal_gown",
            () -> new StaffOfAinzItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> RING_OF_AINZ_OOAL_GOWN = ITEMS.register("ring_of_ainz_ooal_gown",
            () -> new RingOfAinzOoalGownItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> THRONE = ITEMS.register("throne",
            () -> new BlockItem(ModBlocks.THRONE.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> EYE_OF_NAZARICK = ITEMS.register("eye_of_nazarick",
            () -> new EyeOfNazarickItem(new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<Item> ALBEDO_SPAWN_EGG = ITEMS.register("albedo_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.ALBEDO, 0xF2EEE8, 0x2B2350, new Item.Properties()));
    public static final RegistryObject<Item> SEBAS_SPAWN_EGG = ITEMS.register("sebas_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SEBAS, 0x24232A, 0xD9C27A, new Item.Properties()));
    public static final RegistryObject<Item> ELDER_LICH_SPAWN_EGG = ITEMS.register("elder_lich_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.ELDER_LICH, 0x2A2333, 0xE0B33A, new Item.Properties()));
    public static final RegistryObject<Item> DEATH_KNIGHT_SPAWN_EGG = ITEMS.register("death_knight_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.DEATH_KNIGHT, 0x2E2E33, 0x8A1F1F, new Item.Properties()));
}
