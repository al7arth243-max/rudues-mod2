package com.nazarick.client;

import com.nazarick.NazarickMod;
import com.nazarick.client.model.AlbedoModel;
import com.nazarick.client.model.DeathKnightModel;
import com.nazarick.client.model.ElderLichModel;
import com.nazarick.client.model.SebasModel;
import com.nazarick.client.renderer.AlbedoRenderer;
import com.nazarick.client.renderer.DeathKnightRenderer;
import com.nazarick.client.renderer.ElderLichRenderer;
import com.nazarick.item.StaffOfAinzItem;
import com.nazarick.registry.ModItems;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import com.nazarick.client.renderer.SebasRenderer;
import com.nazarick.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NazarickMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        // The staff's model switches as its seven gems appear (0..7 -> 0.0..1.0).
        event.enqueueWork(() -> ItemProperties.register(ModItems.STAFF_OF_AINZ_OOAL_GOWN.get(),
                new ResourceLocation(NazarickMod.MODID, "gems"),
                (stack, level, entity, seed) -> StaffOfAinzItem.getGems(stack) / (float) StaffOfAinzItem.MAX_GEMS));
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AlbedoModel.LAYER, AlbedoModel::createBodyLayer);
        event.registerLayerDefinition(SebasModel.LAYER, SebasModel::createBodyLayer);
        event.registerLayerDefinition(ElderLichModel.LAYER, ElderLichModel::createBodyLayer);
        event.registerLayerDefinition(DeathKnightModel.LAYER, DeathKnightModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ALBEDO.get(), AlbedoRenderer::new);
        event.registerEntityRenderer(ModEntities.SEBAS.get(), SebasRenderer::new);
        event.registerEntityRenderer(ModEntities.SEAT.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.ELDER_LICH.get(), ElderLichRenderer::new);
        event.registerEntityRenderer(ModEntities.DEATH_KNIGHT.get(), DeathKnightRenderer::new);
    }
}
