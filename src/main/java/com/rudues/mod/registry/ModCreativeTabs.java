package com.rudues.mod.registry;

import com.rudues.mod.RuduesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RuduesMod.MODID);

    public static final RegistryObject<CreativeModeTab> RUDUES_TAB = TABS.register("rudues_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rudues"))
                    .icon(() -> new ItemStack(ModItems.FIRE_FIEND.get()))
                    .displayItems((params, out) -> out.accept(ModItems.FIRE_FIEND.get()))
                    .build());
}
