package com.rudues.mod.events;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.FiendEntity;
import com.rudues.mod.entity.InfernalShadowEntity;
import com.rudues.mod.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RuduesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModBusEvents {
    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.FIEND.get(), FiendEntity.createAttributes().build());
        event.put(ModEntities.INFERNAL_SHADOW.get(), InfernalShadowEntity.createAttributes().build());
        event.put(ModEntities.INFERNAL_HERO.get(), com.rudues.mod.entity.InfernalHeroEntity.createAttributes().build());
        event.put(ModEntities.FEATHERED_KNIGHT.get(), com.rudues.mod.entity.FeatheredKnightEntity.createAttributes().build());
    }
}
