package com.rudues.mod.registry;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.FiendEntity;
import com.rudues.mod.entity.BeamEntity;
import com.rudues.mod.entity.DemonEntity;
import com.rudues.mod.entity.InfernalShadowEntity;
import com.rudues.mod.entity.FireGeyserEntity;
import com.rudues.mod.entity.ShadowFireballEntity;
import com.rudues.mod.entity.RageBlastEntity;
import com.rudues.mod.entity.WindSlashEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RuduesMod.MODID);

    public static final RegistryObject<EntityType<RageBlastEntity>> RAGE_BLAST = ENTITIES.register("rage_blast",
            () -> EntityType.Builder.<RageBlastEntity>of(RageBlastEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).fireImmune().clientTrackingRange(4).updateInterval(10)
                    .build("rage_blast"));

    public static final RegistryObject<EntityType<FiendEntity>> FIEND = ENTITIES.register("fire_fiend_minion",
            () -> EntityType.Builder.<FiendEntity>of(FiendEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.6F).fireImmune().clientTrackingRange(8).updateInterval(2)
                    .build("fire_fiend_minion"));

    public static final RegistryObject<EntityType<WindSlashEntity>> WIND_SLASH = ENTITIES.register("wind_slash",
            () -> EntityType.Builder.<WindSlashEntity>of(WindSlashEntity::new, MobCategory.MISC)
                    .sized(1.5F, 0.6F).fireImmune().clientTrackingRange(8).updateInterval(1)
                    .build("wind_slash"));

    public static final RegistryObject<EntityType<DemonEntity>> DEMON = ENTITIES.register("hero_of_hell_demon",
            () -> EntityType.Builder.<DemonEntity>of(DemonEntity::new, MobCategory.MISC)
                    .sized(4.2F, 7.0F).fireImmune().clientTrackingRange(16).updateInterval(1)
                    .build("hero_of_hell_demon"));

    public static final RegistryObject<EntityType<BeamEntity>> BEAM = ENTITIES.register("concentrated_beam",
            () -> EntityType.Builder.<BeamEntity>of(BeamEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().clientTrackingRange(10).updateInterval(1)
                    .build("concentrated_beam"));

    /** the trial's first boss: a dark copy of the chosen player */
    public static final RegistryObject<EntityType<InfernalShadowEntity>> INFERNAL_SHADOW = ENTITIES.register("infernal_shadow",
            () -> EntityType.Builder.<InfernalShadowEntity>of(InfernalShadowEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).fireImmune().clientTrackingRange(10).updateInterval(1)
                    .build("infernal_shadow"));

    /** Inferno Vortex fire geyser (warning circle, then a column of fire) */
    public static final RegistryObject<EntityType<FireGeyserEntity>> FIRE_GEYSER = ENTITIES.register("fire_geyser",
            () -> EntityType.Builder.<FireGeyserEntity>of(FireGeyserEntity::new, MobCategory.MISC)
                    .sized(2.8F, 6.5F).fireImmune().clientTrackingRange(10).updateInterval(20)
                    .build("fire_geyser"));

    /** Hellfire Volley fireball */
    public static final RegistryObject<EntityType<ShadowFireballEntity>> SHADOW_FIREBALL = ENTITIES.register("shadow_fireball",
            () -> EntityType.Builder.<ShadowFireballEntity>of(ShadowFireballEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(2)
                    .build("shadow_fireball"));
}
