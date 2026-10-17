package com.rudues.mod.registry;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.item.FireFiendItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RuduesMod.MODID);

    public static final RegistryObject<Item> FIRE_FIEND = ITEMS.register("fire_fiend",
            () -> new FireFiendItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
}
