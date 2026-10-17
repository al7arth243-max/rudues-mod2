package com.rudues.mod.registry;

import com.rudues.mod.RuduesMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, RuduesMod.MODID);

    /** animated purple flame wisp (rises, flickers, shrinks) */
    public static final RegistryObject<SimpleParticleType> PURPLE_FLAME =
            PARTICLES.register("purple_flame", () -> new SimpleParticleType(false));
    /** same flame, much bigger (beam, fireball, transformation pillar) */
    public static final RegistryObject<SimpleParticleType> PURPLE_FLAME_BIG =
            PARTICLES.register("purple_flame_big", () -> new SimpleParticleType(false));
    /** jagged purple lightning streak */
    public static final RegistryObject<SimpleParticleType> PURPLE_SPARK =
            PARTICLES.register("purple_spark", () -> new SimpleParticleType(false));
    /** tiny glowing ember that drifts upward */
    public static final RegistryObject<SimpleParticleType> PURPLE_EMBER =
            PARTICLES.register("purple_ember", () -> new SimpleParticleType(false));
}
