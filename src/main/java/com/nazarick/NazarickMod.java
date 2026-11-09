package com.nazarick;

import com.nazarick.command.NazarickCommands;
import com.nazarick.entity.AlbedoEntity;
import com.nazarick.entity.DeathKnightEntity;
import com.nazarick.entity.ElderLichEntity;
import com.nazarick.entity.NazarickNpcEntity;
import com.nazarick.registry.ModBlocks;
import com.nazarick.registry.ModCreativeTabs;
import com.nazarick.registry.ModStructures;
import com.nazarick.registry.ModEntities;
import com.nazarick.registry.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NazarickMod.MODID)
public class NazarickMod {
    public static final String MODID = "nazarick";

    public NazarickMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModStructures.STRUCTURE_TYPES.register(modBus);
        ModStructures.PIECE_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(this::onEntityAttributes);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.ALBEDO.get(), AlbedoEntity.createAttributes().build());
        event.put(ModEntities.SEBAS.get(), NazarickNpcEntity.createNpcAttributes().build());
        event.put(ModEntities.ELDER_LICH.get(), ElderLichEntity.createAttributes().build());
        event.put(ModEntities.DEATH_KNIGHT.get(), DeathKnightEntity.createAttributes().build());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        NazarickCommands.register(event.getDispatcher());
    }
}
