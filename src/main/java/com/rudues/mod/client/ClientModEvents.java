package com.rudues.mod.client;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.registry.ModEntities;
import com.rudues.mod.registry.ModParticles;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RuduesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(ClientKeys.OPEN_WHEEL);
        event.register(ClientKeys.CAST_SELECTED);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Rage Blast and Violent Gale are drawn purely with particles
        event.registerEntityRenderer(ModEntities.RAGE_BLAST.get(), ctx -> new NoopRenderer<>(ctx));
        event.registerEntityRenderer(ModEntities.WIND_SLASH.get(), ctx -> new NoopRenderer<>(ctx));
        event.registerEntityRenderer(ModEntities.FIEND.get(), FiendRenderer::new);
        event.registerEntityRenderer(ModEntities.DEMON.get(), DemonRenderer::new);
        event.registerEntityRenderer(ModEntities.BEAM.get(), BeamRenderer::new);
        event.registerEntityRenderer(ModEntities.INFERNAL_SHADOW.get(), InfernalShadowRenderer::new);
        event.registerEntityRenderer(ModEntities.INFERNAL_HERO.get(), InfernalHeroRenderer::new);
        event.registerEntityRenderer(ModEntities.HERO_FIREBALL.get(), HeroFireballRenderer::new);
        event.registerEntityRenderer(ModEntities.FIRE_GEYSER.get(), FireGeyserRenderer::new);
        // drawn as a large, full-bright fire charge (plus its flame trail)
        event.registerEntityRenderer(ModEntities.SHADOW_FIREBALL.get(), ShadowFireballRenderer::new);
    }

    /** awakened players wear the Shadow's corruption (both skin models) */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.LivingEntityRenderer renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                pr.addLayer(new PlayerCorruptionLayer(pr));
            }
        }
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.PURPLE_FLAME.get(), sprites -> new PurpleParticle.Provider(sprites, PurpleParticle.Kind.FLAME));
        event.registerSpriteSet(ModParticles.PURPLE_FLAME_BIG.get(), sprites -> new PurpleParticle.Provider(sprites, PurpleParticle.Kind.FLAME_BIG));
        event.registerSpriteSet(ModParticles.PURPLE_SPARK.get(), sprites -> new PurpleParticle.Provider(sprites, PurpleParticle.Kind.SPARK));
        event.registerSpriteSet(ModParticles.PURPLE_EMBER.get(), sprites -> new PurpleParticle.Provider(sprites, PurpleParticle.Kind.EMBER));
        event.registerSpriteSet(ModParticles.DARK_FLAME.get(), sprites -> new PurpleParticle.Provider(sprites, PurpleParticle.Kind.FLAME, true));
        event.registerSpriteSet(ModParticles.DARK_FLAME_BIG.get(), sprites -> new PurpleParticle.Provider(sprites, PurpleParticle.Kind.FLAME_BIG, true));
        event.registerSpriteSet(ModParticles.DARK_SPARK.get(), sprites -> new PurpleParticle.Provider(sprites, PurpleParticle.Kind.SPARK, true));
        event.registerSpriteSet(ModParticles.DARK_EMBER.get(), sprites -> new PurpleParticle.Provider(sprites, PurpleParticle.Kind.EMBER, true));
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.ARMOR_LEVEL.id(), "hero_gauge", new HeroGaugeOverlay());
    }
}
