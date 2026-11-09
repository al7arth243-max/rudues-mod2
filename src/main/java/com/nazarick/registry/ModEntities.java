package com.nazarick.registry;

import com.nazarick.NazarickMod;
import com.nazarick.entity.AlbedoEntity;
import com.nazarick.entity.DeathKnightEntity;
import com.nazarick.entity.ElderLichEntity;
import com.nazarick.entity.SeatEntity;
import com.nazarick.entity.SebasEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, NazarickMod.MODID);

    public static final RegistryObject<EntityType<AlbedoEntity>> ALBEDO = ENTITY_TYPES.register("albedo",
            () -> EntityType.Builder.<AlbedoEntity>of(AlbedoEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build(NazarickMod.MODID + ":albedo"));

    public static final RegistryObject<EntityType<SebasEntity>> SEBAS = ENTITY_TYPES.register("sebas",
            () -> EntityType.Builder.<SebasEntity>of(SebasEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build(NazarickMod.MODID + ":sebas"));

    /** Invisible seat used for the throne. */
    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITY_TYPES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F).noSummon().noSave().clientTrackingRange(10).build(NazarickMod.MODID + ":seat"));

    /** Elder Lich: a hooded, floating undead caster. */
    public static final RegistryObject<EntityType<ElderLichEntity>> ELDER_LICH = ENTITY_TYPES.register("elder_lich",
            () -> EntityType.Builder.<ElderLichEntity>of(ElderLichEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.3F).fireImmune().clientTrackingRange(10).build(NazarickMod.MODID + ":elder_lich"));

    /** Death Knight: Warden-sized undead warrior with flamberge and tower shield. */
    public static final RegistryObject<EntityType<DeathKnightEntity>> DEATH_KNIGHT = ENTITY_TYPES.register("death_knight",
            () -> EntityType.Builder.<DeathKnightEntity>of(DeathKnightEntity::new, MobCategory.MONSTER)
                    .sized(1.3F, 3.1F).clientTrackingRange(12).build(NazarickMod.MODID + ":death_knight"));
}
