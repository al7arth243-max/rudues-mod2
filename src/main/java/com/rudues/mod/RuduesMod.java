package com.rudues.mod;

import com.rudues.mod.network.Network;
import com.rudues.mod.registry.ModCreativeTabs;
import com.rudues.mod.registry.ModEffects;
import com.rudues.mod.registry.ModEntities;
import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.registry.ModItems;
import com.rudues.mod.registry.ModBlocks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(RuduesMod.MODID)
public class RuduesMod {
    public static final String MODID = "rudues";

    public RuduesMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModEffects.EFFECTS.register(bus);
        ModParticles.PARTICLES.register(bus);
        ModCreativeTabs.TABS.register(bus);
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(Network::register);
    }
}
