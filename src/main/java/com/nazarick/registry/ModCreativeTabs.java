package com.nazarick.registry;

import com.nazarick.NazarickMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NazarickMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.nazarick"))
            .icon(() -> new ItemStack(ModItems.STAFF_OF_AINZ_OOAL_GOWN.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.STAFF_OF_AINZ_OOAL_GOWN.get());
                output.accept(ModItems.RING_OF_AINZ_OOAL_GOWN.get());
                output.accept(ModItems.EYE_OF_NAZARICK.get());
                output.accept(ModItems.THRONE.get());
                output.accept(ModItems.ALBEDO_SPAWN_EGG.get());
                output.accept(ModItems.SEBAS_SPAWN_EGG.get());
                output.accept(ModItems.ELDER_LICH_SPAWN_EGG.get());
                output.accept(ModItems.DEATH_KNIGHT_SPAWN_EGG.get());
            })
            .build());
}
