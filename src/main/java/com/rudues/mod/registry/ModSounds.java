package com.rudues.mod.registry;

import com.rudues.mod.RuduesMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** the trial bosses' themes (streamed from assets/rudues/sounds/music) */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RuduesMod.MODID);

    public static final RegistryObject<SoundEvent> SHADOW_THEME = register("music.infernal_shadow");
    public static final RegistryObject<SoundEvent> HERO_THEME = register("music.infernal_hero");
    /** Benedikta, the Feathered Knight: "To Sail Forbidden Seas" */
    public static final RegistryObject<SoundEvent> BENEDIKTA_THEME = register("music.benedikta");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RuduesMod.MODID, name)));
    }
}
