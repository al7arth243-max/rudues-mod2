package com.rudues.mod;

import com.rudues.mod.network.Network;
import com.rudues.mod.registry.ModCreativeTabs;
import com.rudues.mod.registry.ModEffects;
import com.rudues.mod.registry.ModEntities;
import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.registry.ModItems;
import com.rudues.mod.registry.ModBlocks;
import com.rudues.mod.registry.ModSounds;
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
        ModSounds.SOUNDS.register(bus);
        ModCreativeTabs.TABS.register(bus);
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(Network::register);
        event.enqueueWork(RuduesMod::raiseHealthCap);
    }

    /**
     * Vanilla caps every mob's max health at 1024; the Infernal Hero has 4000. Lift the cap (the attribute's maximum
     * is a final double field holding 1024 - found by value, so it works with any mappings). If this ever fails the
     * Hero scales the damage it takes instead, so it still takes 4000 health's worth to beat.
     */
    private static void raiseHealthCap() {
        if (!(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH instanceof net.minecraft.world.entity.ai.attributes.RangedAttribute ra)) {
            return;
        }
        try {
            for (java.lang.reflect.Field f : net.minecraft.world.entity.ai.attributes.RangedAttribute.class.getDeclaredFields()) {
                if (f.getType() == double.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    if (f.getDouble(ra) == 1024.0D) {
                        f.setDouble(ra, 1000000.0D);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // the damage scaling in InfernalHeroEntity covers it
        }
    }
}
