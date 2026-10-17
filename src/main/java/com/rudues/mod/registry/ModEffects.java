package com.rudues.mod.registry;

import com.rudues.mod.RuduesMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, RuduesMod.MODID);

    /** Marker effect: present on a player while they are the Hero of Hell demon (clients use it to swap the model). */
    public static final RegistryObject<MobEffect> HERO_OF_HELL = EFFECTS.register("hero_of_hell", HeroOfHellEffect::new);

    private static class HeroOfHellEffect extends MobEffect {
        HeroOfHellEffect() {
            super(MobEffectCategory.NEUTRAL, 0x8A2BE2);
        }
    }
}
